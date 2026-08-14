package br.org.fadex.helpdesk.ai;

public class AiIntegrationException extends RuntimeException {

	public AiIntegrationException(String message) {
		super(message);
	}

	public AiIntegrationException(String message, Throwable cause) {
		super(message, cause);
	}
}
