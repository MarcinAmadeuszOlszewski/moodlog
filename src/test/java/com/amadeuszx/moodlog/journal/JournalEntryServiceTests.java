package com.amadeuszx.moodlog.journal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodClassification;
import com.amadeuszx.moodlog.classification.MoodClassificationFailedException;
import com.amadeuszx.moodlog.classification.MoodClassifier;
import com.amadeuszx.moodlog.classification.MoodTag;
import com.amadeuszx.moodlog.user.UserAccount;
import com.amadeuszx.moodlog.user.UserAccountRepository;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import(FixedClockTestConfiguration.class)
class JournalEntryServiceTests {

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@Autowired
	private JournalEntryService journalEntryService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@MockitoBean
	private MoodClassifier moodClassifier;

	@BeforeEach
	void setUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
	}

	@Test
	@DisplayName("saves a journal entry after successful classification")
	void savesJournalEntryAfterSuccessfulClassification() {
		val owner = createUserAccount("ela@example.com");
		val classifiedAt = Instant.parse("2026-05-31T09:30:00Z");
		val expectedClassification = new MoodClassification(MoodTag.CALM, 74, "stub", "stub-v1", classifiedAt);

		given(moodClassifier.classify("Po spacerze czuję spokój.")).willReturn(expectedClassification);

		val savedEntry = journalEntryService.saveEntry(owner.getEmail(), "Po spacerze czuję spokój.");
		val persistedEntries = journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(owner.getId());

		assertEquals(1, persistedEntries.size());
		assertEquals(savedEntry.getId(), persistedEntries.getFirst().getId());
		assertEquals(MoodTag.CALM, savedEntry.getSystemMoodTag());
		assertEquals(74, savedEntry.getSystemMoodScore());
		assertEquals("stub", savedEntry.getClassifierProvider());
		assertEquals("stub-v1", savedEntry.getClassifierModel());
		assertEquals(classifiedAt, savedEntry.getClassifiedAt());
	}

	@Test
	@DisplayName("logs classification success without exposing the entry text")
	void logsClassificationSuccessWithoutExposingTheEntryText(CapturedOutput output) {
		val owner = createUserAccount("ela@example.com");
		val entryText = "To jest prywatny wpis o spokojnym spacerze nad rzeką.";
		val classifiedAt = Instant.parse("2026-05-31T09:30:00Z");
		val expectedClassification = new MoodClassification(MoodTag.CALM, 74, "openai", "gpt-4o-mini", classifiedAt);

		given(moodClassifier.classify(entryText)).willReturn(expectedClassification);

		journalEntryService.saveEntry(owner.getEmail(), entryText);

		assertTrue(output.getOut().contains("journal.classification.success identifier=email-hash:"));
		assertTrue(output.getOut().contains("provider=openai"));
		assertTrue(output.getOut().contains("model=gpt-4o-mini"));
		assertFalse(output.getOut().contains(entryText));
	}

	@Test
	@DisplayName("saves entry with unknown mood when classifier fails")
	void savesEntryWithUnknownMoodWhenClassifierFails() {
		val owner = createUserAccount("ela@example.com");

		given(moodClassifier.classify("Nie mogę się uspokoić."))
			.willThrow(new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu."));

		val savedEntry = journalEntryService.saveEntry(owner.getEmail(), "Nie mogę się uspokoić.");

		assertEquals(1L, journalEntryRepository.count());
		assertEquals(MoodTag.UNKNOWN, savedEntry.getSystemMoodTag());
		assertNull(savedEntry.getSystemMoodScore());
	}

	@Test
	@DisplayName("saves entry with unknown mood when classification payload is invalid")
	void savesEntryWithUnknownMoodWhenClassificationPayloadIsInvalid() {
		val owner = createUserAccount("ela@example.com");

		given(moodClassifier.classify("Czuję chaos."))
			.willThrow(new IllegalArgumentException("Mood score must be between 0 and 100."));

		val savedEntry = journalEntryService.saveEntry(owner.getEmail(), "Czuję chaos.");

		assertEquals(1L, journalEntryRepository.count());
		assertEquals(MoodTag.UNKNOWN, savedEntry.getSystemMoodTag());
		assertNull(savedEntry.getSystemMoodScore());
	}

	@Test
	@DisplayName("returns recent entries newest first for the current account")
	void returnsRecentEntriesNewestFirstForTheCurrentAccount() {
		val owner = createUserAccount("ela@example.com");
		val otherOwner = createUserAccount("ola@example.com");
		val oldestEntry = createJournalEntry(
			owner,
			"Rano było trudno.",
			MoodTag.SADNESS,
			44,
			Instant.parse("2026-05-31T08:00:00Z")
		);
		val newestEntry = createJournalEntry(
			owner,
			"Wieczorem wrócił spokój.",
			MoodTag.CALM,
			71,
			Instant.parse("2026-05-31T18:00:00Z")
		);
		val foreignEntry = createJournalEntry(
			otherOwner,
			"To jest wpis innego użytkownika.",
			MoodTag.JOY,
			83,
			Instant.parse("2026-05-31T20:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(oldestEntry, newestEntry, foreignEntry));

		val recentEntries = journalEntryService.getRecentEntries(owner.getEmail());

		assertEquals(2, recentEntries.size());
		assertEquals(newestEntry.getId(), recentEntries.get(0).getId());
		assertEquals(oldestEntry.getId(), recentEntries.get(1).getId());
	}

	@Test
	@DisplayName("returns history entries with Europe Warsaw timestamps and effective moods")
	void returnsHistoryEntriesWithEuropeWarsawTimestampsAndEffectiveMoods() {
		val owner = createUserAccount("ela@example.com");
		val overriddenEntry = createJournalEntry(
			owner,
			"Po wieczornym spacerze wróciło dużo radości.",
			MoodTag.SADNESS,
			28,
			MoodTag.JOY,
			90,
			Instant.parse("2026-05-31T21:35:40Z")
		);

		journalEntryRepository.saveAndFlush(overriddenEntry);

		val historyPage = journalEntryService.getHistoryEntries(owner.getEmail(), 0);
		val historyItem = historyPage.getContent().getFirst();

		assertEquals(1L, historyPage.getTotalElements());
		assertEquals(LocalDate.of(2026, 5, 31), historyItem.displayDate());
		assertEquals(LocalTime.of(23, 35), historyItem.displayTime());
		assertEquals("Radość", historyItem.moodLabel());
		assertEquals(90, historyItem.moodScore());
	}

	@Test
	@DisplayName("keeps completed daily periods separate from current week data in Europe Warsaw")
	void keepsCompletedDailyPeriodsSeparateFromCurrentWeekDataInEuropeWarsaw() {
		val owner = createUserAccount("ela@example.com");
		val completedDayEntry = createJournalEntry(
			owner,
			"Jeszcze końcówka niedzieli w spokoju.",
			MoodTag.CALM,
			80,
			Instant.parse("2026-05-31T21:59:00Z")
		);
		val currentWeekEntry = createJournalEntry(
			owner,
			"Nowy tydzień zaczął się z energią.",
			MoodTag.JOY,
			90,
			Instant.parse("2026-05-31T22:05:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(completedDayEntry, currentWeekEntry));

		val trendView = journalEntryService.getTrendView(owner.getEmail());

		assertEquals(1, trendView.currentWeekSummary().entriesCount());
		assertEquals(1, trendView.currentWeekSummary().daysWithEntries());
		assertEquals("Radość", trendView.currentWeekSummary().dominantMoodLabel());
		assertEquals(90, trendView.currentWeekSummary().averageMoodScore());
		assertEquals(7, trendView.completedSevenDayTrend().points().size());
		assertEquals(LocalDate.of(2026, 5, 25), trendView.completedSevenDayTrend().points().getFirst().date());
		assertEquals(LocalDate.of(2026, 5, 31), trendView.completedSevenDayTrend().points().getLast().date());
		assertEquals(80, trendView.completedSevenDayTrend().points().getLast().averageMoodScore());
	}

	@Test
	@DisplayName("builds completed thirty day and weekly trends from effective moods")
	void buildsCompletedThirtyDayAndWeeklyTrendsFromEffectiveMoods() {
		val owner = createUserAccount("ela@example.com");
		val outsideThirtyDayEntry = createJournalEntry(
			owner,
			"To jest jeszcze poza pełnym oknem trzydziestodniowym.",
			MoodTag.JOY,
			99,
			Instant.parse("2026-05-01T10:00:00Z")
		);
		val firstThirtyDayEntry = createJournalEntry(
			owner,
			"To powinno wejść do pełnego okna trzydziestu dni.",
			MoodTag.NEUTRAL,
			30,
			Instant.parse("2026-05-01T22:30:00Z")
		);
		val overriddenWeeklyEntry = createJournalEntry(
			owner,
			"Pod koniec tygodnia override zmienił ocenę wpisu.",
			MoodTag.SADNESS,
			20,
			MoodTag.CALM,
			70,
			Instant.parse("2026-05-29T12:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(outsideThirtyDayEntry, firstThirtyDayEntry, overriddenWeeklyEntry));

		val trendView = journalEntryService.getTrendView(owner.getEmail());

		assertEquals(LocalDate.of(2026, 5, 2), trendView.completedThirtyDayTrend().points().getFirst().date());
		assertEquals(30, trendView.completedThirtyDayTrend().points().getFirst().averageMoodScore());
		assertEquals(8, trendView.completedWeeklyTrend().points().size());
		assertEquals(LocalDate.of(2026, 4, 6), trendView.completedWeeklyTrend().points().getFirst().weekStartDate());
		assertEquals(LocalDate.of(2026, 5, 25), trendView.completedWeeklyTrend().points().getLast().weekStartDate());
		assertEquals(70, trendView.completedWeeklyTrend().points().getLast().averageMoodScore());
	}

	@Test
	@DisplayName("keeps sparse trends gap aware instead of filling missing days or weeks")
	void keepsSparseTrendsGapAwareInsteadOfFillingMissingDaysOrWeeks() {
		val owner = createUserAccount("ela@example.com");
		val sparseEntry = createJournalEntry(
			owner,
			"Tylko jeden wpis w całym obserwowanym tygodniu.",
			MoodTag.CALM,
			65,
			Instant.parse("2026-05-27T07:00:00Z")
		);

		journalEntryRepository.saveAndFlush(sparseEntry);

		val trendView = journalEntryService.getTrendView(owner.getEmail());

		assertTrue(trendView.currentWeekSummary().empty());
		assertFalse(trendView.completedSevenDayTrend().empty());
		assertTrue(trendView.completedSevenDayTrend().sparse());
		assertNull(trendView.completedSevenDayTrend().points().getFirst().averageMoodScore());
		assertEquals(65, trendView.completedSevenDayTrend().points().get(2).averageMoodScore());
		assertTrue(trendView.completedThirtyDayTrend().sparse());
		assertTrue(trendView.completedWeeklyTrend().sparse());
		assertNull(trendView.completedWeeklyTrend().points().getFirst().averageMoodScore());
		assertEquals(65, trendView.completedWeeklyTrend().points().getLast().averageMoodScore());
	}

	@Test
	@DisplayName("buckets the same UTC instant into different days for Warsaw and New York users")
	void bucketsSameUtcInstantIntoDifferentDaysForWarsawAndNewYork() {
		val warsawUser = createUserAccount("warsaw@example.com", "Europe/Warsaw");
		val newYorkUser = createUserAccount("newyork@example.com", "America/New_York");
		// 2026-05-31T22:30Z = Warsaw 2026-06-01T00:30 CEST (June 1 = current week)
		//                   = New York 2026-05-31T18:30 EDT (May 31 = completed 7-day series)
		val entryInstant = Instant.parse("2026-05-31T22:30:00Z");
		val warsawEntry = createJournalEntry(warsawUser, "Wpis wieczorny w Warszawie.", MoodTag.CALM, 70, entryInstant);
		val newYorkEntry = createJournalEntry(newYorkUser, "Evening entry in New York.", MoodTag.CALM, 70, entryInstant);
		journalEntryRepository.saveAllAndFlush(List.of(warsawEntry, newYorkEntry));

		val warsawTrend = journalEntryService.getTrendView(warsawUser.getEmail());
		val newYorkTrend = journalEntryService.getTrendView(newYorkUser.getEmail());

		assertEquals(1, warsawTrend.currentWeekSummary().entriesCount());
		assertNull(warsawTrend.completedSevenDayTrend().points().getLast().averageMoodScore());
		assertEquals(0, newYorkTrend.currentWeekSummary().entriesCount());
		assertEquals(70, newYorkTrend.completedSevenDayTrend().points().getLast().averageMoodScore());
	}

	@Test
	@DisplayName("deleteEntry removes the entry when called by the owner")
	void deleteEntryRemovesEntryWhenCalledByOwner() {
		val owner = createUserAccount("ela@example.com");
		val entry = createJournalEntry(owner, "Spokojny dzień.", MoodTag.CALM, 70, Instant.parse("2026-05-31T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);

		journalEntryService.deleteEntry(owner.getEmail(), entry.getId());

		assertEquals(0L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("deleteEntry throws JournalEntryNotFoundException when called by a non-owner")
	void deleteEntryThrowsNotFoundExceptionWhenCalledByNonOwner() {
		val owner = createUserAccount("ela@example.com");
		val other = createUserAccount("ola@example.com");
		val entry = createJournalEntry(owner, "Spokojny dzień.", MoodTag.CALM, 70, Instant.parse("2026-05-31T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);

		assertThrows(JournalEntryNotFoundException.class, () ->
			journalEntryService.deleteEntry(other.getEmail(), entry.getId())
		);
		assertEquals(1L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("applyMoodOverride sets overrideMoodTag and subsequent history returns corrected label")
	void applyMoodOverrideSetsOverrideMoodTagAndHistoryReturnsCorrectLabel() {
		val owner = createUserAccount("ela@example.com");
		val entry = createJournalEntry(owner, "Trudny poranek.", MoodTag.SADNESS, 28, Instant.parse("2026-05-31T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);

		journalEntryService.applyMoodOverride(owner.getEmail(), entry.getId(), MoodTag.JOY);

		val historyPage = journalEntryService.getHistoryEntries(owner.getEmail(), 0);
		val historyItem = historyPage.getContent().getFirst();
		assertEquals("Radość", historyItem.moodLabel());
		assertEquals(28, historyItem.moodScore());
	}

	@Test
	@DisplayName("applyMoodOverride throws JournalEntryNotFoundException for non-owner")
	void applyMoodOverrideThrowsNotFoundExceptionForNonOwner() {
		val owner = createUserAccount("ela@example.com");
		val other = createUserAccount("ola@example.com");
		val entry = createJournalEntry(owner, "Trudny poranek.", MoodTag.SADNESS, 28, Instant.parse("2026-05-31T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);

		assertThrows(JournalEntryNotFoundException.class, () ->
			journalEntryService.applyMoodOverride(other.getEmail(), entry.getId(), MoodTag.JOY)
		);
	}

	@Test
	@DisplayName("resolveEffectiveMood returns corrected tag with system score when only overrideMoodTag is set")
	void resolveEffectiveMoodReturnsCorrectedTagWithSystemScoreForTagOnlyOverride() {
		val owner = createUserAccount("ela@example.com");
		val entry = createJournalEntry(owner, "Wieczorny spacer poprawił humor.", MoodTag.SADNESS, 28, MoodTag.JOY, null,
			Instant.parse("2026-05-31T18:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);

		val historyPage = journalEntryService.getHistoryEntries(owner.getEmail(), 0);
		val historyItem = historyPage.getContent().getFirst();
		assertEquals("Radość", historyItem.moodLabel());
		assertEquals(28, historyItem.moodScore());
	}

	@Test
	@DisplayName("updateEntryContent reclassifies and clears any existing override; updatedAt advances")
	void updateEntryContentReclassifiesAndClearsOverride() {
		val owner = createUserAccount("ela@example.com");
		val entry = createJournalEntry(owner, "Stara treść.", MoodTag.CALM, 74, MoodTag.JOY, null,
			Instant.parse("2026-05-30T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);
		val classifiedAt = Instant.parse("2026-06-01T10:00:00Z");
		val newClassification = new MoodClassification(MoodTag.SADNESS, 40, "stub", "stub-v1", classifiedAt);
		given(moodClassifier.classify("Nowa treść.")).willReturn(newClassification);

		val updated = journalEntryService.updateEntryContent(owner.getEmail(), entry.getId(), "Nowa treść.");

		assertEquals(MoodTag.SADNESS, updated.getSystemMoodTag());
		assertEquals(40, updated.getSystemMoodScore());
		assertNull(updated.getOverrideMoodTag());
		assertNull(updated.getOverrideMoodScore());
		assertEquals(Instant.parse("2026-06-01T10:00:00Z"), updated.getUpdatedAt());
	}

	@Test
	@DisplayName("updateEntryContent falls back to UNKNOWN with null score when classifier throws; entry is still saved")
	void updateEntryContentFallsBackToUnknownWhenClassifierThrows() {
		val owner = createUserAccount("ela@example.com");
		val entry = createJournalEntry(owner, "Stara treść.", MoodTag.CALM, 74, Instant.parse("2026-05-31T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);
		given(moodClassifier.classify("Chaos w głowie."))
			.willThrow(new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu."));

		val updated = journalEntryService.updateEntryContent(owner.getEmail(), entry.getId(), "Chaos w głowie.");

		assertNotNull(updated);
		assertEquals(MoodTag.UNKNOWN, updated.getSystemMoodTag());
		assertNull(updated.getSystemMoodScore());
		assertEquals(1L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("updateEntryContent throws JournalEntryNotFoundException for non-owner")
	void updateEntryContentThrowsNotFoundExceptionForNonOwner() {
		val owner = createUserAccount("ela@example.com");
		val other = createUserAccount("ola@example.com");
		val entry = createJournalEntry(owner, "Stara treść.", MoodTag.CALM, 74, Instant.parse("2026-05-31T08:00:00Z"));
		journalEntryRepository.saveAndFlush(entry);

		assertThrows(JournalEntryNotFoundException.class, () ->
			journalEntryService.updateEntryContent(other.getEmail(), entry.getId(), "Nowa treść.")
		);
	}

	@Test
	@DisplayName("selectableMoodOptions returns 7 options excluding UNKNOWN with Polish labels")
	void selectableMoodOptionsReturnsSevenOptionsExcludingUnknown() {
		val options = journalEntryService.selectableMoodOptions();

		assertEquals(7, options.size());
		assertTrue(options.stream().noneMatch(o -> o.tag() == MoodTag.UNKNOWN));
		assertTrue(options.stream().anyMatch(o -> o.tag() == MoodTag.JOY && "Radość".equals(o.polishLabel())));
		assertTrue(options.stream().anyMatch(o -> o.tag() == MoodTag.CALM && "Spokój".equals(o.polishLabel())));
	}

	private UserAccount createUserAccount(String email) {
		return createUserAccount(email, "Europe/Warsaw");
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

	private JournalEntry createJournalEntry(
		UserAccount owner,
		String content,
		MoodTag moodTag,
		int moodScore,
		Instant createdAt
	) {
		return createJournalEntry(owner, content, moodTag, moodScore, null, null, createdAt);
	}

	private JournalEntry createJournalEntry(
		UserAccount owner,
		String content,
		MoodTag moodTag,
		int moodScore,
		MoodTag overrideMoodTag,
		Integer overrideMoodScore,
		Instant createdAt
	) {
		return new JournalEntry(
			UUID.randomUUID(),
			owner,
			content,
			moodTag,
			moodScore,
			overrideMoodTag,
			overrideMoodScore,
			"stub",
			"stub-v1",
			createdAt.plusSeconds(5),
			createdAt,
			createdAt
		);
	}

}
