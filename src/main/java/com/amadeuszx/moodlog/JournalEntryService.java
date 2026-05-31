package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class JournalEntryService {

	private static final Logger logger = LoggerFactory.getLogger(JournalEntryService.class);

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
		final String safeUserIdentifier = UserAccountService.safeEmailIdentifier(userAccount.getEmail());
		final MoodClassification moodClassification = classifyContent(content, safeUserIdentifier);
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

		final JournalEntry savedJournalEntry = journalEntryRepository.save(journalEntry);

		logger.info(
			"journal.classification.success identifier={} provider={} model={}",
			safeUserIdentifier,
			moodClassification.provider(),
			moodClassification.model()
		);

		return savedJournalEntry;
	}

	public List<JournalEntry> getRecentEntries(String currentUserEmail) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final int effectiveRecentListLimit = Math.max(1, recentListLimit);
		final PageRequest pageRequest = PageRequest.of(0, effectiveRecentListLimit);

		return journalEntryRepository.findByUserAccountIdOrderByCreatedAtDesc(userAccount.getId(), pageRequest);
	}

	private MoodClassification classifyContent(String content, String safeUserIdentifier) {
		try {
			return moodClassifier.classify(content);
		}
		catch (MoodClassificationFailedException exception) {
			logClassificationFailure(safeUserIdentifier, exception);
			throw exception;
		}
		catch (IllegalArgumentException exception) {
			final MoodClassificationFailedException moodClassificationFailedException = new MoodClassificationFailedException(
				"Nie udało się sklasyfikować wpisu.",
				MoodClassificationFailureReason.INVALID_RESPONSE,
				null,
				null,
				exception
			);

			logClassificationFailure(safeUserIdentifier, moodClassificationFailedException);
			throw moodClassificationFailedException;
		}
	}

	private UserAccount resolveUserAccount(String currentUserEmail) {
		return userAccountService.findByEmail(currentUserEmail)
			.orElseThrow(() -> new IllegalStateException("Authenticated user account was not found."));
	}

	private void logClassificationFailure(String safeUserIdentifier, MoodClassificationFailedException exception) {
		logger.warn(
			"journal.classification.failure identifier={} provider={} model={} reason={}",
			safeUserIdentifier,
			exception.getProvider(),
			exception.getModel(),
			exception.getReason()
		);
	}
}
