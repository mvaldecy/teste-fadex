package br.org.fadex.helpdesk.sse.model;

import br.org.fadex.helpdesk.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAudienceTest {

	private static final UUID DESTINATARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final UUID OUTRO_USUARIO = UUID.fromString("2f5b1c77-9e4a-4a1e-9c8e-2b1d3f4a5c6d");

	@Test
	void deveIncluirApenasUsuariosListados() {
		NotificationAudience audience = new NotificationAudience.Users(Set.of(DESTINATARIO));

		assertThat(audience.includes(DESTINATARIO, Role.SOLICITANTE)).isTrue();
		assertThat(audience.includes(OUTRO_USUARIO, Role.SOLICITANTE)).isFalse();
	}

	@Test
	void deveIncluirApenasRolesListadas() {
		NotificationAudience audience = new NotificationAudience.Roles(Set.of(Role.ADMIN));

		assertThat(audience.includes(OUTRO_USUARIO, Role.ADMIN)).isTrue();
		assertThat(audience.includes(DESTINATARIO, Role.SOLICITANTE)).isFalse();
	}

	@Test
	void deveIncluirTodosNoBroadcast() {
		NotificationAudience audience = new NotificationAudience.Everyone();

		assertThat(audience.includes(DESTINATARIO, Role.SOLICITANTE)).isTrue();
		assertThat(audience.includes(OUTRO_USUARIO, Role.ADMIN)).isTrue();
	}

	@Test
	void deveGerarIdentificadorUnicoParaCadaMensagem() {
		NotificationMessage primeira = NotificationMessage.of("CHAMADO_CRIADO", "dado", new NotificationAudience.Everyone());
		NotificationMessage segunda = NotificationMessage.of("CHAMADO_CRIADO", "dado", new NotificationAudience.Everyone());

		assertThat(primeira.eventId()).isNotBlank();
		assertThat(primeira.eventId()).isNotEqualTo(segunda.eventId());
	}
}
