package com.amadeuszx.moodlog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
class AuthenticationFlowTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private UserAccountService userAccountService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		userAccountRepository.deleteAll();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build();
	}

	@Test
	@DisplayName("renders the public login page")
	void loginPageRendersForAnonymousUsers() throws Exception {
		mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(view().name("login"))
			.andExpect(content().string(containsString("Zaloguj się do MoodLog")));
	}

	@Test
	@DisplayName("renders the public registration page")
	void registrationPageRendersForAnonymousUsers() throws Exception {
		mockMvc.perform(get("/register"))
			.andExpect(status().isOk())
			.andExpect(view().name("register"))
			.andExpect(content().string(containsString("Załóż konto w MoodLog")));
	}

	@Test
	@DisplayName("registers a new user and redirects into the private journal")
	void successfulRegistrationAuthenticatesAndRedirectsToJournal() throws Exception {
		final MvcResult registrationResult = mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"))
			.andReturn();
		final MockHttpSession session = (MockHttpSession) registrationResult.getRequest().getSession(false);

		assertNotNull(session);
		assertEquals(1L, userAccountRepository.count());

		mockMvc.perform(get("/journal").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("ela@example.com")));
	}

	@Test
	@DisplayName("rejects duplicate registrations on the public signup form")
	void duplicateEmailRegistrationShowsValidationMessage() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().isOk())
			.andExpect(view().name("register"))
			.andExpect(content().string(containsString("Konto z tym adresem e-mail już istnieje.")));
	}

	@Test
	@DisplayName("redirects failed logins back to the login page with a generic error")
	void failedLoginRedirectsWithGenericError() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "zle-haslo"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?error"));

		mockMvc.perform(get("/login").param("error", ""))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Nie udało się zalogować. Sprawdź adres e-mail i hasło.")));
	}

	@Test
	@DisplayName("logs in an existing user and sends them to the private journal")
	void existingUserCanLogIn() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"));
	}

	@Test
	@DisplayName("returns an anonymous journal request to the journal shell after login")
	void anonymousJournalRequestReturnsToJournalAfterLogin() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		final MvcResult anonymousJournalResult = mockMvc.perform(get("/journal"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"))
			.andReturn();
		final MockHttpSession anonymousSession = (MockHttpSession) anonymousJournalResult.getRequest().getSession(false);

		assertNotNull(anonymousSession);

		final MvcResult loginResult = mockMvc.perform(post("/login")
				.with(csrf())
				.session(anonymousSession)
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		final String redirectedUrl = loginResult.getResponse().getRedirectedUrl();
		final MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

		assertNotNull(redirectedUrl);
		assertTrue(redirectedUrl.endsWith("/journal"));
		assertNotNull(authenticatedSession);

		mockMvc.perform(get("/journal").session(authenticatedSession))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Dodaj pierwszy wpis (wkrótce)")))
			.andExpect(content().string(containsString("Tworzenie wpisów odblokujemy w kolejnym kroku.")))
			.andExpect(content().string(containsString("ela@example.com")));
	}

	@Test
	@DisplayName("redirects logout to the login page with confirmation")
	void logoutRedirectsToLoginWithConfirmationFlag() throws Exception {
		mockMvc.perform(post("/logout")
				.with(csrf())
				.with(user("ela@example.com").roles("USER")))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?logout"));

		mockMvc.perform(get("/login").param("logout", ""))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Wylogowano Cię z MoodLog.")));
	}
}
