package br.org.fadex.helpdesk.exception;

public record FieldErrorResponse(
		String field,
		String message
) {
}
