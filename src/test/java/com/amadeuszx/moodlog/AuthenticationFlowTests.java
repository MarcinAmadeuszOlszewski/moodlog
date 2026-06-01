package com.amadeuszx.moodlog;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
@ExtendWith(OutputCaptureExtension.class)
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
		val registrationResult = mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"))
			.andReturn();
		val session = (MockHttpSession) registrationResult.getRequest().getSession(false);

		assertNotNull(session);
		assertEquals(1L, userAccountRepository.count());

		mockMvc.perform(get("/journal").session(session))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("ela@example.com")));
	}

	@Test
	@DisplayName("rotates the anonymous session id during registration")
	void registrationRotatesAnonymousSessionId() throws Exception {
		val anonymousSession = new MockHttpSession();
		val anonymousSessionId = anonymousSession.getId();
		val registrationResult = mockMvc.perform(post("/register")
				.with(csrf())
				.session(anonymousSession)
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"))
			.andReturn();
		val authenticatedSession = (MockHttpSession) registrationResult.getRequest().getSession(false);

		assertNotNull(authenticatedSession);
		assertNotEquals(anonymousSessionId, authenticatedSession.getId());
	}

	@Test
	@DisplayName("rejects duplicate registrations on the public signup form")
	void duplicateEmailRegistrationShowsValidationMessage() throws Exception {
		userAccountService.registerUser("Ela@Example.com", "sekret");

		mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ELA@example.com")
				.param("password", "sekret"))
			.andExpect(status().isOk())
			.andExpect(view().name("register"))
			.andExpect(content().string(containsString("Konto z tym adresem e-mail już istnieje.")));

		assertEquals(1L, userAccountRepository.count());
	}

	@Test
	@DisplayName("rejects too-short passwords on the public signup form")
	void shortPasswordRegistrationShowsValidationMessage() throws Exception {
		mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "12345"))
			.andExpect(status().isOk())
			.andExpect(view().name("register"))
			.andExpect(content().string(containsString("Hasło musi mieć co najmniej 6 znaków.")));

		assertEquals(0L, userAccountRepository.count());
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
	@DisplayName("keeps login failures generic when the account does not exist")
	void failedUnknownUserLoginRedirectsWithGenericError() throws Exception {
		mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", "nie-ma@example.com")
				.param("password", "sekret"))
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

		val anonymousJournalResult = mockMvc.perform(get("/journal"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"))
			.andReturn();
		val anonymousSession = (MockHttpSession) anonymousJournalResult.getRequest().getSession(false);

		assertNotNull(anonymousSession);

		val loginResult = mockMvc.perform(post("/login")
				.with(csrf())
				.session(anonymousSession)
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		val redirectedUrl = loginResult.getResponse().getRedirectedUrl();
		val authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

		assertNotNull(redirectedUrl);
		assertTrue(redirectedUrl.endsWith("/journal"));
		assertNotNull(authenticatedSession);

		mockMvc.perform(get("/journal").session(authenticatedSession))
			.andExpect(status().isOk())
			.andExpect(view().name("journal"))
			.andExpect(content().string(containsString("Jak się dziś czujesz?")))
			.andExpect(content().string(containsString("Nie masz jeszcze zapisanych wpisów.")))
			.andExpect(content().string(containsString("ela@example.com")));
	}

	@Test
	@DisplayName("keeps the journal redirect target when the browser requests the favicon before login")
	void faviconRequestDoesNotOverrideJournalSavedRequest() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		val anonymousJournalResult = mockMvc.perform(get("/journal"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"))
			.andReturn();
		val anonymousSession = (MockHttpSession) anonymousJournalResult.getRequest().getSession(false);

		assertNotNull(anonymousSession);

		mockMvc.perform(get("/favicon.ico").session(anonymousSession))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<svg")));

		val loginResult = mockMvc.perform(post("/login")
				.with(csrf())
				.session(anonymousSession)
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		val redirectedUrl = loginResult.getResponse().getRedirectedUrl();

		assertNotNull(redirectedUrl);
		assertTrue(redirectedUrl.endsWith("/journal"));
	}

	@Test
	@DisplayName("returns an anonymous journal history request to the history page after login")
	void anonymousHistoryRequestReturnsToHistoryAfterLogin() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		val anonymousHistoryResult = mockMvc.perform(get("/journal/history"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"))
			.andReturn();
		val anonymousSession = (MockHttpSession) anonymousHistoryResult.getRequest().getSession(false);

		assertNotNull(anonymousSession);

		val loginResult = mockMvc.perform(post("/login")
				.with(csrf())
				.session(anonymousSession)
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		val redirectedUrl = loginResult.getResponse().getRedirectedUrl();
		val authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

		assertNotNull(redirectedUrl);
		assertTrue(redirectedUrl.endsWith("/journal/history"));
		assertNotNull(authenticatedSession);

		mockMvc.perform(get("/journal/history").session(authenticatedSession))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-history"))
			.andExpect(content().string(containsString("Historia wpisów")))
			.andExpect(content().string(containsString("ela@example.com")));
	}

	@Test
	@DisplayName("returns an anonymous journal trends request to the trends page after login")
	void anonymousTrendsRequestReturnsToTrendsAfterLogin() throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		val anonymousTrendsResult = mockMvc.perform(get("/journal/trends"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"))
			.andReturn();
		val anonymousSession = (MockHttpSession) anonymousTrendsResult.getRequest().getSession(false);

		assertNotNull(anonymousSession);

		val loginResult = mockMvc.perform(post("/login")
				.with(csrf())
				.session(anonymousSession)
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		val redirectedUrl = loginResult.getResponse().getRedirectedUrl();
		val authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

		assertNotNull(redirectedUrl);
		assertTrue(redirectedUrl.endsWith("/journal/trends"));
		assertNotNull(authenticatedSession);

		mockMvc.perform(get("/journal/trends").session(authenticatedSession))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-trends"))
			.andExpect(content().string(containsString("Trendy nastroju")))
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

	@Test
	@DisplayName("clears access to the journal after logout")
	void logoutClearsJournalAccess() throws Exception {
		val registrationResult = mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"))
			.andReturn();
		val session = (MockHttpSession) registrationResult.getRequest().getSession(false);

		assertNotNull(session);

		mockMvc.perform(post("/logout")
				.with(csrf())
				.session(session))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?logout"));

		mockMvc.perform(get("/journal").session(session))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("logs registration success without exposing the password")
	void registrationWritesSafeAuthLog(CapturedOutput output) throws Exception {
		mockMvc.perform(post("/register")
				.with(csrf())
				.param("email", "ela@example.com")
				.param("password", "sekret"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/journal"));

		assertTrue(output.getOut().contains("auth.registration.success identifier=email-hash:"));
		assertFalse(output.getOut().contains("ela@example.com"));
		assertFalse(output.getOut().contains("sekret"));
	}

	@Test
	@DisplayName("logs login failures without exposing the password")
	void failedLoginWritesSafeAuthLog(CapturedOutput output) throws Exception {
		userAccountService.registerUser("ela@example.com", "sekret");

		mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", "ELA@example.com")
				.param("password", "tajne123"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?error"));

		assertTrue(output.getOut().contains("auth.login.failure identifier=email-hash:"));
		assertTrue(output.getOut().contains("reason=BadCredentialsException"));
		assertFalse(output.getOut().contains("ela@example.com"));
		assertFalse(output.getOut().contains("tajne123"));
	}
}
