package com.amadeuszx.moodlog.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.amadeuszx.moodlog.user.register.DuplicateUserAccountException;
import com.amadeuszx.moodlog.user.register.InvalidPasswordException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class UserAccountService implements UserDetailsService {

	static final String DEFAULT_TIMEZONE = "Europe/Warsaw";

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

	public UserAccount registerUser(String email, String rawPassword, String timezone) {
		final String normalizedEmail = normalizeEmailAddress(email);

		if (userAccountRepository.existsByEmail(normalizedEmail)) {
			logDuplicateEmailRegistration(normalizedEmail);
			throw new DuplicateUserAccountException();
		}

		ensurePasswordMeetsPolicy(rawPassword, normalizedEmail);

		final String effectiveTimezone = resolveTimezone(timezone, normalizedEmail);

		final Instant now = Instant.now();
		final UserAccount userAccount = new UserAccount(
			UUID.randomUUID(),
			normalizedEmail,
			passwordEncoder.encode(rawPassword),
			true,
			now,
			now,
			effectiveTimezone
		);

		try {
			final UserAccount savedUserAccount = userAccountRepository.save(userAccount);

			log.info("auth.registration.success identifier={}", safeEmailIdentifier(normalizedEmail));

			return savedUserAccount;
		}
		// Handles concurrent-registration race: both requests passed existsByEmail, but DB unique constraint fires on the second insert.
		catch (DataIntegrityViolationException exception) {
			logDuplicateEmailRegistration(normalizedEmail);
			throw new DuplicateUserAccountException();
		}
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

	public static String safeEmailIdentifier(String email) {
		if (!StringUtils.hasText(email)) {
			return "missing-email";
		}

		try {
			final String truncated = email.length() > 500 ? email.substring(0, 500) : email;
			return "email-hash:" + hashEmail(normalizeEmailAddress(truncated));
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
			log.warn(
				"auth.registration.failure identifier={} reason=PASSWORD_TOO_SHORT minLength={}",
				safeEmailIdentifier(normalizedEmail),
				passwordMinimumLength
			);
			throw new InvalidPasswordException(passwordMinimumLength);
		}
	}

	private void logDuplicateEmailRegistration(String normalizedEmail) {
		log.warn("auth.registration.failure identifier={} reason=DUPLICATE_EMAIL", safeEmailIdentifier(normalizedEmail));
	}

	private static String hashEmail(String normalizedEmail) {
		try {
			final MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			final byte[] hashedBytes = messageDigest.digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));

			return HexFormat.of().formatHex(hashedBytes).substring(0, 12);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
		}
	}

	static String normalizeEmailAddress(String email) {
		if (!StringUtils.hasText(email)) {
			throw new IllegalArgumentException("Email must not be blank");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	private static String resolveTimezone(String timezone, String normalizedEmail) {
		if (!StringUtils.hasText(timezone)) {
			return DEFAULT_TIMEZONE;
		}

		try {
			ZoneId.of(timezone);
			return timezone;
		}
		catch (DateTimeException exception) {
			log.warn(
				"auth.registration.timezone.invalid identifier={} timezone={}",
				safeEmailIdentifier(normalizedEmail),
				timezone.substring(0, Math.min(timezone.length(), 64))
			);
			return DEFAULT_TIMEZONE;
		}
	}
}
