package com.amadeuszx.moodlog;

import jakarta.validation.constraints.NotBlank;

public class JournalEntryForm {

	@NotBlank(message = "Wpis nie może być pusty.")
	private String content;

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
