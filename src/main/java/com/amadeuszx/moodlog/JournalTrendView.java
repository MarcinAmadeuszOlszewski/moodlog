package com.amadeuszx.moodlog;

import java.time.LocalDate;
import java.util.List;

public record JournalTrendView(
	CurrentWeekSummary currentWeekSummary,
	DailyTrendSeries completedSevenDayTrend,
	DailyTrendSeries completedThirtyDayTrend,
	WeeklyTrendSeries completedWeeklyTrend,
	boolean empty
) {

	public record CurrentWeekSummary(
		int entriesCount,
		int daysWithEntries,
		String dominantMoodLabel,
		Integer averageMoodScore,
		boolean empty
	) {
	}

	public record DailyTrendSeries(
		List<DailyTrendPoint> points,
		List<String> chartLabels,
		List<Integer> chartValues,
		boolean empty,
		boolean sparse
	) {
	}

	public record DailyTrendPoint(
		LocalDate date,
		String label,
		Integer averageMoodScore,
		int entryCount
	) {
	}

	public record WeeklyTrendSeries(
		List<WeeklyTrendPoint> points,
		List<String> chartLabels,
		List<Integer> chartValues,
		boolean empty,
		boolean sparse
	) {
	}

	public record WeeklyTrendPoint(
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		String label,
		Integer averageMoodScore,
		int entryCount
	) {
	}
}
