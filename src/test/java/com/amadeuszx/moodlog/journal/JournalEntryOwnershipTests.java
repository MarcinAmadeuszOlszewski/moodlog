package com.amadeuszx.moodlog.journal;

import java.time.Instant;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodTag;
import com.amadeuszx.moodlog.user.UserAccount;
import com.amadeuszx.moodlog.user.UserAccountRepository;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class JournalEntryOwnershipTests {

	private static final String USER_A_EMAIL = "user-a@example.com";
	private static final String USER_B_EMAIL = "user-b@example.com";

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;
	private UUID entryId;

	@BeforeEach
	void setUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();

		val now = Instant.now();
		userAccountRepository.saveAndFlush(new UserAccount(
			UUID.randomUUID(),
			USER_A_EMAIL,
			"$2a$10$storedHash",
			true,
			now,
			now,
			"Europe/Warsaw"
		));
		val userB = userAccountRepository.saveAndFlush(new UserAccount(
			UUID.randomUUID(),
			USER_B_EMAIL,
			"$2a$10$storedHash",
			true,
			now,
			now,
			"Europe/Warsaw"
		));

		val entry = journalEntryRepository.saveAndFlush(new JournalEntry(
			UUID.randomUUID(),
			userB,
			"Wpis należy do użytkownika B.",
			MoodTag.CALM,
			70,
			null,
			null,
			"stub",
			"stub-v1",
			now.plusSeconds(5),
			now,
			now
		));
		entryId = entry.getId();
	}

	@Test
	@Disabled("Activate when S-04 edit/delete endpoints ship")
	@DisplayName("DELETE /journal/{id} returns 404 when id belongs to a different authenticated user")
	void deleteJournalEntryReturns404WhenIdBelongsToADifferentAuthenticatedUser() throws Exception {
		mockMvc.perform(delete("/journal/{id}", entryId)
				.with(user(USER_A_EMAIL).roles("USER"))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	@Disabled("Activate when S-04 edit/delete endpoints ship")
	@DisplayName("PATCH /journal/{id}/mood returns 404 when id belongs to a different authenticated user")
	void patchJournalEntryMoodReturns404WhenIdBelongsToADifferentAuthenticatedUser() throws Exception {
		mockMvc.perform(patch("/journal/{id}/mood", entryId)
				.with(user(USER_A_EMAIL).roles("USER"))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	@Disabled("Activate when S-04 edit/delete endpoints ship")
	@DisplayName("DELETE /journal/{id} succeeds when called by the entry owner")
	void deleteJournalEntrySucceedsWhenCalledByTheEntryOwner() throws Exception {
		mockMvc.perform(delete("/journal/{id}", entryId)
				.with(user(USER_B_EMAIL).roles("USER"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection());
	}

	@Test
	@Disabled("Activate when S-04 edit/delete endpoints ship")
	@DisplayName("PATCH /journal/{id}/mood succeeds when called by the entry owner")
	void patchJournalEntryMoodSucceedsWhenCalledByTheEntryOwner() throws Exception {
		mockMvc.perform(patch("/journal/{id}/mood", entryId)
				.with(user(USER_B_EMAIL).roles("USER"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection());
	}
}
