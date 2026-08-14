package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserCreationDto;
import br.org.fadex.helpdesk.model.user.UserDto;
import br.org.fadex.helpdesk.model.user.UserFilter;
import br.org.fadex.helpdesk.model.user.UserMapper;
import br.org.fadex.helpdesk.model.user.UserMinDto;
import br.org.fadex.helpdesk.repository.UserRepository;
import br.org.fadex.helpdesk.repository.UserSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public Page<UserMinDto> findAll(UserFilter filter, Pageable pageable) {
		Specification<User> spec = UserSpecification.createSpecification(filter);
		Page<User> users = userRepository.findAll(spec, pageable);
		Page<UserMinDto> response = users.map(UserMapper::toMinDto);

		return response;
	}

	@Transactional(readOnly = true)
	public UserDto findById(UUID id) {
		User user = findEntityById(id);
		UserDto response = UserMapper.toResponseDto(user);

		return response;
	}

	@Transactional
	public UserDto create(UserCreationDto userCreationDto) {
		validateEmailAvailable(userCreationDto.email());

		String passwordHash = passwordEncoder.encode(userCreationDto.password());
		User user = UserMapper.toEntity(userCreationDto, passwordHash);
		User savedUser = userRepository.save(user);
		UserDto response = UserMapper.toResponseDto(savedUser);

		return response;
	}

	@Transactional(readOnly = true)
	public User findEntityById(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
	}

	private void validateEmailAvailable(String email) {
		Boolean exists = userRepository.existsByEmail(email);

		if (exists) {
			throw new ConflictException("E-mail já cadastrado.");
		}
	}
}
