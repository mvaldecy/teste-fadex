package br.org.fadex.helpdesk.model.user;

import br.org.fadex.helpdesk.model.enums.Role;

public abstract class UserMapper {

	private UserMapper() {
	}

	public static UserDto toResponseDto(User user) {
		return new UserDto(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}

	public static UserMinDto toMinDto(User user) {
		return new UserMinDto(
				user.getId(),
				user.getName()
		);
	}

	public static User toEntity(UserCreationDto userCreationDto, String passwordHash) {
		return toEntity(userCreationDto, passwordHash, userCreationDto.role());
	}

	public static User toEntity(UserCreationDto userCreationDto, String passwordHash, Role role) {
		return new User(
				userCreationDto.name(),
				userCreationDto.email(),
				passwordHash,
				role
		);
	}
}
