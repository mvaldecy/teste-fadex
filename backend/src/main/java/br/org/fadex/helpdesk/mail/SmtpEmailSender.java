package br.org.fadex.helpdesk.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
			SimpleMailMessage email = new SimpleMailMessage();
			email.setFrom(from);
			email.setTo(message.to());
			email.setSubject(message.subject());
			email.setText(message.text());
			mailSender.send(email);
		} catch (MailException exception) {
			throw new EmailDeliveryException("Nao foi possivel enviar o e-mail.", exception);
		}
	}
}
