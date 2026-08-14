package br.org.fadex.helpdesk.config;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevDataSeeder {

	@Bean
	@Order(1)
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

			createUserIfNotExists(
					userRepository,
					passwordEncoder,
					"Marcos Valdecy",
					"mvaldecy11@gmail.com",
					"dev123",
					Role.ADMIN
			);

			createUserIfNotExists(
					userRepository,
					passwordEncoder,
					"Carla Menezes",
					"carla.menezes@fadex.org.br",
					"admin123",
					Role.ADMIN
			);

			createUserIfNotExists(
					userRepository,
					passwordEncoder,
					"Ana Ribeiro",
					"ana.ribeiro@fadex.org.br",
					"solicitante123",
					Role.SOLICITANTE
			);

			createUserIfNotExists(
					userRepository,
					passwordEncoder,
					"Bruno Carvalho",
					"bruno.carvalho@fadex.org.br",
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
					role,
					false
			);

			userRepository.save(user);
		}
	}
}
