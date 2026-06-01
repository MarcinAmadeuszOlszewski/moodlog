package com.amadeuszx.moodlog;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

public class JournalEntryContentValidator implements ConstraintValidator<JournalEntryContent, String> {

	private final int journalMaxContentLength;

	public JournalEntryContentValidator(@Value("${moodlog.journal.max-content-length:2000}") int journalMaxContentLength) {
		this.journalMaxContentLength = journalMaxContentLength;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			addViolation(context, "Wpis nie może być pusty.");
			return false;
		}

		if (value.length() > journalMaxContentLength) {
			addViolation(context, "Wpis może mieć maksymalnie " + journalMaxContentLength + " znaków.");
			return false;
		}

		return true;
	}

	private void addViolation(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
	}
}
