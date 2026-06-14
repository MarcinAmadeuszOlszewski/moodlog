package com.amadeuszx.moodlog.journal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodClassification;
import com.amadeuszx.moodlog.classification.MoodClassificationFailedException;
import com.amadeuszx.moodlog.classification.MoodClassificationFailureReason;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class JournalFlowTests {

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@MockitoBean
	private MoodClassifier moodClassifier;

	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@BeforeEach
	void setUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(SecurityMockMvcConfigurers.springSecurity())
			.build();
	}

	@Test
	@DisplayName("allows an authenticated user to save an entry and see it in the recent list")
	void authenticatedUserCanSaveAnEntryAndSeeItInTheRecentList() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val classification = new MoodClassification(
			MoodTag.CALM,
			74,
			"stub",
			"stub-v1",
			Instant.parse("2026-05-31T12:00:05Z")
		);

		given(moodClassifier.classify("Po spacerze czuję spokój.")).willReturn(classification);

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", "Po spacerze czuję spokój."))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"));

		assertEquals(1L, journalEntryRepository.count());

		mockMvc.perform(get("/journal").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Po spacerze czuję spokój.")))
			.andExpect(content().string(containsString("Spokój — 74/100")));
	}

	@Test
	@DisplayName("keeps blank journal submissions on the form and does not persist them")
	void blankJournalSubmissionStaysOnTheForm() throws Exception {
		val owner = createUserAccount("ela@example.com");

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", "   "))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Wpis nie może być pusty.")));

		assertEquals(0L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("blocks too-long journal submissions with a validation error")
	void tooLongJournalSubmissionShowsValidationError() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val tooLongContent = "x".repeat(2001);

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", tooLongContent))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Wpis może mieć maksymalnie 2000 znaków.")));

		assertEquals(0L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("saves entry with unknown mood and redirects when classification fails")
	void savesEntryWithUnknownMoodAndRedirectsWhenClassificationFails() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val entryText = "Mam dziś za dużo myśli na raz.";

		given(moodClassifier.classify(entryText))
			.willThrow(new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu."));

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", entryText))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"));

		assertEquals(1L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("does not expose journal text in logs when classification fails")
	void classificationFailureDoesNotExposeJournalTextInLogs(CapturedOutput output) throws Exception {
		val owner = createUserAccount("ela@example.com");
		val entryText = "To jest prywatny wpis, który nie może trafić do logów.";

		given(moodClassifier.classify(entryText))
			.willThrow(new MoodClassificationFailedException(
				"Nie udało się sklasyfikować wpisu.",
				MoodClassificationFailureReason.PROVIDER_ERROR,
				"openai",
				"gpt-4o-mini",
				new IllegalStateException("provider raw output: " + entryText)
			));

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", entryText))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"));

		assertTrue(output.getOut().contains("journal.classification.failure identifier=email-hash:"));
		assertTrue(output.getOut().contains("provider=openai"));
		assertTrue(output.getOut().contains("model=gpt-4o-mini"));
		assertTrue(output.getOut().contains("reason=PROVIDER_ERROR"));
		assertFalse(output.getOut().contains(entryText));
	}

	@Test
	@DisplayName("shows unknown mood entry in recent list after classification failure")
	void showsUnknownMoodEntryInRecentListAfterClassificationFailure() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val entryText = "Po prostu czuję chaos dzisiaj.";

		given(moodClassifier.classify(entryText))
			.willThrow(new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu."));

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", entryText))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"));

		val responseContent = mockMvc.perform(get("/journal").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertTrue(responseContent.contains("Po prostu czuję chaos dzisiaj."));
		assertTrue(responseContent.contains("Nieznane"));
		assertFalse(responseContent.contains("/100"));
	}

	@Test
	@DisplayName("shows only the current user's recent entries in newest-first order")
	void currentUserSeesOnlyTheirRecentEntriesNewestFirst() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val otherOwner = createUserAccount("ola@example.com");
		val olderOwnerEntry = createJournalEntry(
			owner,
			"Starszy wpis Eli.",
			MoodTag.SADNESS,
			42,
			Instant.parse("2026-05-31T08:00:00Z")
		);
		val newerOwnerEntry = createJournalEntry(
			owner,
			"Najnowszy wpis Eli.",
			MoodTag.JOY,
			86,
			Instant.parse("2026-05-31T18:00:00Z")
		);
		val otherOwnerEntry = createJournalEntry(
			otherOwner,
			"Wpis Oli powinien pozostać ukryty.",
			MoodTag.CALM,
			71,
			Instant.parse("2026-05-31T20:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(olderOwnerEntry, newerOwnerEntry, otherOwnerEntry));

		val responseContent = mockMvc.perform(get("/journal").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertTrue(responseContent.contains("Najnowszy wpis Eli."));
		assertTrue(responseContent.contains("Starszy wpis Eli."));
		assertFalse(responseContent.contains("Wpis Oli powinien pozostać ukryty."));
		assertTrue(responseContent.indexOf("Najnowszy wpis Eli.") < responseContent.indexOf("Starszy wpis Eli."));
	}

	@Test
	@DisplayName("shows only the latest ten entries on journal page and links to archive surfaces")
	void journalPageShowsOnlyTheLatestTenEntriesAndLinksToArchiveSurfaces() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val ownerEntries = createJournalEntries(
			owner,
			"Eli wpis",
			11,
			Instant.parse("2026-05-01T08:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(ownerEntries);

		val responseContent = mockMvc.perform(get("/journal").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("/journal/history")))
			.andExpect(content().string(containsString("/journal/trends")))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertTrue(responseContent.contains("Eli wpis [11]"));
		assertTrue(responseContent.contains("Eli wpis [02]"));
		assertFalse(responseContent.contains("Eli wpis [01]"));
		assertTrue(responseContent.indexOf("Eli wpis [11]") < responseContent.indexOf("Eli wpis [10]"));
	}

	@Test
	@DisplayName("shows the history archive newest first with paging and owner-only visibility")
	void historyArchiveShowsNewestFirstWithPagingAndOwnerOnlyVisibility() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val otherOwner = createUserAccount("ola@example.com");
		val ownerEntries = createJournalEntries(
			owner,
			"Eli archiwum",
			21,
			Instant.parse("2026-05-01T08:00:00Z")
		);
		val allEntries = new ArrayList<>(ownerEntries);
		allEntries.add(createJournalEntry(
			otherOwner,
			"Ola archiwum [01]",
			MoodTag.CALM,
			71,
			Instant.parse("2026-06-01T10:00:00Z")
		));

		journalEntryRepository.saveAllAndFlush(allEntries);

		val firstPageContent = mockMvc.perform(get("/journal/history")
				.param("page", "0")
				.with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-history"))
			.andExpect(content().string(containsString("/journal/trends")))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertTrue(firstPageContent.contains("Eli archiwum [21]"));
		assertTrue(firstPageContent.contains("Eli archiwum [02]"));
		assertFalse(firstPageContent.contains("Eli archiwum [01]"));
		assertFalse(firstPageContent.contains("Ola archiwum [01]"));
		assertTrue(firstPageContent.indexOf("Eli archiwum [21]") < firstPageContent.indexOf("Eli archiwum [20]"));
		assertTrue(firstPageContent.contains("/journal/history?page=1"));

		val secondPageContent = mockMvc.perform(get("/journal/history")
				.param("page", "1")
				.with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-history"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertTrue(secondPageContent.contains("Eli archiwum [01]"));
		assertFalse(secondPageContent.contains("Eli archiwum [02]"));
		assertFalse(secondPageContent.contains("Ola archiwum [01]"));
		assertTrue(secondPageContent.contains("/journal/history?page=0"));
	}

	@Test
	@DisplayName("shows an empty history state when the user has no saved entries")
	void emptyHistoryStateShowsHelpfulCopy() throws Exception {
		val owner = createUserAccount("ela@example.com");

		mockMvc.perform(get("/journal/history").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-history"))
			.andExpect(content().string(containsString("Nie masz jeszcze zapisanych wpisów do przeglądania.")));
	}

	@Test
	@DisplayName("falls back to the last history page when the requested page is out of range")
	void outOfRangeHistoryPageFallsBackToTheLastAvailablePage() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val ownerEntries = createJournalEntries(
			owner,
			"Eli archiwum",
			21,
			Instant.parse("2026-05-01T08:00:00Z")
		);

		journalEntryRepository.saveAllAndFlush(ownerEntries);

		val responseContent = mockMvc.perform(get("/journal/history")
				.param("page", "999")
				.with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-history"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertTrue(responseContent.contains("Eli archiwum [01]"));
		assertFalse(responseContent.contains("Eli archiwum [02]"));
		assertTrue(responseContent.contains("Strona 2 z 2"));
		assertFalse(responseContent.contains("Strona 1000 z 2"));
	}

	private UserAccount createUserAccount(String email) {
		val createdAt = Instant.now();
		val userAccount = new UserAccount(
			UUID.randomUUID(),
			email,
			"$2a$10$storedHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
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

	private List<JournalEntry> createJournalEntries(
		UserAccount owner,
		String contentPrefix,
		int count,
		Instant firstCreatedAt
	) {
		val entries = new ArrayList<JournalEntry>();

		for (int index = 1; index <= count; index++) {
			val entryLabel = String.format("%s [%02d]", contentPrefix, index);
			val createdAt = firstCreatedAt.plusSeconds(index * 60L);
			entries.add(createJournalEntry(owner, entryLabel, MoodTag.CALM, 60 + index, createdAt));
		}

		return entries;
	}
}
