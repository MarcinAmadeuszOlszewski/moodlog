package com.amadeuszx.moodlog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
class JournalTrendsFlowTests {

	private MockMvc mockMvc;

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@BeforeEach
	void cleanDatabase() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(SecurityMockMvcConfigurers.springSecurity())
			.build();
	}

	@Test
	@DisplayName("renders an empty trends page with chart-ready placeholders")
	void emptyTrendsPageShowsChartReadyPlaceholders() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val responseContent = mockMvc.perform(get("/journal/trends").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-trends"))
			.andExpect(content().string(containsString("Zapisz kilka wpisów, aby zobaczyć trendy nastroju.")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		val normalizedContent = normalize(responseContent);

		assertTrue(normalizedContent.contains("window.journalTrendsData={"));
		assertTrue(normalizedContent.contains("labels:[\"25.05\",\"26.05\",\"27.05\",\"28.05\",\"29.05\",\"30.05\",\"31.05\"]"));
		assertTrue(normalizedContent.contains("values:[null,null,null,null,null,null,null]"));
		assertTrue(normalizedContent.contains("labels:[\"06.04-12.04\",\"13.04-19.04\",\"20.04-26.04\",\"27.04-03.05\",\"04.05-10.05\",\"11.05-17.05\",\"18.05-24.05\",\"25.05-31.05\"]"));
	}

	@Test
	@DisplayName("renders sparse owner-only trend data without leaking another user's analytics")
	void trendsPageRendersSparseOwnerOnlyTrendData() throws Exception {
		val owner = createUserAccount("ela@example.com");
		val otherOwner = createUserAccount("ola@example.com");
		val sparseWeekEntry = createJournalEntry(
			owner,
			"W środku tygodnia było spokojniej.",
			MoodTag.CALM,
			65,
			Instant.parse("2026-05-27T07:00:00Z")
		);
		val completedDayEntry = createJournalEntry(
			owner,
			"Niedziela skończyła się spokojnie.",
			MoodTag.CALM,
			80,
			Instant.parse("2026-05-31T21:59:00Z")
		);
		val currentWeekEntry = createJournalEntry(
			owner,
			"Nowy tydzień zaczął się z radością.",
			MoodTag.JOY,
			90,
			Instant.parse("2026-05-31T22:05:00Z")
		);
		val foreignEntry = createJournalEntry(
			otherOwner,
			"Ten wpis nie może wpłynąć na analitykę Eli.",
			MoodTag.SADNESS,
			5,
			Instant.parse("2026-05-31T21:30:00Z")
		);

		journalEntryRepository.saveAllAndFlush(List.of(sparseWeekEntry, completedDayEntry, currentWeekEntry, foreignEntry));

		val responseContent = mockMvc.perform(get("/journal/trends").with(user(owner.getEmail()).roles("USER")))
			.andExpect(status().isOk())
			.andExpect(view().name("journal-trends"))
			.andExpect(content().string(containsString("Radość")))
			.andExpect(content().string(containsString("90/100")))
			.andExpect(content().string(containsString("Brak wpisów w niektórych dniach — wykres zostawia luki zamiast zgadywać wartości.")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		val normalizedContent = normalize(responseContent);

		assertTrue(normalizedContent.contains("values:[null,null,65,null,null,null,80]"));
		assertTrue(normalizedContent.contains("values:[null,null,null,null,null,null,null,73]"));
		assertTrue(normalizedContent.contains("averageMoodScore:90"));
	}

	private String normalize(String content) {
		return content.replace(" ", "")
			.replace("\r", "")
			.replace("\n", "");
	}

	private UserAccount createUserAccount(String email) {
		return userAccountRepository.saveAndFlush(new UserAccount(
			UUID.randomUUID(),
			email,
			"{noop}password",
			true,
			Instant.now(),
			Instant.now()
		));
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

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
		}
	}
}
