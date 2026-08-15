package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApplicationException {

	private static final String CODE = "TOO_MANY_REQUESTS";

	public TooManyRequestsException(String message) {
		super(CODE, message, HttpStatus.TOO_MANY_REQUESTS);
	}
}
