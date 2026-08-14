package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.mail.EmailMessage;
import br.org.fadex.helpdesk.mail.EmailSender;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserCreationDto;
import br.org.fadex.helpdesk.model.user.UserDto;
import br.org.fadex.helpdesk.model.user.UserFilter;
import br.org.fadex.helpdesk.model.user.UserMapper;
import br.org.fadex.helpdesk.model.user.UserMinDto;
import br.org.fadex.helpdesk.repository.UserRepository;
import br.org.fadex.helpdesk.repository.UserSpecification;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.security.TemporaryPasswordGenerator;
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
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;
	private final EmailSender emailSender;
	private final AccessControlService accessControlService;

	public UserService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator temporaryPasswordGenerator,
			EmailSender emailSender,
			AccessControlService accessControlService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.temporaryPasswordGenerator = temporaryPasswordGenerator;
		this.emailSender = emailSender;
		this.accessControlService = accessControlService;
	}

	@Transactional(readOnly = true)
	public Page<UserMinDto> findAll(UserFilter filter, Pageable pageable) {
		UserFilter resolvedFilter = resolveFilterByRole(filter);
		Specification<User> spec = UserSpecification.createSpecification(resolvedFilter);
		Page<User> users = userRepository.findAll(spec, pageable);
		Page<UserMinDto> response = users.map(UserMapper::toMinDto);

		return response;
	}

	@Transactional(readOnly = true)
	public UserDto findById(UUID id) {
		accessControlService.assertCanAccessUser(id);
		User user = findEntityById(id);
		UserDto response = UserMapper.toResponseDto(user);

		return response;
	}

	@Transactional
	public UserDto create(UserCreationDto userCreationDto) {
		accessControlService.assertAdmin();
		validateEmailAvailable(userCreationDto.email());

		String temporaryPassword = temporaryPasswordGenerator.generate();
		String passwordHash = passwordEncoder.encode(temporaryPassword);
		User user = UserMapper.toEntity(userCreationDto, passwordHash, true);
		User savedUser = userRepository.save(user);
		EmailMessage message = new EmailMessage(
				savedUser.getEmail(),
				"Acesso provisorio ao Fadex Helpdesk",
				"Ola, " + savedUser.getName() + ". Sua senha provisoria e: " + temporaryPassword
		);
		emailSender.send(message);
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

	private UserFilter resolveFilterByRole(UserFilter filter) {
		if (accessControlService.isAdmin()) {
			return filter;
		}

		return new UserFilter(
				accessControlService.getAuthenticatedUserId(),
				filter.role(),
				filter.name(),
				filter.email(),
				filter.search()
		);
	}
}
