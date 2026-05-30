package com.amadeuszx.moodlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

@Configuration
public class SecurityConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		RequestCache requestCache,
		AuthenticationSuccessHandler authenticationSuccessHandler,
		AuthenticationFailureHandler authenticationFailureHandler
	) throws Exception {
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
				.successHandler(authenticationSuccessHandler)
				.failureHandler(authenticationFailureHandler)
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
	public AuthenticationSuccessHandler authenticationSuccessHandler(RequestCache requestCache) {
		final SavedRequestAwareAuthenticationSuccessHandler authenticationSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();

		authenticationSuccessHandler.setDefaultTargetUrl("/journal");
		authenticationSuccessHandler.setAlwaysUseDefaultTargetUrl(false);
		authenticationSuccessHandler.setRequestCache(requestCache);

		return (request, response, authentication) -> {
			logger.info("auth.login.success email={}", UserAccountService.safeEmailIdentifier(authentication.getName()));
			authenticationSuccessHandler.onAuthenticationSuccess(request, response, authentication);
		};
	}

	@Bean
	public AuthenticationFailureHandler authenticationFailureHandler() {
		final SimpleUrlAuthenticationFailureHandler authenticationFailureHandler = new SimpleUrlAuthenticationFailureHandler("/login?error");

		return (request, response, exception) -> {
			final String safeEmailIdentifier = UserAccountService.safeEmailIdentifier(request.getParameter("email"));

			logger.warn(
				"auth.login.failure email={} reason={}",
				safeEmailIdentifier,
				exception.getClass().getSimpleName()
			);
			authenticationFailureHandler.onAuthenticationFailure(request, response, exception);
		};
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
