package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApplicationException {

	private static final String CODE = "FORBIDDEN";

	public ForbiddenException(String message) {
		super(CODE, message, HttpStatus.FORBIDDEN);
	}
}
