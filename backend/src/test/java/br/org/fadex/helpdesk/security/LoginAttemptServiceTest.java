package br.org.fadex.helpdesk.security;

import br.org.fadex.helpdesk.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

	private static final Instant INICIO = Instant.parse("2026-08-15T12:00:00Z");
	private static final String EMAIL = "maria@fadex.org.br";

	/** Relogio controlado: o bloqueio expira por tempo, e esperar de verdade tornaria o teste lento. */
	private static final class RelogioMovel extends Clock {

		private Instant agora = INICIO;

		void avancar(Duration duracao) {
			agora = agora.plus(duracao);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora;
		}
	}

	private LoginAttemptService criar(RelogioMovel relogio) {
		return new LoginAttemptService(3, 15, 5, relogio);
	}

	@Test
	void deveLiberarAntesDoLimite() {
		LoginAttemptService service = criar(new RelogioMovel());

		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);

		assertThatCode(() -> service.ensureNotBlocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void deveBloquearAoAtingirOLimite() {
		LoginAttemptService service = criar(new RelogioMovel());

		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);

		assertThatThrownBy(() -> service.ensureNotBlocked(EMAIL))
				.isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void deveLiberarDepoisQueOBloqueioExpira() {
		RelogioMovel relogio = new RelogioMovel();
		LoginAttemptService service = criar(relogio);

		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);

		relogio.avancar(Duration.ofMinutes(6));

		assertThatCode(() -> service.ensureNotBlocked(EMAIL)).doesNotThrowAnyException();
	}

	/**
	 * Falhas espalhadas nao somam para sempre: quem errou a senha uma vez por semana nao pode
	 * acabar trancado por acumulo.
	 */
	@Test
	void deveReiniciarAContagemDepoisDaJanela() {
		RelogioMovel relogio = new RelogioMovel();
		LoginAttemptService service = criar(relogio);

		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);

		relogio.avancar(Duration.ofMinutes(16));

		service.registerFailure(EMAIL);

		assertThatCode(() -> service.ensureNotBlocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void sucessoDeveZerarOHistorico() {
		LoginAttemptService service = criar(new RelogioMovel());

		service.registerFailure(EMAIL);
		service.registerFailure(EMAIL);
		service.registerSuccess(EMAIL);
		service.registerFailure(EMAIL);

		assertThatCode(() -> service.ensureNotBlocked(EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void deveIgnorarCaixaEEspacoNoEmail() {
		LoginAttemptService service = criar(new RelogioMovel());

		service.registerFailure("  MARIA@fadex.org.br ");
		service.registerFailure(EMAIL);
		service.registerFailure("Maria@Fadex.Org.Br");

		assertThatThrownBy(() -> service.ensureNotBlocked(EMAIL))
				.isInstanceOf(TooManyRequestsException.class);
	}
}
