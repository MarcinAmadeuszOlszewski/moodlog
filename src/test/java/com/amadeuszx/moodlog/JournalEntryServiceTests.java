package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@SpringBootTest
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
	@DisplayName("blocks persistence when the classifier fails")
	void blocksPersistenceWhenTheClassifierFails() {
		val owner = createUserAccount("ela@example.com");

		given(moodClassifier.classify("Nie mogę się uspokoić."))
			.willThrow(new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu."));

		assertThrows(
			MoodClassificationFailedException.class,
			() -> journalEntryService.saveEntry(owner.getEmail(), "Nie mogę się uspokoić.")
		);
		assertEquals(0L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("rejects invalid classification payloads before persistence")
	void rejectsInvalidClassificationPayloadsBeforePersistence() {
		val owner = createUserAccount("ela@example.com");

		given(moodClassifier.classify("Czuję chaos."))
			.willThrow(new IllegalArgumentException("Mood score must be between 0 and 100."));

		assertThrows(
			MoodClassificationFailedException.class,
			() -> journalEntryService.saveEntry(owner.getEmail(), "Czuję chaos.")
		);
		assertEquals(0L, journalEntryRepository.count());
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

	private UserAccount createUserAccount(String email) {
		val createdAt = Instant.now();
		val userAccount = new UserAccount(
			UUID.randomUUID(),
			email,
			"$2a$10$storedHash",
			true,
			createdAt,
			createdAt
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
		return new JournalEntry(
			UUID.randomUUID(),
			owner,
			content,
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
