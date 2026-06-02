package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

	interface JournalTrendEntryProjection {

		Instant getCreatedAt();

		MoodTag getSystemMoodTag();

		int getSystemMoodScore();

		MoodTag getOverrideMoodTag();

		Integer getOverrideMoodScore();
	}

	List<JournalEntry> findTop10ByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId);

	List<JournalEntry> findByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);

	Page<JournalEntry> findAllByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);

	List<JournalTrendEntryProjection> findTrendEntriesByUserAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
		UUID userAccountId,
		Instant createdAtFromInclusive,
		Instant createdAtToExclusive
	);
}
