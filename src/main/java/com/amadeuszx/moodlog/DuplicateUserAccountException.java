package com.amadeuszx.moodlog;

public class DuplicateUserAccountException extends RuntimeException {

	public DuplicateUserAccountException() {
		super("User account already exists");
	}
}
