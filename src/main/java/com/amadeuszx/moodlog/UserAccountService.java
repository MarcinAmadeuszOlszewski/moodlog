package com.amadeuszx.moodlog;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserAccountService implements UserDetailsService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public UserAccount registerUser(String email, String rawPassword) {
		final String normalizedEmail = normalizeEmail(email);
		final Instant now = Instant.now();
		final UserAccount userAccount = new UserAccount(
			UUID.randomUUID(),
			normalizedEmail,
			passwordEncoder.encode(rawPassword),
			true,
			now,
			now
		);

		return userAccountRepository.save(userAccount);
	}

	public Optional<UserAccount> findByEmail(String email) {
		final String normalizedEmail = normalizeEmail(email);

		return userAccountRepository.findByEmail(normalizedEmail);
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		final String normalizedEmail = normalizeEmail(username);
		final UserAccount userAccount = userAccountRepository.findByEmail(normalizedEmail)
			.orElseThrow(() -> new UsernameNotFoundException("No user found for email: " + normalizedEmail));

		return User.withUsername(userAccount.getEmail())
			.password(userAccount.getPasswordHash())
			.roles("USER")
			.disabled(!userAccount.isActive())
			.build();
	}

	private String normalizeEmail(String email) {
		if (!StringUtils.hasText(email)) {
			throw new IllegalArgumentException("Email must not be blank");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
