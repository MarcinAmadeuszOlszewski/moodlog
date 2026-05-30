package com.amadeuszx.moodlog;

public class InvalidPasswordException extends RuntimeException {

	private final int minimumLength;

	public InvalidPasswordException(int minimumLength) {
		super("Hasło musi mieć co najmniej " + minimumLength + " znaków.");
		this.minimumLength = minimumLength;
	}

	public int getMinimumLength() {
		return minimumLength;
	}
}
