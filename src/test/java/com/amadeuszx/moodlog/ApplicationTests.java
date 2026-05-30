package com.amadeuszx.moodlog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
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

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
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
	@DisplayName("keeps the public random endpoint accessible")
	void randomEndpointReturnsRandomNumber() throws Exception {
		mockMvc.perform(get("/v1/random"))
			.andExpect(status().isOk())
			.andExpect(content().string(matchesPattern("\\d+")));
	}

	@Test
	@DisplayName("keeps the landing page public and wired to the random endpoint")
	void indexPageContainsWelcomeMessageAndUsesRandomEndpoint() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("index"))
			.andExpect(content().string(containsString("Witaj! Jesteś dziś")))
			.andExpect(content().string(containsString("\\/v1\\/random")))
			.andExpect(content().string(containsString("gościem!")));
	}

	@Test
	@DisplayName("boots the user account schema under test configuration")
	void userAccountSchemaBootstrapsUnderTestConfiguration() {
		assertEquals(0L, userAccountRepository.count());
	}

	@Test
	@DisplayName("redirects anonymous journal requests to login")
	void journalPageRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/journal"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

}
