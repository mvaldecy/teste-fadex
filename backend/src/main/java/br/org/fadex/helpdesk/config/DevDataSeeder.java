package br.org.fadex.helpdesk.config;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevDataSeeder {

	@Bean
	public CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			createUserIfNotExists(
					userRepository,
					passwordEncoder,
					"Administrador",
					"admin@fadex.org.br",
					"admin123",
					Role.ADMIN
			);

			createUserIfNotExists(
					userRepository,
					passwordEncoder,
					"Solicitante",
					"solicitante@fadex.org.br",
					"solicitante123",
					Role.SOLICITANTE
			);
		};
	}

	private void createUserIfNotExists(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			String name,
			String email,
			String password,
			Role role
	) {
		Boolean exists = userRepository.existsByEmail(email);

		if (!exists) {
			User user = new User(
					name,
					email,
					passwordEncoder.encode(password),
					role
			);

			userRepository.save(user);
		}
	}
}
