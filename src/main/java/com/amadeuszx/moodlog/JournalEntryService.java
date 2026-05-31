package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class JournalEntryService {

	private final JournalEntryRepository journalEntryRepository;
	private final MoodClassifier moodClassifier;
	private final UserAccountService userAccountService;
	private final int recentListLimit;

	public JournalEntryService(
		JournalEntryRepository journalEntryRepository,
		MoodClassifier moodClassifier,
		UserAccountService userAccountService,
		@Value("${moodlog.journal.recent-list-limit:10}") int recentListLimit
	) {
		this.journalEntryRepository = journalEntryRepository;
		this.moodClassifier = moodClassifier;
		this.userAccountService = userAccountService;
		this.recentListLimit = recentListLimit;
	}

	public JournalEntry saveEntry(String currentUserEmail, String content) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final MoodClassification moodClassification = classifyContent(content);
		final Instant now = Instant.now();
		final JournalEntry journalEntry = new JournalEntry(
			UUID.randomUUID(),
			userAccount,
			content,
			moodClassification.moodTag(),
			moodClassification.moodScore(),
			null,
			null,
			moodClassification.provider(),
			moodClassification.model(),
			moodClassification.classifiedAt(),
			now,
			now
		);

		return journalEntryRepository.save(journalEntry);
	}

	public List<JournalEntry> getRecentEntries(String currentUserEmail) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final int effectiveRecentListLimit = Math.max(1, recentListLimit);
		final PageRequest pageRequest = PageRequest.of(0, effectiveRecentListLimit);

		return journalEntryRepository.findByUserAccountIdOrderByCreatedAtDesc(userAccount.getId(), pageRequest);
	}

	private MoodClassification classifyContent(String content) {
		try {
			return moodClassifier.classify(content);
		}
		catch (IllegalArgumentException exception) {
			throw new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu.", exception);
		}
	}

	private UserAccount resolveUserAccount(String currentUserEmail) {
		return userAccountService.findByEmail(currentUserEmail)
			.orElseThrow(() -> new IllegalStateException("Authenticated user account was not found."));
	}
}
