package br.org.fadex.helpdesk.mail;

import java.util.regex.Pattern;

/**
 * Mensagem de e-mail em duas versoes.
 *
 * O {@code text} e obrigatorio e o {@code html} e opcional: quando os dois existem, o envio sai
 * como {@code multipart/alternative}, e o cliente que bloqueia HTML cai no texto puro.
 */
public record EmailMessage(
		String to,
		String subject,
		String text,
		String html
) {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public EmailMessage {
		to = requireNotBlank(to, "Destinatario do e-mail e obrigatorio.");
		subject = requireNotBlank(subject, "Assunto do e-mail e obrigatorio.");
		text = requireNotBlank(text, "Texto do e-mail e obrigatorio.");
		html = html == null || html.isBlank() ? null : html;

		if (!EMAIL_PATTERN.matcher(to).matches()) {
			throw new IllegalArgumentException("Destinatario do e-mail deve ser valido.");
		}
	}

	public EmailMessage(String to, String subject, String text) {
		this(to, subject, text, null);
	}

	public boolean hasHtml() {
		return html != null;
	}

	private static String requireNotBlank(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}

		return value.trim();
	}
}
