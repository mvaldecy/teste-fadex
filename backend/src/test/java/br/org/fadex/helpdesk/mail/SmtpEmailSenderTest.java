package br.org.fadex.helpdesk.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

	@Mock
	private JavaMailSender mailSender;

	@Test
	void deveEnviarMensagemSimplesPorSmtp() {
		SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@fadex.local");
		EmailMessage message = new EmailMessage("usuario@fadex.org.br", "Chamado criado", "Seu chamado foi criado.");

		sender.send(message);

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		SimpleMailMessage sentMessage = captor.getValue();

		assertThat(sentMessage.getFrom()).isEqualTo("no-reply@fadex.local");
		assertThat(sentMessage.getTo()).containsExactly("usuario@fadex.org.br");
		assertThat(sentMessage.getSubject()).isEqualTo("Chamado criado");
		assertThat(sentMessage.getText()).isEqualTo("Seu chamado foi criado.");
	}

	@Test
	void deveEncapsularFalhaDeEnvioSmtp() {
		SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@fadex.local");
		EmailMessage message = new EmailMessage("usuario@fadex.org.br", "Chamado criado", "Seu chamado foi criado.");
		doThrow(new MailSendException("falha")).when(mailSender).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> sender.send(message))
				.isInstanceOf(EmailDeliveryException.class)
				.hasMessage("Nao foi possivel enviar o e-mail.")
				.hasCauseInstanceOf(MailSendException.class);
	}
}
