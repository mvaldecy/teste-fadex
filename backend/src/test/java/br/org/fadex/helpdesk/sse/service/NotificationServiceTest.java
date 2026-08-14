package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final UUID USUARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final long TIMEOUT = 1800000L;
	private static final long RECONNECT_TIME = 5000L;

	@Mock
	private NotificationEmitterRegistry registry;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	@Test
	void deveRegistrarAssinaturaComIdentidadeCapturadaDoToken() {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		ArgumentCaptor<SseSubscription> subscriptionCaptor = ArgumentCaptor.forClass(SseSubscription.class);

		when(authenticatedUserService.getUserId()).thenReturn(USUARIO);
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);

		SseEmitter emitter = notificationService.subscribe();

		verify(registry).add(subscriptionCaptor.capture());
		SseSubscription subscription = subscriptionCaptor.getValue();

		assertThat(subscription.userId()).isEqualTo(USUARIO);
		assertThat(subscription.role()).isEqualTo(Role.ADMIN);
		assertThat(subscription.connectionId()).isNotBlank();
		assertThat(subscription.emitter()).isSameAs(emitter);
		assertThat(emitter.getTimeout()).isEqualTo(TIMEOUT);
	}

	@Test
	void deveEnviarApenasParaAssinaturasDaAudiencia() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterDoDestinatario = mock(SseEmitter.class);
		SseEmitter emitterDeOutroUsuario = mock(SseEmitter.class);
		SseSubscription destinatario = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterDoDestinatario);
		SseSubscription outroUsuario = new SseSubscription(
				"conexao-2",
				UUID.fromString("2f5b1c77-9e4a-4a1e-9c8e-2b1d3f4a5c6d"),
				Role.SOLICITANTE,
				emitterDeOutroUsuario
		);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Users(Set.of(USUARIO))
		);

		when(registry.findAll()).thenReturn(List.of(destinatario, outroUsuario));

		notificationService.dispatch(message);

		verify(emitterDoDestinatario).send(any(SseEmitter.SseEventBuilder.class));
		verify(emitterDeOutroUsuario, never()).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	void deveRemoverConexaoQuebradaSemInterromperAsDemaisENaoCompletarComErro() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterQuebrado = mock(SseEmitter.class);
		SseEmitter emitterSaudavel = mock(SseEmitter.class);
		SseSubscription conexaoQuebrada = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterQuebrado);
		SseSubscription conexaoSaudavel = new SseSubscription("conexao-2", USUARIO, Role.ADMIN, emitterSaudavel);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		when(registry.findAll()).thenReturn(List.of(conexaoQuebrada, conexaoSaudavel));
		doThrow(new IOException("conexao fechada")).when(emitterQuebrado).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.dispatch(message);

		verify(registry).remove(conexaoQuebrada);
		verify(emitterSaudavel).send(any(SseEmitter.SseEventBuilder.class));
		// IOException e desconexao do cliente: o container ja dispara o error dispatch
		// sozinho, entao a aplicacao nao deve chamar completeWithError.
		verify(emitterQuebrado, never()).completeWithError(any());
	}

	@Test
	void deveRemoverConexaoECompletarComErroQuandoFalhaDeSerializacaoLancaIllegalState() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterComFalha = mock(SseEmitter.class);
		SseSubscription conexaoComFalha = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterComFalha);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		when(registry.findAll()).thenReturn(List.of(conexaoComFalha));
		IllegalStateException falhaDeSerializacao = new IllegalStateException("erro ao serializar o corpo do evento");
		doThrow(falhaDeSerializacao).when(emitterComFalha).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.dispatch(message);

		verify(registry).remove(conexaoComFalha);
		// IllegalStateException pode ser um emitter ja concluido ou uma falha de
		// serializacao num emitter vivo; nos dois casos o stream precisa fechar de
		// fato para que o cliente detecte e reconecte, em vez de ficar mudo ate o timeout.
		verify(emitterComFalha).completeWithError(falhaDeSerializacao);
	}

	@Test
	void deveEnviarKeepAliveParaTodasAsConexoes() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter primeiroEmitter = mock(SseEmitter.class);
		SseEmitter segundoEmitter = mock(SseEmitter.class);
		SseSubscription primeiraConexao = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, primeiroEmitter);
		SseSubscription segundaConexao = new SseSubscription("conexao-2", USUARIO, Role.ADMIN, segundoEmitter);

		when(registry.findAll()).thenReturn(List.of(primeiraConexao, segundaConexao));

		notificationService.sendHeartbeat();

		verify(primeiroEmitter).send(any(SseEmitter.SseEventBuilder.class));
		verify(segundoEmitter).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	void deveRemoverConexaoMortaDetectadaPeloKeepAliveENaoCompletarComErro() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterMorto = mock(SseEmitter.class);
		SseSubscription conexaoMorta = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterMorto);

		when(registry.findAll()).thenReturn(List.of(conexaoMorta));
		doThrow(new IOException("broken pipe")).when(emitterMorto).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.sendHeartbeat();

		verify(registry).remove(conexaoMorta);
		verify(emitterMorto, never()).completeWithError(any());
	}

	@Test
	void deveEncerrarEmitterQuandoKeepAliveEncontraFalhaDeSerializacao() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterComFalha = mock(SseEmitter.class);
		SseSubscription conexaoComFalha = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterComFalha);

		when(registry.findAll()).thenReturn(List.of(conexaoComFalha));
		IllegalStateException falhaDeSerializacao = new IllegalStateException("erro interno do emitter");
		doThrow(falhaDeSerializacao).when(emitterComFalha).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.sendHeartbeat();

		verify(registry).remove(conexaoComFalha);
		verify(emitterComFalha).completeWithError(falhaDeSerializacao);
	}
}
