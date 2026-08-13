package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApplicationException {

	private static final String CODE = "CONFLICT";

	public ConflictException(String message) {
		super(CODE, message, HttpStatus.CONFLICT);
	}
}
