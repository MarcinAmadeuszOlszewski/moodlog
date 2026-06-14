package com.amadeuszx.moodlog.journal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodClassifier;
import com.amadeuszx.moodlog.classification.MoodTag;
import com.amadeuszx.moodlog.user.UserAccount;
import com.amadeuszx.moodlog.user.UserAccountRepository;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class JournalEntryServiceDstTests {

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@Autowired
	private JournalEntryService journalEntryService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@MockitoBean
	private Clock clock;

	@MockitoBean
	private MoodClassifier moodClassifier;

	@BeforeEach
	void setUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
	}

	@Test
	@DisplayName("buckets entries correctly on Warsaw spring-forward day (23-hour day)")
	void bucketsEntriesCorrectlyOnWarsawSpringForwardDay() {
		given(clock.instant()).willReturn(Instant.parse("2026-03-29T10:00:00Z"));
		val owner = createUserAccount("ela@example.com", "Europe/Warsaw");
		// 2026-03-28T22:59Z = Warsaw 2026-03-28T23:59 CET → March 28 → 7-day completed series
		val completedDayEntry = createJournalEntry(owner, MoodTag.CALM, 70, Instant.parse("2026-03-28T22:59:00Z"));
		// 2026-03-28T23:00Z = Warsaw 2026-03-29T00:00 CET (midnight on spring-forward day) → March 29 → current week
		val currentWeekEntry = createJournalEntry(owner, MoodTag.JOY, 85, Instant.parse("2026-03-28T23:00:00Z"));
		journalEntryRepository.saveAllAndFlush(List.of(completedDayEntry, currentWeekEntry));

		val trendView = journalEntryService.getTrendView(owner.getEmail());

		// March 29 is Sunday; currentWeekStart = March 23 (Monday), so March 28 is also in current week
		assertEquals(2, trendView.currentWeekSummary().entriesCount());
		// Only Entry 1 (March 28) lands in the completed series; Entry 2 (March 29 = today) is excluded
		assertEquals(70, trendView.completedSevenDayTrend().points().getLast().averageMoodScore());
	}

	@Test
	@DisplayName("buckets entries correctly on Warsaw fall-back day (25-hour day)")
	void bucketsEntriesCorrectlyOnWarsawFallBackDay() {
		given(clock.instant()).willReturn(Instant.parse("2026-10-25T10:00:00Z"));
		val owner = createUserAccount("ela@example.com", "Europe/Warsaw");
		// 2026-10-24T21:59Z = Warsaw 2026-10-24T23:59 CEST → October 24 → 7-day completed series
		val completedDayEntry = createJournalEntry(owner, MoodTag.CALM, 70, Instant.parse("2026-10-24T21:59:00Z"));
		// 2026-10-24T22:00Z = Warsaw 2026-10-25T00:00 CEST (midnight on fall-back day) → October 25 → current week
		val currentWeekEntry = createJournalEntry(owner, MoodTag.JOY, 85, Instant.parse("2026-10-24T22:00:00Z"));
		journalEntryRepository.saveAllAndFlush(List.of(completedDayEntry, currentWeekEntry));

		val trendView = journalEntryService.getTrendView(owner.getEmail());

		// October 25 is Sunday; currentWeekStart = October 19 (Monday), so October 24 is also in current week
		assertEquals(2, trendView.currentWeekSummary().entriesCount());
		// Only Entry 1 (October 24) lands in the completed series; Entry 2 (October 25 = today) is excluded
		assertEquals(70, trendView.completedSevenDayTrend().points().getLast().averageMoodScore());
	}

	@Test
	@DisplayName("includes an entry whose createdAt equals the exact window start boundary")
	void includesEntryAtExactWindowStartBoundary() {
		given(clock.instant()).willReturn(Instant.parse("2026-06-01T10:00:00Z"));
		val owner = createUserAccount("ela@example.com", "Europe/Warsaw");
		// windowStartInclusive = May 25 midnight Warsaw = 2026-05-24T22:00:00Z (Warsaw is UTC+2 in summer)
		val boundaryEntry = createJournalEntry(owner, MoodTag.CALM, 70, Instant.parse("2026-05-24T22:00:00Z"));
		journalEntryRepository.saveAndFlush(boundaryEntry);

		val trendView = journalEntryService.getTrendView(owner.getEmail());

		assertEquals(LocalDate.of(2026, 5, 25), trendView.completedSevenDayTrend().points().getFirst().date());
		assertEquals(70, trendView.completedSevenDayTrend().points().getFirst().averageMoodScore());
	}

	private UserAccount createUserAccount(String email, String timezone) {
		val createdAt = Instant.now();
		val userAccount = new UserAccount(
			UUID.randomUUID(),
			email,
			"$2a$10$storedHash",
			true,
			createdAt,
			createdAt,
			timezone
		);
		return userAccountRepository.saveAndFlush(userAccount);
	}

	private JournalEntry createJournalEntry(UserAccount owner, MoodTag moodTag, int moodScore, Instant createdAt) {
		return new JournalEntry(
			UUID.randomUUID(),
			owner,
			"Wpis testowy.",
			moodTag,
			moodScore,
			null,
			null,
			"stub",
			"stub-v1",
			createdAt.plusSeconds(5),
			createdAt,
			createdAt
		);
	}
}
