package br.org.fadex.helpdesk.model.user;

public abstract class UserMapper {

	private UserMapper() {
	}

	public static UserMinDto toMinDto(User user) {
		return new UserMinDto(
				user.getId(),
				user.getName()
		);
	}
}
