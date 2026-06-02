package com.amadeuszx.moodlog.user;

import com.amadeuszx.moodlog.user.register.DuplicateUserAccountException;
import com.amadeuszx.moodlog.user.register.InvalidPasswordException;
import com.amadeuszx.moodlog.user.register.RegistrationForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

	private final UserAccountService userAccountService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextHolderStrategy securityContextHolderStrategy;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	public AuthController(
		UserAccountService userAccountService,
		AuthenticationManager authenticationManager,
		SecurityContextRepository securityContextRepository,
		SessionAuthenticationStrategy sessionAuthenticationStrategy
	) {
		this.userAccountService = userAccountService;
		this.authenticationManager = authenticationManager;
		this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
	}

	@ModelAttribute("registrationForm")
	public RegistrationForm registrationForm() {
		return new RegistrationForm();
	}

	@GetMapping("/login")
	public String login(
		@RequestParam(name = "error", required = false) String error,
		@RequestParam(name = "logout", required = false) String logout,
		Model model
	) {
		final boolean loginError = error != null;
		final boolean logoutSuccess = logout != null;

		model.addAttribute("loginError", loginError);
		model.addAttribute("logoutSuccess", logoutSuccess);

		return "login";
	}

	@GetMapping("/register")
	public String register() {
		return "register";
	}

	@PostMapping("/register")
	public String register(
		@Valid @ModelAttribute("registrationForm") RegistrationForm registrationForm,
		BindingResult bindingResult,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		if (bindingResult.hasErrors()) {
			return "register";
		}

		try {
			final UserAccount userAccount = userAccountService.registerUser(
				registrationForm.getEmail(),
				registrationForm.getPassword()
			);

			authenticate(userAccount.getEmail(), registrationForm.getPassword(), request, response);

			return "redirect:/journal";
		}
		catch (InvalidPasswordException exception) {
			bindingResult.rejectValue("password", "invalid", exception.getMessage());
			return "register";
		}
		catch (DuplicateUserAccountException exception) {
			bindingResult.rejectValue("email", "duplicate", "Konto z tym adresem e-mail już istnieje.");
			return "register";
		}
	}

	private void authenticate(
		String email,
		String rawPassword,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		final UsernamePasswordAuthenticationToken authenticationRequest =
			UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword);
		final Authentication authentication = authenticationManager.authenticate(authenticationRequest);
		final SecurityContext securityContext = securityContextHolderStrategy.createEmptyContext();

		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		securityContext.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(securityContext);
		securityContextRepository.saveContext(securityContext, request, response);
	}
}
