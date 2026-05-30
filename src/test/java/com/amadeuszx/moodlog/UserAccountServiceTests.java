package com.amadeuszx.moodlog;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTests {

	@Mock
	private UserAccountRepository userAccountRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserAccountService userAccountService;

	@BeforeEach
	void setUp() {
		userAccountService = new UserAccountService(userAccountRepository, passwordEncoder, 6);
	}

	@Test
	@DisplayName("translates save-time duplicate email collisions into domain duplicate exceptions")
	void saveTimeDuplicateEmailCollisionBecomesDuplicateUserAccountException() {
		when(userAccountRepository.existsByEmail("ela@example.com")).thenReturn(false);
		when(passwordEncoder.encode("sekret")).thenReturn("encoded-secret");
		when(userAccountRepository.save(any(UserAccount.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate"));

		val exception = assertThrows(
			DuplicateUserAccountException.class,
			() -> userAccountService.registerUser("ela@example.com", "sekret")
		);

		org.junit.jupiter.api.Assertions.assertNotNull(exception);
	}
}
