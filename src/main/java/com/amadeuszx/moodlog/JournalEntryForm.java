package com.amadeuszx.moodlog;

public class JournalEntryForm {

	@JournalEntryContent
	private String content;

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
