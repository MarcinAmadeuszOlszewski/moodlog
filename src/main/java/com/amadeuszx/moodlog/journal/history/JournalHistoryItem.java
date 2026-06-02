package com.amadeuszx.moodlog.journal.history;

import java.time.LocalDate;
import java.time.LocalTime;

public record JournalHistoryItem(
	LocalDate displayDate,
	LocalTime displayTime,
	String excerpt,
	String moodLabel,
	int moodScore
) {
}
