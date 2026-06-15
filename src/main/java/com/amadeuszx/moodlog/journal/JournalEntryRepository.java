package com.amadeuszx.moodlog.journal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

	interface JournalTrendEntryProjection {

		Instant getCreatedAt();

		MoodTag getSystemMoodTag();

		Integer getSystemMoodScore();

		MoodTag getOverrideMoodTag();

		Integer getOverrideMoodScore();
	}

	Optional<JournalEntry> findByIdAndUserAccountId(UUID id, UUID userAccountId);

	List<JournalEntry> findTop10ByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId);

	List<JournalEntry> findByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);

	Page<JournalEntry> findAllByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId, Pageable pageable);

	List<JournalTrendEntryProjection> findTrendEntriesByUserAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
		UUID userAccountId,
		Instant createdAtFromInclusive,
		Instant createdAtToInclusive
	);
}
