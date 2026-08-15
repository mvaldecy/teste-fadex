package br.org.fadex.helpdesk.security;

import br.org.fadex.helpdesk.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trava tentativas de login repetidas contra o mesmo e-mail.
 *
 * A contagem e por e-mail, e nao por IP: e o e-mail que identifica a conta sob ataque, e atras de
 * NAT o IP agrupa gente que nao tem relacao nenhuma entre si. A contrapartida esta assumida — quem
 * souber o e-mail de alguem consegue trancar aquela conta por alguns minutos. Para um helpdesk
 * interno, uma conta indisponivel por cinco minutos custa menos que uma conta invadida.
 *
 * O estado vive em memoria. Com mais de uma instancia cada uma conta o seu, o que multiplica o
 * limite efetivo pelo numero de instancias; a versao correta usaria armazenamento compartilhado.
 * Isso esta registrado no README em vez de escondido: em uma instancia, que e como o sistema roda
 * hoje, a protecao vale integralmente.
 */
@Component
public class LoginAttemptService {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

	private record Attempts(int count, Instant firstFailureAt, Instant blockedUntil) {
	}

	private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
	private final int maxAttempts;
	private final Duration window;
	private final Duration blockDuration;
	private final Clock clock;

	public LoginAttemptService(
			@Value("${app.security.login.max-attempts:5}") int maxAttempts,
			@Value("${app.security.login.window-minutes:15}") int windowMinutes,
			@Value("${app.security.login.block-minutes:5}") int blockMinutes,
			Clock clock
	) {
		this.maxAttempts = maxAttempts;
		this.window = Duration.ofMinutes(windowMinutes);
		this.blockDuration = Duration.ofMinutes(blockMinutes);
		this.clock = clock;
	}

	/**
	 * Barra antes de conferir a senha. Verificar primeiro custaria um BCrypt por tentativa, que e
	 * exatamente o trabalho que um ataque de forca bruta quer nos impor.
	 */
	public void ensureNotBlocked(String email) {
		Attempts attempts = attemptsByEmail.get(normalize(email));

		if (attempts == null || attempts.blockedUntil() == null) {
			return;
		}

		if (Instant.now(clock).isBefore(attempts.blockedUntil())) {
			throw new TooManyRequestsException(
					"Muitas tentativas de login. Tente novamente em alguns minutos.");
		}
	}

	public void registerFailure(String email) {
		String key = normalize(email);
		Instant now = Instant.now(clock);

		attemptsByEmail.compute(key, (ignored, current) -> {
			boolean expired = current == null
					|| now.isAfter(current.firstFailureAt().plus(window));

			int count = expired ? 1 : current.count() + 1;
			Instant firstFailureAt = expired ? now : current.firstFailureAt();

			if (count >= maxAttempts) {
				log.warn("Login bloqueado por excesso de tentativas: {}", key);
				return new Attempts(count, firstFailureAt, now.plus(blockDuration));
			}

			return new Attempts(count, firstFailureAt, null);
		});
	}

	/** Sucesso zera o historico: a conta legitima nao carrega tentativa de quem tentou adivinhar. */
	public void registerSuccess(String email) {
		attemptsByEmail.remove(normalize(email));
	}

	private String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
