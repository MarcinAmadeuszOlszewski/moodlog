package com.amadeuszx.moodlog;

import java.time.Instant;

import org.springframework.util.StringUtils;

public record MoodClassification(
	MoodTag moodTag,
	int moodScore,
	String provider,
	String model,
	Instant classifiedAt
) {

	public MoodClassification {
		if (moodTag == null) {
			throw new IllegalArgumentException("Mood tag is required.");
		}
		if (moodScore < 0 || moodScore > 100) {
			throw new IllegalArgumentException("Mood score must be between 0 and 100.");
		}
		if (!StringUtils.hasText(provider)) {
			throw new IllegalArgumentException("Provider is required.");
		}
		if (!StringUtils.hasText(model)) {
			throw new IllegalArgumentException("Model is required.");
		}
		if (classifiedAt == null) {
			throw new IllegalArgumentException("Classification timestamp is required.");
		}
	}
}
