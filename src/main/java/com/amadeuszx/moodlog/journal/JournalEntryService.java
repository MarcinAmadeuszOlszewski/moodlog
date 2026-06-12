package com.amadeuszx.moodlog.journal;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.amadeuszx.moodlog.classification.MoodClassification;
import com.amadeuszx.moodlog.classification.MoodClassificationFailedException;
import com.amadeuszx.moodlog.classification.MoodClassificationFailureReason;
import com.amadeuszx.moodlog.classification.MoodClassifier;
import com.amadeuszx.moodlog.journal.history.JournalEntryListItem;
import com.amadeuszx.moodlog.journal.history.JournalHistoryItem;
import com.amadeuszx.moodlog.journal.trend.JournalTrendView;
import com.amadeuszx.moodlog.classification.MoodTag;
import com.amadeuszx.moodlog.user.UserAccount;
import com.amadeuszx.moodlog.user.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalEntryService {

	private static final Logger logger = LoggerFactory.getLogger(JournalEntryService.class);
	private static final int ENTRY_EXCERPT_LENGTH = 120;
	private static final DayOfWeek REPORTING_WEEK_START = DayOfWeek.MONDAY;
	private static final DateTimeFormatter DAILY_CHART_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd.MM");

	private final JournalEntryRepository journalEntryRepository;
	private final MoodClassifier moodClassifier;
	private final UserAccountService userAccountService;
	private final Clock clock;
	private final int recentListLimit;
	private final int historyPageSize;
	private final ZoneId reportingZoneId;
	private final int weeklyTrendSpan;

	public JournalEntryService(
		JournalEntryRepository journalEntryRepository,
		MoodClassifier moodClassifier,
		UserAccountService userAccountService,
		Clock clock,
		@Value("${moodlog.journal.recent-list-limit:10}") int recentListLimit,
		@Value("${moodlog.journal.history-page-size:20}") int historyPageSize,
		@Value("${moodlog.journal.reporting-zone-id:Europe/Warsaw}") String reportingZoneId,
		@Value("${moodlog.journal.weekly-trend-span:8}") int weeklyTrendSpan
	) {
		this.journalEntryRepository = journalEntryRepository;
		this.moodClassifier = moodClassifier;
		this.userAccountService = userAccountService;
		this.clock = clock;
		this.recentListLimit = Math.max(1, recentListLimit);
		this.historyPageSize = Math.max(1, historyPageSize);
		this.reportingZoneId = ZoneId.of(reportingZoneId);
		this.weeklyTrendSpan = Math.max(1, weeklyTrendSpan);
	}

	@Transactional
	public JournalEntry saveEntry(String currentUserEmail, String content) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final String safeUserIdentifier = UserAccountService.safeEmailIdentifier(userAccount.getEmail());
		MoodClassification moodClassification;
		try {
			moodClassification = classifyContent(content, safeUserIdentifier);
		}
		catch (MoodClassificationFailedException exception) {
			return saveEntryWithUnknownMood(userAccount, content, safeUserIdentifier, exception);
		}
		final Instant now = Instant.now(clock);
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

	private JournalEntry saveEntryWithUnknownMood(
		UserAccount userAccount,
		String content,
		String safeUserIdentifier,
		MoodClassificationFailedException exception
	) {
		final Instant now = Instant.now(clock);
		final String provider = exception.getProvider();
		final String model = exception.getModel();
		final JournalEntry journalEntry = new JournalEntry(
			UUID.randomUUID(),
			userAccount,
			content,
			MoodTag.UNKNOWN,
			null,
			null,
			null,
			provider,
			model,
			now,
			now,
			now
		);

		final JournalEntry savedJournalEntry = journalEntryRepository.save(journalEntry);

		logger.warn(
			"journal.entry.saved.with.unknown.mood identifier={} provider={} model={}",
			safeUserIdentifier,
			provider,
			model
		);

		return savedJournalEntry;
	}

	public List<JournalEntry> getRecentEntries(String currentUserEmail) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final PageRequest pageRequest = PageRequest.of(0, recentListLimit);

		return journalEntryRepository.findByUserAccountIdOrderByCreatedAtDesc(userAccount.getId(), pageRequest);
	}

	public List<JournalEntryListItem> getRecentEntryListItems(String currentUserEmail) {
		return getRecentEntries(currentUserEmail).stream()
			.map(this::toListItem)
			.toList();
	}

	public Page<JournalHistoryItem> getHistoryEntries(String currentUserEmail, int pageNumber) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final int safePageNumber = Math.max(0, pageNumber);
		final PageRequest pageRequest = PageRequest.of(safePageNumber, historyPageSize);

		return journalEntryRepository.findAllByUserAccountIdOrderByCreatedAtDesc(userAccount.getId(), pageRequest)
			.map(this::toHistoryItem);
	}

	public JournalTrendView getTrendView(String currentUserEmail) {
		final UserAccount userAccount = resolveUserAccount(currentUserEmail);
		final Instant now = Instant.now(clock);
		final LocalDate currentDate = now.atZone(reportingZoneId).toLocalDate();
		final LocalDate currentWeekStart = currentDate.with(TemporalAdjusters.previousOrSame(REPORTING_WEEK_START));
		final LocalDate completedSevenDayStart = currentDate.minusDays(7);
		final LocalDate completedThirtyDayStart = currentDate.minusDays(30);
		final LocalDate firstCompletedWeekStart = currentWeekStart.minusWeeks(weeklyTrendSpan);
		final LocalDate firstReportingDate = earliestReportingDate(
			completedThirtyDayStart,
			firstCompletedWeekStart,
			currentWeekStart
		);
		final Instant windowStartInclusive = firstReportingDate.atStartOfDay(reportingZoneId).toInstant();
		final Instant windowEndExclusive = now.plusNanos(1);
		final List<ReportedJournalEntry> reportedEntries = journalEntryRepository
			.findTrendEntriesByUserAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
				userAccount.getId(),
				windowStartInclusive,
				windowEndExclusive
			)
			.stream()
			.map(this::toReportedEntry)
			.filter(Objects::nonNull)
			.toList();
		final JournalTrendView.CurrentWeekSummary currentWeekSummary = buildCurrentWeekSummary(
			reportedEntries,
			currentWeekStart
		);
		final JournalTrendView.DailyTrendSeries completedSevenDayTrend = buildDailyTrendSeries(
			reportedEntries,
			completedSevenDayStart,
			7
		);
		final JournalTrendView.DailyTrendSeries completedThirtyDayTrend = buildDailyTrendSeries(
			reportedEntries,
			completedThirtyDayStart,
			30
		);
		final JournalTrendView.WeeklyTrendSeries completedWeeklyTrend = buildWeeklyTrendSeries(
			reportedEntries,
			firstCompletedWeekStart,
			weeklyTrendSpan
		);
		final boolean empty = currentWeekSummary.empty()
			&& completedSevenDayTrend.empty()
			&& completedThirtyDayTrend.empty()
			&& completedWeeklyTrend.empty();

		return new JournalTrendView(
			currentWeekSummary,
			completedSevenDayTrend,
			completedThirtyDayTrend,
			completedWeeklyTrend,
			empty
		);
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

	private JournalHistoryItem toHistoryItem(JournalEntry journalEntry) {
		final EffectiveMood effectiveMood = resolveEffectiveMood(journalEntry);
		final LocalDate displayDate = journalEntry.getCreatedAt().atZone(reportingZoneId).toLocalDate();

		return new JournalHistoryItem(
			displayDate,
			journalEntry.getCreatedAt().atZone(reportingZoneId).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
			buildExcerpt(journalEntry.getContent()),
			polishMoodLabel(effectiveMood.moodTag()),
			effectiveMood.moodScore()
		);
	}

	private JournalEntryListItem toListItem(JournalEntry journalEntry) {
		final EffectiveMood effectiveMood = resolveEffectiveMood(journalEntry);

		return new JournalEntryListItem(
			buildExcerpt(journalEntry.getContent()),
			polishMoodLabel(effectiveMood.moodTag()),
			effectiveMood.moodScore()
		);
	}

	private JournalTrendView.CurrentWeekSummary buildCurrentWeekSummary(
		List<ReportedJournalEntry> reportedEntries,
		LocalDate currentWeekStart
	) {
		final List<ReportedJournalEntry> currentWeekEntries = reportedEntries.stream()
			.filter(reportedEntry -> !reportedEntry.reportingDate().isBefore(currentWeekStart))
			.toList();

		if (currentWeekEntries.isEmpty()) {
			return new JournalTrendView.CurrentWeekSummary(0, 0, null, null, true);
		}

		final int daysWithEntries = (int) currentWeekEntries.stream()
			.map(ReportedJournalEntry::reportingDate)
			.distinct()
			.count();
		final MoodTag dominantMoodTag = determineDominantMood(currentWeekEntries);

		return new JournalTrendView.CurrentWeekSummary(
			currentWeekEntries.size(),
			daysWithEntries,
			polishMoodLabel(dominantMoodTag),
			averageMoodScore(currentWeekEntries),
			false
		);
	}

	private JournalTrendView.DailyTrendSeries buildDailyTrendSeries(
		List<ReportedJournalEntry> reportedEntries,
		LocalDate startDate,
		int dayCount
	) {
		final Map<LocalDate, List<ReportedJournalEntry>> entriesByDate = reportedEntries.stream()
			.filter(reportedEntry -> !reportedEntry.reportingDate().isBefore(startDate))
			.filter(reportedEntry -> reportedEntry.reportingDate().isBefore(startDate.plusDays(dayCount)))
			.collect(Collectors.groupingBy(ReportedJournalEntry::reportingDate));
		final List<JournalTrendView.DailyTrendPoint> points = new ArrayList<>();
		final List<String> chartLabels = new ArrayList<>();
		final List<Integer> chartValues = new ArrayList<>();

		for (int offset = 0; offset < dayCount; offset++) {
			final LocalDate reportingDate = startDate.plusDays(offset);
			final List<ReportedJournalEntry> entriesForDate = entriesByDate.getOrDefault(reportingDate, List.of());
			final Integer averageMoodScore = entriesForDate.isEmpty() ? null : averageMoodScore(entriesForDate);
			final String label = formatDailyLabel(reportingDate);

			points.add(new JournalTrendView.DailyTrendPoint(
				reportingDate,
				label,
				averageMoodScore,
				entriesForDate.size()
			));
			chartLabels.add(label);
			chartValues.add(averageMoodScore);
		}

		return new JournalTrendView.DailyTrendSeries(
			points,
			chartLabels,
			chartValues,
			chartValues.stream().allMatch(Objects::isNull),
			chartValues.stream().anyMatch(Objects::isNull) && chartValues.stream().anyMatch(Objects::nonNull)
		);
	}

	private JournalTrendView.WeeklyTrendSeries buildWeeklyTrendSeries(
		List<ReportedJournalEntry> reportedEntries,
		LocalDate firstCompletedWeekStart,
		int weekCount
	) {
		final Map<LocalDate, List<ReportedJournalEntry>> entriesByWeekStart = reportedEntries.stream()
			.filter(reportedEntry -> !reportedEntry.reportingWeekStart().isBefore(firstCompletedWeekStart))
			.filter(reportedEntry -> reportedEntry.reportingWeekStart().isBefore(firstCompletedWeekStart.plusWeeks(weekCount)))
			.collect(Collectors.groupingBy(ReportedJournalEntry::reportingWeekStart));
		final List<JournalTrendView.WeeklyTrendPoint> points = new ArrayList<>();
		final List<String> chartLabels = new ArrayList<>();
		final List<Integer> chartValues = new ArrayList<>();

		for (int offset = 0; offset < weekCount; offset++) {
			final LocalDate weekStartDate = firstCompletedWeekStart.plusWeeks(offset);
			final LocalDate weekEndDate = weekStartDate.plusDays(6);
			final List<ReportedJournalEntry> entriesForWeek = entriesByWeekStart.getOrDefault(weekStartDate, List.of());
			final Integer averageMoodScore = entriesForWeek.isEmpty() ? null : averageMoodScore(entriesForWeek);
			final String label = formatWeeklyLabel(weekStartDate, weekEndDate);

			points.add(new JournalTrendView.WeeklyTrendPoint(
				weekStartDate,
				weekEndDate,
				label,
				averageMoodScore,
				entriesForWeek.size()
			));
			chartLabels.add(label);
			chartValues.add(averageMoodScore);
		}

		return new JournalTrendView.WeeklyTrendSeries(
			points,
			chartLabels,
			chartValues,
			chartValues.stream().allMatch(Objects::isNull),
			chartValues.stream().anyMatch(Objects::isNull) && chartValues.stream().anyMatch(Objects::nonNull)
		);
	}

	private MoodTag determineDominantMood(List<ReportedJournalEntry> reportedEntries) {
		return reportedEntries.stream()
			.collect(Collectors.groupingBy(ReportedJournalEntry::moodTag))
			.entrySet()
			.stream()
			.max(
				Comparator.<Map.Entry<MoodTag, List<ReportedJournalEntry>>>comparingInt(entry -> entry.getValue().size())
					.thenComparingInt(entry -> averageMoodScore(entry.getValue()))
					.thenComparing(entry -> entry.getKey().name())
			)
			.orElseThrow(() -> new IllegalStateException("Expected at least one mood for dominant-mood calculation."))
			.getKey();
	}

	private int averageMoodScore(List<ReportedJournalEntry> reportedEntries) {
		final double averageMoodScore = reportedEntries.stream()
			.mapToInt(ReportedJournalEntry::moodScore)
			.average()
			.orElseThrow(() -> new IllegalStateException("Expected at least one mood score for aggregation."));

		return (int) Math.round(averageMoodScore);
	}

	private ReportedJournalEntry toReportedEntry(JournalEntryRepository.JournalTrendEntryProjection journalTrendEntry) {
		final EffectiveMood effectiveMood = resolveEffectiveMood(journalTrendEntry);
		if (effectiveMood.moodScore() == null) {
			return null;
		}
		final LocalDate reportingDate = journalTrendEntry.getCreatedAt().atZone(reportingZoneId).toLocalDate();

		return new ReportedJournalEntry(
			reportingDate,
			reportingDate.with(TemporalAdjusters.previousOrSame(REPORTING_WEEK_START)),
			effectiveMood.moodTag(),
			effectiveMood.moodScore()
		);
	}

	private EffectiveMood resolveEffectiveMood(JournalEntry journalEntry) {
		if (journalEntry.getOverrideMoodTag() != null && journalEntry.getOverrideMoodScore() != null) {
			return new EffectiveMood(journalEntry.getOverrideMoodTag(), journalEntry.getOverrideMoodScore());
		}

		return new EffectiveMood(journalEntry.getSystemMoodTag(), journalEntry.getSystemMoodScore());
	}

	private EffectiveMood resolveEffectiveMood(JournalEntryRepository.JournalTrendEntryProjection journalTrendEntry) {
		if (journalTrendEntry.getOverrideMoodTag() != null && journalTrendEntry.getOverrideMoodScore() != null) {
			return new EffectiveMood(journalTrendEntry.getOverrideMoodTag(), journalTrendEntry.getOverrideMoodScore());
		}

		return new EffectiveMood(journalTrendEntry.getSystemMoodTag(), journalTrendEntry.getSystemMoodScore());
	}

	private LocalDate earliestReportingDate(LocalDate firstDate, LocalDate secondDate, LocalDate thirdDate) {
		return List.of(firstDate, secondDate, thirdDate).stream()
			.min(LocalDate::compareTo)
			.orElseThrow(() -> new IllegalStateException("Expected at least one reporting boundary."));
	}

	private String buildExcerpt(String content) {
		if (content.length() <= ENTRY_EXCERPT_LENGTH) {
			return content;
		}

		return content.substring(0, ENTRY_EXCERPT_LENGTH - 3) + "...";
	}

	private String polishMoodLabel(MoodTag moodTag) {
		return switch (moodTag) {
			case JOY -> "Radość";
			case CALM -> "Spokój";
			case NEUTRAL -> "Neutralnie";
			case SADNESS -> "Smutek";
			case ANXIETY -> "Lęk";
			case ANGER -> "Złość";
			case OVERWHELMED -> "Przytłoczenie";
			case UNKNOWN -> "Nieznane";
		};
	}

	private String formatDailyLabel(LocalDate reportingDate) {
		return reportingDate.format(DAILY_CHART_LABEL_FORMATTER);
	}

	private String formatWeeklyLabel(LocalDate weekStartDate, LocalDate weekEndDate) {
		return weekStartDate.format(DAILY_CHART_LABEL_FORMATTER)
			+ "-"
			+ weekEndDate.format(DAILY_CHART_LABEL_FORMATTER);
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

	private record EffectiveMood(MoodTag moodTag, Integer moodScore) {
	}

	private record ReportedJournalEntry(
		LocalDate reportingDate,
		LocalDate reportingWeekStart,
		MoodTag moodTag,
		int moodScore
	) {
	}
}
