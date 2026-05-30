package com.amadeuszx.moodlog;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class JournalController {

	@GetMapping("/journal")
	public String journal(Authentication authentication, Model model) {
		final String userEmail = authentication.getName();

		model.addAttribute("userEmail", userEmail);
		model.addAttribute("firstEntryCallToAction", "Dodaj pierwszy wpis");
		model.addAttribute("firstEntryHint", "Tworzenie wpisów odblokujemy w kolejnym kroku.");

		return "journal";
	}
}
