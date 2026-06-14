package com.amadeuszx.moodlog.user;

import java.time.Instant;
import java.util.UUID;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UserAccountRepositoryTests {

	@Autowired
	private UserAccountRepository userAccountRepository;

	@BeforeEach
	void setUp() {
		userAccountRepository.deleteAll();
	}

	@Test
	@DisplayName("persists and reloads a user account by email")
	void userAccountPersistsAndCanBeLoadedByEmail() {
		val createdAt = Instant.now();
		val userAccount = new UserAccount(
			UUID.randomUUID(),
			"ela@example.com",
			"$2a$10$storedHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
		);

		userAccountRepository.saveAndFlush(userAccount);

		val loadedUserAccount = userAccountRepository.findByEmail("ela@example.com");

		assertTrue(loadedUserAccount.isPresent());
		assertEquals("ela@example.com", loadedUserAccount.get().getEmail());
		assertEquals("$2a$10$storedHash", loadedUserAccount.get().getPasswordHash());
		assertTrue(loadedUserAccount.get().isActive());
	}

	@Test
	@DisplayName("rejects duplicate email addresses at the persistence layer")
	void duplicateEmailCannotBePersisted() {
		val createdAt = Instant.now();
		val firstAccount = new UserAccount(
			UUID.randomUUID(),
			"ela@example.com",
			"$2a$10$firstHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
		);
		val duplicateAccount = new UserAccount(
			UUID.randomUUID(),
			"ela@example.com",
			"$2a$10$secondHash",
			true,
			createdAt,
			createdAt,
			"Europe/Warsaw"
		);

		userAccountRepository.saveAndFlush(firstAccount);

		assertThrows(DataIntegrityViolationException.class, () -> userAccountRepository.saveAndFlush(duplicateAccount));
	}
}
