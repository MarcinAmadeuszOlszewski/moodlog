package com.amadeuszx.moodlog.user.register;

public class DuplicateUserAccountException extends RuntimeException {

	public DuplicateUserAccountException() {
		super("User account already exists");
	}
}
