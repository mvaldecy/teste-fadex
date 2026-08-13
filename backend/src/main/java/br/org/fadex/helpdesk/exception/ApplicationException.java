package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatusCode;

public abstract class ApplicationException extends RuntimeException {

	private final String code;
	private final HttpStatusCode status;

	protected ApplicationException(String code, String message, HttpStatusCode status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String getCode() {
		return code;
	}

	public HttpStatusCode getStatus() {
		return status;
	}
}
