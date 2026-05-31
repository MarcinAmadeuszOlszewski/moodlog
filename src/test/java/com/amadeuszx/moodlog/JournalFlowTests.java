package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
	@DisplayName("preserves the submitted text when classification blocks the save")
	void classificationFailurePreservesTheSubmittedText() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val entryText = "Mam dziś za dużo myśli na raz.";

		given(moodClassifier.classify(entryText))
			.willThrow(new MoodClassificationFailedException("Nie udało się sklasyfikować wpisu."));

		mockMvc.perform(post("/journal")
				.with(user(owner.getEmail()).roles("USER"))
				.with(csrf())
				.param("content", entryText))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Nie udało się sklasyfikować wpisu.")))
			.andExpect(content().string(containsString(entryText)));

		assertEquals(0L, journalEntryRepository.count());
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
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Nie udało się sklasyfikować wpisu.")));

		assertTrue(output.getOut().contains("journal.classification.failure identifier=email-hash:"));
		assertTrue(output.getOut().contains("provider=openai"));
		assertTrue(output.getOut().contains("model=gpt-4o-mini"));
		assertTrue(output.getOut().contains("reason=PROVIDER_ERROR"));
		assertFalse(output.getOut().contains(entryText));
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
