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
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class JournalEntryRepositoryTests {

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@BeforeEach
	void setUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
	}

	@Test
	@DisplayName("persists and reloads a journal entry with system classification fields")
	void journalEntryPersistsWithSystemClassificationFields() {
		val owner = createUserAccount("ela@example.com");
		val createdAt = Instant.parse("2026-05-31T10:15:30Z");
		val journalEntry = createJournalEntry(owner, "Dzisiaj czuję spokój po spacerze.", MoodTag.CALM, 78, createdAt);

		journalEntryRepository.saveAndFlush(journalEntry);

		val loadedEntries = journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(owner.getId());
		val loadedEntry = loadedEntries.getFirst();

		assertEquals(1, loadedEntries.size());
		assertEquals("Dzisiaj czuję spokój po spacerze.", loadedEntry.getContent());
		assertEquals(MoodTag.CALM, loadedEntry.getSystemMoodTag());
		assertEquals(78, loadedEntry.getSystemMoodScore());
		assertNull(loadedEntry.getOverrideMoodTag());
		assertNull(loadedEntry.getOverrideMoodScore());
		assertEquals("stub", loadedEntry.getClassifierProvider());
		assertEquals("stub-v1", loadedEntry.getClassifierModel());
		assertEquals(createdAt.plusSeconds(5), loadedEntry.getClassifiedAt());
	}

	@Test
	@DisplayName("returns the latest entries newest first for one user")
	void recentEntriesReturnNewestFirstForOneUser() {
		val owner = createUserAccount("ela@example.com");
		val firstEntry = createJournalEntry(
			owner,
			"Rano było trudno.",
			MoodTag.SADNESS,
			42,
			Instant.parse("2026-05-31T08:00:00Z")
		);
		val secondEntry = createJournalEntry(
			owner,
			"Po południu odzyskałam spokój.",
			MoodTag.CALM,
			69,
			Instant.parse("2026-05-31T12:00:00Z")
		);
		val thirdEntry = createJournalEntry(
			owner,
			"Wieczorem mam dużo energii.",
			MoodTag.JOY,
			88,
			Instant.parse("2026-05-31T18:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(firstEntry, secondEntry, thirdEntry));

		val recentEntries = journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(owner.getId());

		assertEquals(3, recentEntries.size());
		assertEquals(thirdEntry.getId(), recentEntries.get(0).getId());
		assertEquals(secondEntry.getId(), recentEntries.get(1).getId());
		assertEquals(firstEntry.getId(), recentEntries.get(2).getId());
	}

	@Test
	@DisplayName("keeps recent-entry queries scoped to the owning account")
	void recentEntriesStayScopedToTheOwningAccount() {
		val firstOwner = createUserAccount("ela@example.com");
		val secondOwner = createUserAccount("ola@example.com");
		val firstOwnerEntry = createJournalEntry(
			firstOwner,
			"To jest prywatny wpis Eli.",
			MoodTag.NEUTRAL,
			55,
			Instant.parse("2026-05-31T09:00:00Z")
		);
		val secondOwnerEntry = createJournalEntry(
			secondOwner,
			"To jest prywatny wpis Oli.",
			MoodTag.ANXIETY,
			61,
			Instant.parse("2026-05-31T11:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(firstOwnerEntry, secondOwnerEntry));

		val firstOwnerEntries = journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(firstOwner.getId());
		val secondOwnerEntries = journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(secondOwner.getId());

		assertEquals(1, firstOwnerEntries.size());
		assertEquals(firstOwnerEntry.getId(), firstOwnerEntries.getFirst().getId());
		assertEquals(1, secondOwnerEntries.size());
		assertEquals(secondOwnerEntry.getId(), secondOwnerEntries.getFirst().getId());
	}

	@Test
	@DisplayName("returns paged history newest first for one user")
	void pagedHistoryReturnsNewestFirstForOneUser() {
		val owner = createUserAccount("ela@example.com");
		val oldestEntry = createJournalEntry(
			owner,
			"To był trudny poranek.",
			MoodTag.SADNESS,
			39,
			Instant.parse("2026-05-29T08:00:00Z")
		);
		val middleEntry = createJournalEntry(
			owner,
			"Po rozmowie zrobiło się lżej.",
			MoodTag.CALM,
			67,
			Instant.parse("2026-05-30T12:00:00Z")
		);
		val newestEntry = createJournalEntry(
			owner,
			"Wieczorem wróciła radość.",
			MoodTag.JOY,
			84,
			Instant.parse("2026-05-31T18:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(oldestEntry, middleEntry, newestEntry));

		val historyPage = journalEntryRepository.findAllByUserAccountIdOrderByCreatedAtDesc(
			owner.getId(),
			PageRequest.of(0, 2)
		);

		assertEquals(3L, historyPage.getTotalElements());
		assertEquals(2, historyPage.getContent().size());
		assertEquals(newestEntry.getId(), historyPage.getContent().get(0).getId());
		assertEquals(middleEntry.getId(), historyPage.getContent().get(1).getId());
	}

	@Test
	@DisplayName("keeps paged history scoped to the owning account")
	void pagedHistoryStaysScopedToTheOwningAccount() {
		val firstOwner = createUserAccount("ela@example.com");
		val secondOwner = createUserAccount("ola@example.com");
		val firstOwnerEntry = createJournalEntry(
			firstOwner,
			"To jest archiwalny wpis Eli.",
			MoodTag.CALM,
			70,
			Instant.parse("2026-05-31T09:00:00Z")
		);
		val secondOwnerEntry = createJournalEntry(
			secondOwner,
			"To jest archiwalny wpis Oli.",
			MoodTag.ANXIETY,
			61,
			Instant.parse("2026-05-31T11:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(firstOwnerEntry, secondOwnerEntry));

		val firstOwnerHistory = journalEntryRepository.findAllByUserAccountIdOrderByCreatedAtDesc(
			firstOwner.getId(),
			PageRequest.of(0, 20)
		);

		assertEquals(1L, firstOwnerHistory.getTotalElements());
		assertEquals(firstOwnerEntry.getId(), firstOwnerHistory.getContent().getFirst().getId());
	}

	@Test
	@DisplayName("reads bounded trend windows in ascending order for one owner")
	void boundedTrendWindowReadsStayScopedAndAscending() {
		val owner = createUserAccount("ela@example.com");
		val otherOwner = createUserAccount("ola@example.com");
		val beforeWindowEntry = createJournalEntry(
			owner,
			"Jeszcze przed wybranym oknem.",
			MoodTag.SADNESS,
			35,
			Instant.parse("2026-05-31T07:59:59Z")
		);
		val startBoundaryEntry = createJournalEntry(
			owner,
			"To powinno wejść od granicy startu.",
			MoodTag.NEUTRAL,
			52,
			Instant.parse("2026-05-31T08:00:00Z")
		);
		val inWindowEntry = createJournalEntry(
			owner,
			"To jest wpis ze środka okna.",
			MoodTag.CALM,
			71,
			Instant.parse("2026-05-31T12:00:00Z")
		);
		val endBoundaryEntry = createJournalEntry(
			owner,
			"To już wypada poza granicę końca.",
			MoodTag.JOY,
			88,
			Instant.parse("2026-05-31T18:00:00Z")
		);
		val foreignEntry = createJournalEntry(
			otherOwner,
			"To jest wpis innego użytkownika w środku okna.",
			MoodTag.ANGER,
			63,
			Instant.parse("2026-05-31T10:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(
			List.of(beforeWindowEntry, startBoundaryEntry, inWindowEntry, endBoundaryEntry, foreignEntry)
		);

		val windowEntries = journalEntryRepository
			.findAllByUserAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
				owner.getId(),
				Instant.parse("2026-05-31T08:00:00Z"),
				Instant.parse("2026-05-31T18:00:00Z")
			);

		assertEquals(2, windowEntries.size());
		assertEquals(startBoundaryEntry.getId(), windowEntries.get(0).getId());
		assertEquals(inWindowEntry.getId(), windowEntries.get(1).getId());
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
