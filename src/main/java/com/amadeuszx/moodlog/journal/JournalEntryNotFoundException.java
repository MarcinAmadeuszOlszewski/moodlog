package com.amadeuszx.moodlog.journal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class JournalEntryNotFoundException extends RuntimeException {

	public JournalEntryNotFoundException(String message) {
		super(message);
	}
}
