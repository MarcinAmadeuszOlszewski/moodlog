package com.amadeuszx.moodlog.e2e;

import com.amadeuszx.moodlog.journal.JournalEntryRepository;
import com.amadeuszx.moodlog.user.UserAccountRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JournalHappyPathE2ETests {

    private static final String TEST_EMAIL = "e2e@example.com";
    private static final String TEST_PASSWORD = "e2e-password";
    private static final String TEST_ENTRY = "E2E happy path test entry.";

    @LocalServerPort
    private int port;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void setUp() {
        journalEntryRepository.deleteAll();
        userAccountRepository.deleteAll();
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterAll
    void tearDown() {
        try {
            if (browser != null) {
                browser.close();
            }
        } finally {
            if (playwright != null) {
                playwright.close();
            }
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @Order(1)
    @DisplayName("registration redirects to the journal page")
    void registerLandsOnJournal() {
        page.navigate(url("/register"));
        page.fill("#email", TEST_EMAIL);
        page.fill("#password", TEST_PASSWORD);
        page.click("button[type='submit']");

        assertThat(page).hasURL(url("/journal"));
        assertThat(page.locator("body")).containsText("Twój prywatny dziennik");
    }

    @Test
    @Order(2)
    @DisplayName("created entry appears in the journal list")
    void createEntryAppearsInList() {
        page.navigate(url("/journal"));
        page.fill("#content", TEST_ENTRY);
        page.click("text=Zapisz wpis");

        assertThat(page).hasURL(url("/journal"));
        assertThat(page.locator("body")).containsText(TEST_ENTRY);
    }

    @Test
    @Order(3)
    @DisplayName("history page lists the previously created entry")
    void historyShowsCreatedEntry() {
        page.navigate(url("/journal/history"));

        assertThat(page).hasURL(url("/journal/history"));
        assertThat(page.locator("body")).containsText(TEST_ENTRY);
    }

    @Test
    @Order(4)
    @DisplayName("trends page renders the seven-day chart canvas")
    void trendsPageLoadsWithChartCanvas() {
        page.navigate(url("/journal/trends"));

        assertThat(page).hasURL(url("/journal/trends"));
        assertThat(page.locator("body")).containsText("Trendy nastroju");
        assertThat(page.locator("#seven-day-trend-chart")).isVisible();
    }

    @Test
    @Order(5)
    @DisplayName("logout clears session and re-login restores journal access")
    void logoutAndLoginAgain() {
        page.click("text=Wyloguj się");

        assertThat(page).hasURL(url("/login?logout"));

        page.navigate(url("/login"));
        page.fill("#email", TEST_EMAIL);
        page.fill("#password", TEST_PASSWORD);
        page.click("button[type='submit']");

        assertThat(page).hasURL(url("/journal"));
    }
}
