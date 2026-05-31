package com.amadeuszx.moodlog;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class JournalController {

	private static final int ENTRY_EXCERPT_LENGTH = 120;

	private final JournalEntryService journalEntryService;
	private final int journalMaxContentLength;

	public JournalController(
		JournalEntryService journalEntryService,
		@Value("${moodlog.journal.max-content-length:2000}") int journalMaxContentLength
	) {
		this.journalEntryService = journalEntryService;
		this.journalMaxContentLength = journalMaxContentLength;
	}

	@GetMapping("/journal")
	public String journalPage(Authentication authentication, Model model) {
		final String userEmail = authentication.getName();

		if (!model.containsAttribute("journalEntryForm")) {
			model.addAttribute("journalEntryForm", new JournalEntryForm());
		}

		populateJournalModel(userEmail, model);

		return "journal";
	}

	@PostMapping("/journal")
	public String saveJournalEntry(
		Authentication authentication,
		@Valid @ModelAttribute("journalEntryForm") JournalEntryForm journalEntryForm,
		BindingResult bindingResult,
		Model model
	) {
		final String userEmail = authentication.getName();

		validateContentLength(journalEntryForm, bindingResult);
		if (bindingResult.hasErrors()) {
			populateJournalModel(userEmail, model);
			return "journal";
		}

		try {
			journalEntryService.saveEntry(userEmail, journalEntryForm.getContent());
			return "redirect:/journal";
		}
		catch (MoodClassificationFailedException exception) {
			model.addAttribute("classificationError", exception.getMessage());
			populateJournalModel(userEmail, model);
			return "journal";
		}
	}

	private void populateJournalModel(String userEmail, Model model) {
		final List<JournalEntryListItem> recentEntries = journalEntryService.getRecentEntries(userEmail).stream()
			.map(this::toListItem)
			.toList();

		model.addAttribute("journalMaxContentLength", journalMaxContentLength);
		model.addAttribute("recentEntries", recentEntries);
		model.addAttribute("userEmail", userEmail);
	}

	private void validateContentLength(JournalEntryForm journalEntryForm, BindingResult bindingResult) {
		final String content = journalEntryForm.getContent();

		if (content != null && content.length() > journalMaxContentLength) {
			bindingResult.rejectValue(
				"content",
				"journalEntryForm.content.tooLong",
				"Wpis może mieć maksymalnie " + journalMaxContentLength + " znaków."
			);
		}
	}

	private JournalEntryListItem toListItem(JournalEntry journalEntry) {
		final String excerpt = buildExcerpt(journalEntry.getContent());
		final String moodLabel = polishMoodLabel(journalEntry.getSystemMoodTag());

		return new JournalEntryListItem(excerpt, moodLabel, journalEntry.getSystemMoodScore());
	}

	private String buildExcerpt(String content) {
		if (content.length() <= ENTRY_EXCERPT_LENGTH) {
			return content;
		}

		return content.substring(0, ENTRY_EXCERPT_LENGTH - 3) + "...";
	}

	private String polishMoodLabel(MoodTag moodTag) {
		return switch (moodTag) {
			case JOY -> "Radość";
			case CALM -> "Spokój";
			case NEUTRAL -> "Neutralnie";
			case SADNESS -> "Smutek";
			case ANXIETY -> "Lęk";
			case ANGER -> "Złość";
			case OVERWHELMED -> "Przytłoczenie";
		};
	}
}
