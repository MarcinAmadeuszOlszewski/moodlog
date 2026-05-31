package com.amadeuszx.moodlog;

public class MoodClassificationFailedException extends RuntimeException {

	public MoodClassificationFailedException(String message) {
		super(message);
	}

	public MoodClassificationFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
