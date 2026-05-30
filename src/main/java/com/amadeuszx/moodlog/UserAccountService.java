package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAccountService implements UserDetailsService {

	private static final Logger logger = LoggerFactory.getLogger(UserAccountService.class);

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final int passwordMinimumLength;

	public UserAccountService(
		UserAccountRepository userAccountRepository,
		PasswordEncoder passwordEncoder,
		@Value("${moodlog.auth.password.min-length}") int passwordMinimumLength
	) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordMinimumLength = passwordMinimumLength;
	}

	public UserAccount registerUser(String email, String rawPassword) {
		final String normalizedEmail = normalizeEmailAddress(email);

		if (userAccountRepository.existsByEmail(normalizedEmail)) {
			logger.warn("auth.registration.failure email={} reason=DUPLICATE_EMAIL", normalizedEmail);
			throw new DuplicateUserAccountException();
		}

		ensurePasswordMeetsPolicy(rawPassword, normalizedEmail);

		final Instant now = Instant.now();
		final UserAccount userAccount = new UserAccount(
			UUID.randomUUID(),
			normalizedEmail,
			passwordEncoder.encode(rawPassword),
			true,
			now,
			now
		);

		final UserAccount savedUserAccount = userAccountRepository.save(userAccount);

		logger.info("auth.registration.success email={}", normalizedEmail);

		return savedUserAccount;
	}

	public Optional<UserAccount> findByEmail(String email) {
		final String normalizedEmail = normalizeEmailAddress(email);

		return userAccountRepository.findByEmail(normalizedEmail);
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		final String normalizedEmail = normalizeUsernameForAuthentication(username);
		final UserAccount userAccount = userAccountRepository.findByEmail(normalizedEmail)
			.orElseThrow(() -> new UsernameNotFoundException("Authentication failed"));

		return User.withUsername(userAccount.getEmail())
			.password(userAccount.getPasswordHash())
			.roles("USER")
			.disabled(!userAccount.isActive())
			.build();
	}

	static String safeEmailIdentifier(String email) {
		if (!StringUtils.hasText(email)) {
			return "missing-email";
		}

		try {
			return normalizeEmailAddress(email);
		}
		catch (IllegalArgumentException exception) {
			return "invalid-email";
		}
	}

	private static String normalizeUsernameForAuthentication(String username) {
		try {
			return normalizeEmailAddress(username);
		}
		catch (IllegalArgumentException exception) {
			throw new UsernameNotFoundException("Authentication failed");
		}
	}

	private void ensurePasswordMeetsPolicy(String rawPassword, String normalizedEmail) {
		if (!StringUtils.hasText(rawPassword) || rawPassword.length() < passwordMinimumLength) {
			logger.warn(
				"auth.registration.failure email={} reason=PASSWORD_TOO_SHORT minLength={}",
				normalizedEmail,
				passwordMinimumLength
			);
			throw new InvalidPasswordException(passwordMinimumLength);
		}
	}

	static String normalizeEmailAddress(String email) {
		if (!StringUtils.hasText(email)) {
			throw new IllegalArgumentException("Email must not be blank");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
