package com.amadeuszx.moodlog.migration;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.amadeuszx.moodlog.classification.MoodTag;
import com.amadeuszx.moodlog.journal.JournalEntry;
import com.amadeuszx.moodlog.journal.JournalEntryRepository;
import com.amadeuszx.moodlog.user.UserAccount;
import com.amadeuszx.moodlog.user.UserAccountRepository;
import lombok.val;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class FlywayMigrationPostgresTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@DynamicPropertySource
	static void configurePostgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private Flyway flyway;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JournalEntryRepository journalEntryRepository;

	@BeforeEach
	void cleanUp() {
		journalEntryRepository.deleteAll();
		userAccountRepository.deleteAll();
	}

	@Test
	@DisplayName("all V1–V4 Flyway migrations apply cleanly against PostgreSQL 16")
	void allMigrationsApplyCleanlyToPostgres() {
		val applied = Arrays.stream(flyway.info().all())
			.filter(info -> info.getState() == MigrationState.SUCCESS)
			.count();
		assertEquals(4L, applied);
	}

	@Test
	@DisplayName("user_accounts schema is queryable and enforces email uniqueness after migration")
	void userAccountSchemaIsQueryable() {
		val createdAt = Instant.now();
		val account = new UserAccount(
			UUID.randomUUID(),
			"test@example.com",
			"$2a$10$storedHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
		);
		userAccountRepository.saveAndFlush(account);

		assertTrue(userAccountRepository.findByEmail("test@example.com").isPresent());

		val duplicateAccount = new UserAccount(
			UUID.randomUUID(),
			"test@example.com",
			"$2a$10$anotherHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
		);
		assertThrows(DataIntegrityViolationException.class,
			() -> userAccountRepository.saveAndFlush(duplicateAccount));
	}

	@Test
	@DisplayName("journal_entries schema is queryable with FK and CHECK constraints live after migration")
	void journalEntrySchemaIsQueryable() {
		val createdAt = Instant.now();
		val owner = new UserAccount(
			UUID.randomUUID(),
			"owner@example.com",
			"$2a$10$storedHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
		);
		userAccountRepository.saveAndFlush(owner);

		val entry = new JournalEntry(
			UUID.randomUUID(),
			owner,
			"Test entry content.",
			MoodTag.CALM,
			75,
			null,
			null,
			"stub",
			"stub-v1",
			createdAt.plusSeconds(5),
			createdAt,
			createdAt
		);
		journalEntryRepository.saveAndFlush(entry);

		val loaded = journalEntryRepository.findTop10ByUserAccountIdOrderByCreatedAtDesc(owner.getId());
		assertEquals(1, loaded.size());
	}
}
