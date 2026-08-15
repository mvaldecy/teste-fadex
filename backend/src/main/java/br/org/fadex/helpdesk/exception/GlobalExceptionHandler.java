package br.org.fadex.helpdesk.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApplicationException.class)
	public ResponseEntity<ErrorResponse> handleApplicationException(
			ApplicationException exception,
			HttpServletRequest request
	) {
		ErrorResponse error = createErrorResponse(
				exception.getCode(),
				exception.getMessage(),
				exception.getStatus(),
				request.getRequestURI(),
				List.of()
		);

		return ResponseEntity.status(exception.getStatus()).body(error);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		List<FieldErrorResponse> fields = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(this::toFieldErrorResponse)
				.toList();

		ErrorResponse error = createErrorResponse(
				"VALIDATION_ERROR",
				"Existem campos inválidos na requisição.",
				HttpStatus.BAD_REQUEST,
				request.getRequestURI(),
				fields
		);

		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(
			ConstraintViolationException exception,
			HttpServletRequest request
	) {
		List<FieldErrorResponse> fields = exception.getConstraintViolations()
				.stream()
				.map(violation -> new FieldErrorResponse(
						violation.getPropertyPath().toString(),
						violation.getMessage()
				))
				.toList();

		ErrorResponse error = createErrorResponse(
				"VALIDATION_ERROR",
				"Existem parâmetros inválidos na requisição.",
				HttpStatus.BAD_REQUEST,
				request.getRequestURI(),
				fields
		);

		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request
	) {
		List<FieldErrorResponse> fields = List.of(new FieldErrorResponse(
				exception.getName(),
				"Valor informado em formato inválido."
		));

		ErrorResponse error = createErrorResponse(
				"INVALID_PARAMETER",
				"Existem parâmetros inválidos na requisição.",
				HttpStatus.BAD_REQUEST,
				request.getRequestURI(),
				fields
		);

		return ResponseEntity.badRequest().body(error);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
			HttpMessageNotReadableException exception,
			HttpServletRequest request
	) {
		ErrorResponse error = createErrorResponse(
				"INVALID_BODY",
				"O corpo da requisição está inválido.",
				HttpStatus.BAD_REQUEST,
				request.getRequestURI(),
				List.of()
		);

		return ResponseEntity.badRequest().body(error);
	}

	/**
	 * Papel insuficiente e 403, nao 500.
	 *
	 * O {@code @PreAuthorize} lanca {@link AccessDeniedException}, que nao e {@code
	 * ApplicationException} e caia direto no catch-all abaixo: todo endpoint anotado respondia
	 * "erro interno" para quem simplesmente nao tinha permissao. O sintoma enganava duas vezes —
	 * escondia a causa de quem chamava e sugeria defeito onde havia regra funcionando.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDeniedException(
			AccessDeniedException exception,
			HttpServletRequest request
	) {
		ErrorResponse error = createErrorResponse(
				"FORBIDDEN",
				"Acesso negado.",
				HttpStatus.FORBIDDEN,
				request.getRequestURI(),
				List.of()
		);

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(
			Exception exception,
			HttpServletRequest request
	) {
		// Registrado antes de responder. Sem este log, o 500 saia sem rastro nenhum e a causa
		// tinha de ser deduzida por fora, testando a API — foi assim que este proprio handler
		// escondeu por semanas um 403 disfarcado de erro interno.
		log.error("Erro nao tratado em {}: {}", request.getRequestURI(), exception.getMessage(), exception);

		ErrorResponse error = createErrorResponse(
				"INTERNAL_ERROR",
				"Ocorreu um erro interno.",
				HttpStatus.INTERNAL_SERVER_ERROR,
				request.getRequestURI(),
				List.of()
		);

		return ResponseEntity.internalServerError().body(error);
	}

	private ErrorResponse createErrorResponse(
			String code,
			String message,
			HttpStatusCode status,
			String path,
			List<FieldErrorResponse> fields
	) {
		return new ErrorResponse(
				code,
				message,
				status.value(),
				path,
				LocalDateTime.now(),
				fields
		);
	}

	private FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
		return new FieldErrorResponse(
				fieldError.getField(),
				fieldError.getDefaultMessage()
		);
	}
}
