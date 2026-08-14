package br.org.fadex.helpdesk.mail;

import java.util.regex.Pattern;

public record EmailMessage(
		String to,
		String subject,
		String text
) {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public EmailMessage {
		to = requireNotBlank(to, "Destinatario do e-mail e obrigatorio.");
		subject = requireNotBlank(subject, "Assunto do e-mail e obrigatorio.");
		text = requireNotBlank(text, "Texto do e-mail e obrigatorio.");

		if (!EMAIL_PATTERN.matcher(to).matches()) {
			throw new IllegalArgumentException("Destinatario do e-mail deve ser valido.");
		}
	}

	private static String requireNotBlank(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}

		return value.trim();
	}
}
