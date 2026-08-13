package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApplicationException {

	private static final String CODE = "NOT_FOUND";

	public NotFoundException(String message) {
		super(CODE, message, HttpStatus.NOT_FOUND);
	}
}
