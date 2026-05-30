package com.amadeuszx.moodlog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

@Configuration
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, RequestCache requestCache) throws Exception {
		return http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/", "/index", "/login", "/register", "/error", "/v1/random", "/css/**", "/js/**", "/images/**")
				.permitAll()
				.anyRequest()
				.authenticated()
			)
			.requestCache(requestCacheConfigurer -> requestCacheConfigurer
				.requestCache(requestCache)
			)
			.formLogin(formLogin -> formLogin
				.loginPage("/login")
				.loginProcessingUrl("/login")
				.usernameParameter("email")
				.defaultSuccessUrl("/journal", false)
				.failureUrl("/login?error")
				.permitAll()
			)
			.logout(logout -> logout
				.clearAuthentication(true)
				.invalidateHttpSession(true)
				.logoutSuccessUrl("/login?logout")
			)
			.build();
	}

	@Bean
	public RequestCache requestCache() {
		final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

		requestCache.setMatchingRequestParameterName(null);

		return requestCache;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
