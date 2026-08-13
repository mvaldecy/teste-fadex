package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApplicationException {

	private static final String CODE = "UNAUTHORIZED";

	public UnauthorizedException(String message) {
		super(CODE, message, HttpStatus.UNAUTHORIZED);
	}
}
