package br.org.fadex.helpdesk.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
		String code,
		String message,
		Integer status,
		String path,
		LocalDateTime timestamp,
		List<FieldErrorResponse> fields
) {
}
