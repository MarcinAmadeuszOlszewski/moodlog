package com.amadeuszx.moodlog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

	List<JournalEntry> findTop10ByUserAccountIdOrderByCreatedAtDesc(UUID userAccountId);
}
