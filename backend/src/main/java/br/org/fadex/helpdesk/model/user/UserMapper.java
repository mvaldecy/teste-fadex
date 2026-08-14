package br.org.fadex.helpdesk.model.user;

public abstract class UserMapper {

	private UserMapper() {
	}

	public static UserDto toResponseDto(User user) {
		return new UserDto(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.getMustChangePassword(),
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

	public static User toEntity(UserCreationDto userCreationDto, String passwordHash, Boolean mustChangePassword) {
		return new User(
				userCreationDto.name(),
				userCreationDto.email(),
				passwordHash,
				userCreationDto.role(),
				mustChangePassword
		);
	}
}
