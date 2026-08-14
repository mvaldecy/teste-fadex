package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEmitterRegistryTest {

	private static final UUID USUARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final UUID OUTRO_USUARIO = UUID.fromString("2f5b1c77-9e4a-4a1e-9c8e-2b1d3f4a5c6d");

	private final NotificationEmitterRegistry registry = new NotificationEmitterRegistry();

	@Test
	void deveGuardarVariasConexoesDoMesmoUsuario() {
		SseSubscription primeiraAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());
		SseSubscription segundaAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());

		registry.add(primeiraAba);
		registry.add(segundaAba);

		assertThat(registry.countConnections()).isEqualTo(2);
		assertThat(registry.findAll()).containsExactlyInAnyOrder(primeiraAba, segundaAba);
	}

	@Test
	void deveRemoverApenasAConexaoInformada() {
		SseSubscription primeiraAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());
		SseSubscription segundaAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());

		registry.add(primeiraAba);
		registry.add(segundaAba);
		registry.remove(primeiraAba);

		assertThat(registry.findAll()).containsExactly(segundaAba);
	}

	@Test
	void deveIgnorarRemocaoRepetida() {
		SseSubscription conexao = SseSubscription.create(USUARIO, Role.SOLICITANTE, new SseEmitter());

		registry.add(conexao);
		registry.remove(conexao);
		registry.remove(conexao);

		assertThat(registry.countConnections()).isZero();
	}

	@Test
	void deveIsolarConexoesPorUsuario() {
		SseSubscription conexaoDoUsuario = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());
		SseSubscription conexaoDeOutroUsuario = SseSubscription.create(OUTRO_USUARIO, Role.SOLICITANTE, new SseEmitter());

		registry.add(conexaoDoUsuario);
		registry.add(conexaoDeOutroUsuario);
		registry.remove(conexaoDoUsuario);

		assertThat(registry.findAll()).containsExactly(conexaoDeOutroUsuario);
	}
}
