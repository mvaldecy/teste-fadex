package br.org.fadex.helpdesk.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

	@Mock
	private JavaMailSender mailSender;

	private SmtpEmailSender sender;

	@BeforeEach
	void setUp() {
		sender = new SmtpEmailSender(mailSender, "no-reply@fadex.local");
		when(mailSender.createMimeMessage()).thenAnswer(
				invocation -> new JavaMailSenderImpl().createMimeMessage()
		);
	}

	@Test
	void deveEnviarMensagemDeTextoPuroPorSmtp() throws Exception {
		EmailMessage message = new EmailMessage(
				"usuario@fadex.org.br",
				"Chamado criado",
				"Seu chamado foi criado."
		);

		sender.send(message);

		MimeMessage sentMessage = capturarMensagemEnviada();

		assertThat(sentMessage.getFrom()[0].toString()).isEqualTo("no-reply@fadex.local");
		assertThat(sentMessage.getAllRecipients()[0].toString()).isEqualTo("usuario@fadex.org.br");
		assertThat(sentMessage.getSubject()).isEqualTo("Chamado criado");
		assertThat(sentMessage.getContent().toString()).contains("Seu chamado foi criado.");
	}

	@Test
	void deveEnviarMultipartAlternativoQuandoHouverHtml() throws Exception {
		EmailMessage message = new EmailMessage(
				"usuario@fadex.org.br",
				"Chamado atualizado",
				"Versao em texto puro.",
				"<html><body><p>Versao em HTML.</p></body></html>"
		);

		sender.send(message);

		MimeMessage sentMessage = capturarMensagemEnviada();

		assertThat(sentMessage.getContentType()).contains("multipart/mixed");
		assertThat(conteudoBruto(sentMessage)).contains("Versao em texto puro.");
		assertThat(conteudoBruto(sentMessage)).contains("Versao em HTML.");
		assertThat(conteudoBruto(sentMessage)).contains("multipart/alternative");
	}

	@Test
	void deveEncapsularFalhaDeEnvioSmtp() {
		EmailMessage message = new EmailMessage(
				"usuario@fadex.org.br",
				"Chamado criado",
				"Seu chamado foi criado."
		);
		doThrow(new MailSendException("falha")).when(mailSender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> sender.send(message))
				.isInstanceOf(EmailDeliveryException.class)
				.hasMessage("Nao foi possivel enviar o e-mail.")
				.hasCauseInstanceOf(MailSendException.class);
	}

	private MimeMessage capturarMensagemEnviada() {
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());

		try {
			// O envio esta mockado, entao ninguem chamou saveChanges(); sem isso o Content-Type
			// ainda e o valor default e nao o multipart montado pelo helper.
			captor.getValue().saveChanges();
		} catch (jakarta.mail.MessagingException exception) {
			throw new IllegalStateException(exception);
		}

		return captor.getValue();
	}

	private String conteudoBruto(MimeMessage message) throws Exception {
		java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
		message.writeTo(output);

		return output.toString(java.nio.charset.StandardCharsets.UTF_8);
	}
}
