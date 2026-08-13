package br.org.fadex.helpdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/api/v1/choices"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.build();
	}
}
