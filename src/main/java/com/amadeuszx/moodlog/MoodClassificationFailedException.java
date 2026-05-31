package com.amadeuszx.moodlog;

import org.springframework.util.StringUtils;

public class MoodClassificationFailedException extends RuntimeException {

	private static final String UNKNOWN_MODEL = "unknown";
	private static final String UNKNOWN_PROVIDER = "unknown";

	private final String model;
	private final String provider;
	private final MoodClassificationFailureReason reason;

	public MoodClassificationFailedException(String message) {
		this(message, MoodClassificationFailureReason.INVALID_RESPONSE, UNKNOWN_PROVIDER, UNKNOWN_MODEL, null);
	}

	public MoodClassificationFailedException(String message, Throwable cause) {
		this(message, MoodClassificationFailureReason.INVALID_RESPONSE, UNKNOWN_PROVIDER, UNKNOWN_MODEL, cause);
	}

	public MoodClassificationFailedException(
		String message,
		MoodClassificationFailureReason reason,
		String provider,
		String model
	) {
		this(message, reason, provider, model, null);
	}

	public MoodClassificationFailedException(
		String message,
		MoodClassificationFailureReason reason,
		String provider,
		String model,
		Throwable cause
	) {
		super(message, cause);
		this.reason = reason == null ? MoodClassificationFailureReason.PROVIDER_ERROR : reason;
		this.provider = normalizeMetadata(provider, UNKNOWN_PROVIDER);
		this.model = normalizeMetadata(model, UNKNOWN_MODEL);
	}

	public String getModel() {
		return model;
	}

	public String getProvider() {
		return provider;
	}

	public MoodClassificationFailureReason getReason() {
		return reason;
	}

	private static String normalizeMetadata(String value, String fallback) {
		if (StringUtils.hasText(value)) {
			return value;
		}

		return fallback;
	}
}
