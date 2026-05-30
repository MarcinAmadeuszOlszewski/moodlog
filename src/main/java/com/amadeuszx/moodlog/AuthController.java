package com.amadeuszx.moodlog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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

	public AuthController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
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
		HttpServletRequest request
	) {
		if (bindingResult.hasErrors()) {
			return "register";
		}

		try {
			final UserAccount userAccount = userAccountService.registerUser(
				registrationForm.getEmail(),
				registrationForm.getPassword()
			);

			authenticate(userAccount.getEmail(), request);

			return "redirect:/journal";
		}
		catch (DuplicateUserAccountException exception) {
			bindingResult.rejectValue("email", "duplicate", "Konto z tym adresem e-mail już istnieje.");
			return "register";
		}
	}

	private void authenticate(String email, HttpServletRequest request) {
		final UserDetails userDetails = userAccountService.loadUserByUsername(email);
		final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			userDetails,
			userDetails.getPassword(),
			userDetails.getAuthorities()
		);
		final SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		final HttpSession session = request.getSession(true);

		securityContext.setAuthentication(authentication);
		SecurityContextHolder.setContext(securityContext);
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
	}
}
