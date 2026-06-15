package com.amadeuszx.moodlog.journal.history;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodTag;

public record JournalHistoryItem(
	UUID id,
	LocalDate displayDate,
	LocalTime displayTime,
	String excerpt,
	MoodTag effectiveMoodTag,
	String moodLabel,
	Integer moodScore
) {
}
