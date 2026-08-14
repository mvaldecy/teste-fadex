package br.org.fadex.helpdesk.security;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class TemporaryPasswordGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
	private static final int LENGTH = 16;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		StringBuilder password = new StringBuilder(LENGTH);

		for (int index = 0; index < LENGTH; index++) {
			int position = secureRandom.nextInt(ALPHABET.length());
			password.append(ALPHABET.charAt(position));
		}

		return password.toString();
	}
}
