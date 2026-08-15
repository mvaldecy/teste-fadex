package br.org.fadex.helpdesk.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailSender(
			JavaMailSender mailSender,
			@Value("${app.mail.from}") String from
	) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(EmailMessage message) {
		try {
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(
					mimeMessage,
					message.hasHtml(),
					StandardCharsets.UTF_8.name()
			);

			helper.setFrom(from);
			helper.setTo(message.to());
			helper.setSubject(message.subject());

			if (message.hasHtml()) {
				helper.setText(message.text(), message.html());
			} else {
				helper.setText(message.text(), false);
			}

			mailSender.send(mimeMessage);
		} catch (MailException | MessagingException exception) {
			throw new EmailDeliveryException("Nao foi possivel enviar o e-mail.", exception);
		}
	}
}
