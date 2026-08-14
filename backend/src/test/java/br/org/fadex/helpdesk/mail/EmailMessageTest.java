package br.org.fadex.helpdesk.mail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMessageTest {

	@Test
	void deveCriarMensagemComCamposNormalizados() {
		EmailMessage message = new EmailMessage(" usuario@fadex.org.br ", " Assunto ", " Texto ");

		assertThat(message.to()).isEqualTo("usuario@fadex.org.br");
		assertThat(message.subject()).isEqualTo("Assunto");
		assertThat(message.text()).isEqualTo("Texto");
	}

	@Test
	void deveRejeitarDestinatarioVazio() {
		assertThatThrownBy(() -> new EmailMessage("", "Assunto", "Texto"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Destinatario do e-mail e obrigatorio.");
	}

	@Test
	void deveRejeitarDestinatarioInvalido() {
		assertThatThrownBy(() -> new EmailMessage("email-invalido", "Assunto", "Texto"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Destinatario do e-mail deve ser valido.");
	}

	@Test
	void deveRejeitarAssuntoVazio() {
		assertThatThrownBy(() -> new EmailMessage("usuario@fadex.org.br", "", "Texto"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Assunto do e-mail e obrigatorio.");
	}

	@Test
	void deveRejeitarTextoVazio() {
		assertThatThrownBy(() -> new EmailMessage("usuario@fadex.org.br", "Assunto", ""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Texto do e-mail e obrigatorio.");
	}
}
