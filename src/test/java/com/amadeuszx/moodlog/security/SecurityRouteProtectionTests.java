package com.amadeuszx.moodlog.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityRouteProtectionTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();
	}

	@Test
	@DisplayName("Anonymous GET /journal redirects to /login")
	void anonymousJournalRequestRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/journal"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("Anonymous GET /journal/history redirects to /login")
	void anonymousJournalHistoryRequestRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/journal/history"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("Anonymous GET /journal/trends redirects to /login")
	void anonymousJournalTrendsRequestRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/journal/trends"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("Anonymous POST /journal redirects to /login (CSRF supplied to reach auth filter)")
	void anonymousJournalPostRedirectsToLogin() throws Exception {
		mockMvc.perform(post("/journal").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("Anonymous GET on unmapped /journal path redirects to /login (deny-all catch-all)")
	void anonymousUnmappedJournalPathRedirectsToLogin() throws Exception {
		mockMvc.perform(get("/journal/nonexistent-route"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}
}
