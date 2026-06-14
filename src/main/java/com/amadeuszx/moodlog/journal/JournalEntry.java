package com.amadeuszx.moodlog.journal;

import java.time.Instant;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodTag;
import com.amadeuszx.moodlog.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {

	public static final int MAX_CONTENT_LENGTH = 2000;

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_account_id", nullable = false)
	private UserAccount userAccount;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "system_mood_tag", nullable = false, length = 32)
	private MoodTag systemMoodTag;

	@Column(name = "system_mood_score")
	private Integer systemMoodScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "override_mood_tag", length = 32)
	private MoodTag overrideMoodTag;

	@Column(name = "override_mood_score")
	private Integer overrideMoodScore;

	@Column(name = "classifier_provider", nullable = false, length = 100)
	private String classifierProvider;

	@Column(name = "classifier_model", nullable = false, length = 100)
	private String classifierModel;

	@Column(name = "classified_at", nullable = false)
	private Instant classifiedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public JournalEntry(
		UUID id,
		UserAccount userAccount,
		String content,
		MoodTag systemMoodTag,
		Integer systemMoodScore,
		MoodTag overrideMoodTag,
		Integer overrideMoodScore,
		String classifierProvider,
		String classifierModel,
		Instant classifiedAt,
		Instant createdAt,
		Instant updatedAt
	) {
		this.id = id;
		this.userAccount = userAccount;
		this.content = content;
		this.systemMoodTag = systemMoodTag;
		this.systemMoodScore = systemMoodScore;
		this.overrideMoodTag = overrideMoodTag;
		this.overrideMoodScore = overrideMoodScore;
		this.classifierProvider = classifierProvider;
		this.classifierModel = classifierModel;
		this.classifiedAt = classifiedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}
