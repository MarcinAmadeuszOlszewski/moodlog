package com.amadeuszx.moodlog;

import com.amadeuszx.moodlog.classification.MoodClassifier;
import com.amadeuszx.moodlog.classification.StubMoodClassifier;
import com.amadeuszx.moodlog.journal.JournalEntryRepository;
import com.amadeuszx.moodlog.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
class ApplicationTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@Autowired
	private MoodClassifier moodClassifier;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();
	}

	@Test
	@DisplayName("loads the application context with auth foundation enabled")
	void contextLoads() {
	}

	@Test
	@DisplayName("serves the landing page publicly with app description and no random endpoint")
	void indexPageContainsAppDescriptionAndNoRandomEndpoint() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("index"))
			.andExpect(content().string(containsString("prywatnego dziennika")))
			.andExpect(content().string(containsString("Zaloguj się")))
			.andExpect(content().string(containsString("załóż konto")))
			.andExpect(content().string(containsString("/favicon.svg")));
	}

	@Test
	@DisplayName("serves the public favicon without routing through the error page")
	void faviconRequestReturnsPublicAsset() throws Exception {
		mockMvc.perform(get("/favicon.ico"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<svg")));
	}

	@Test
	@DisplayName("boots the user account schema under test configuration")
	void userAccountSchemaBootstrapsUnderTestConfiguration() {
		assertEquals(0L, userAccountRepository.count());
	}

	@Test
	@DisplayName("boots the journal entry schema under test configuration")
	void journalEntrySchemaBootstrapsUnderTestConfiguration() {
		assertEquals(0L, journalEntryRepository.count());
	}

	@Test
	@DisplayName("wires the stub classifier under test configuration")
	void testConfigurationUsesStubMoodClassifier() {
		assertTrue(moodClassifier instanceof StubMoodClassifier);
	}

	@Test
	@DisplayName("redirects anonymous journal requests to login")
	void journalPageRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/journal"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("redirects anonymous journal history requests to login")
	void journalHistoryPageRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/journal/history"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("redirects anonymous journal trends requests to login")
	void journalTrendsPageRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/journal/trends"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

}
