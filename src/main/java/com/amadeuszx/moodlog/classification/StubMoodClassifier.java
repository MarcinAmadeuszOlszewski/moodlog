package com.amadeuszx.moodlog.classification;

import java.time.Instant;
import java.util.Locale;

import org.springframework.util.StringUtils;

public class StubMoodClassifier implements MoodClassifier {

	private static final String MODEL_NAME = "stub-v1";
	private static final String PROVIDER_NAME = "stub";
	private static final String USER_SAFE_ERROR_MESSAGE = "Nie udało się sklasyfikować wpisu.";

	@Override
	public MoodClassification classify(String entryText) {
		final String normalizedEntryText = normalizeEntryText(entryText);
		final MoodTag moodTag = resolveMoodTag(normalizedEntryText);
		final int moodScore = resolveMoodScore(moodTag);

		return new MoodClassification(
			moodTag,
			moodScore,
			PROVIDER_NAME,
			MODEL_NAME,
			Instant.now()
		);
	}

	private String normalizeEntryText(String entryText) {
		if (!StringUtils.hasText(entryText)) {
			throw new MoodClassificationFailedException(
				USER_SAFE_ERROR_MESSAGE,
				MoodClassificationFailureReason.INVALID_INPUT,
				PROVIDER_NAME,
				MODEL_NAME
			);
		}

		return entryText.toLowerCase(Locale.ROOT);
	}

	private MoodTag resolveMoodTag(String normalizedEntryText) {
		if (containsAny(normalizedEntryText, "przytłocz", "chaos", "nie ogarniam")) {
			return MoodTag.OVERWHELMED;
		}
		if (containsAny(normalizedEntryText, "złość", "wściek", "iryt")) {
			return MoodTag.ANGER;
		}
		if (containsAny(normalizedEntryText, "lęk", "stres", "niepok")) {
			return MoodTag.ANXIETY;
		}
		if (containsAny(normalizedEntryText, "smut", "przykro", "płacz")) {
			return MoodTag.SADNESS;
		}
		if (containsAny(normalizedEntryText, "spok", "cisza", "odpoc")) {
			return MoodTag.CALM;
		}
		if (containsAny(normalizedEntryText, "rado", "szczę", "ciesz", "wdzię")) {
			return MoodTag.JOY;
		}

		return MoodTag.NEUTRAL;
	}

	private int resolveMoodScore(MoodTag moodTag) {
		return switch (moodTag) {
			case JOY -> 84;
			case CALM -> 73;
			case NEUTRAL -> 55;
			case SADNESS -> 41;
			case ANXIETY -> 67;
			case ANGER -> 79;
			case OVERWHELMED -> 88;
			case UNKNOWN -> 0;
		};
	}

	private boolean containsAny(String entryText, String... fragments) {
		for (final String fragment : fragments) {
			if (entryText.contains(fragment)) {
				return true;
			}
		}

		return false;
	}
}
