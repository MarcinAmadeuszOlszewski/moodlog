package com.amadeuszx.moodlog.journal;

import java.util.List;

import com.amadeuszx.moodlog.classification.MoodClassificationFailedException;
import com.amadeuszx.moodlog.journal.history.JournalEntryListItem;
import com.amadeuszx.moodlog.journal.history.JournalHistoryItem;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class JournalController {

	private final JournalEntryService journalEntryService;
	private final int journalMaxContentLength;
	private final int recentEntriesLimit;
	private final int historyPageSize;
	private final int weeklyTrendSpan;

	public JournalController(
		JournalEntryService journalEntryService,
		@Value("${moodlog.journal.max-content-length:2000}") int journalMaxContentLength,
		@Value("${moodlog.journal.recent-list-limit:10}") int recentEntriesLimit,
		@Value("${moodlog.journal.history-page-size:20}") int historyPageSize,
		@Value("${moodlog.journal.weekly-trend-span:8}") int weeklyTrendSpan
	) {
		this.journalEntryService = journalEntryService;
		this.journalMaxContentLength = journalMaxContentLength;
		this.recentEntriesLimit = recentEntriesLimit;
		this.historyPageSize = historyPageSize;
		this.weeklyTrendSpan = weeklyTrendSpan;
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

	@GetMapping("/journal/history")
	public String historyPage(
		Authentication authentication,
		@RequestParam(name = "page", defaultValue = "0") int page,
		Model model
	) {
		final String userEmail = authentication.getName();
		Page<JournalHistoryItem> historyPage = journalEntryService.getHistoryEntries(userEmail, page);

		if (historyPage.isEmpty() && historyPage.getTotalPages() > 0 && page >= historyPage.getTotalPages()) {
			historyPage = journalEntryService.getHistoryEntries(userEmail, historyPage.getTotalPages() - 1);
		}

		populateHistoryModel(userEmail, historyPage, model);

		return "journal-history";
	}

	@GetMapping("/journal/trends")
	public String trendsPage(Authentication authentication, Model model) {
		final String userEmail = authentication.getName();

		model.addAttribute("trendView", journalEntryService.getTrendView(userEmail));
		model.addAttribute("userEmail", userEmail);
		model.addAttribute("weeklyTrendSpan", weeklyTrendSpan);

		return "journal-trends";
	}

	private void populateJournalModel(String userEmail, Model model) {
		final List<JournalEntryListItem> recentEntries = journalEntryService.getRecentEntryListItems(userEmail);

		model.addAttribute("journalMaxContentLength", journalMaxContentLength);
		model.addAttribute("recentEntriesLimit", recentEntriesLimit);
		model.addAttribute("recentEntries", recentEntries);
		model.addAttribute("userEmail", userEmail);
	}

	private void populateHistoryModel(String userEmail, Page<JournalHistoryItem> historyPage, Model model) {
		final int historyCurrentPage = historyPage.getTotalPages() == 0 ? 1 : historyPage.getNumber() + 1;
		final int historyTotalPages = Math.max(historyPage.getTotalPages(), 1);

		model.addAttribute("historyCurrentPage", historyCurrentPage);
		model.addAttribute("historyEntries", historyPage.getContent());
		model.addAttribute("historyHasNext", historyPage.hasNext());
		model.addAttribute("historyHasPrevious", historyPage.hasPrevious());
		model.addAttribute("historyNextPage", historyPage.getNumber() + 1);
		model.addAttribute("historyPreviousPage", Math.max(historyPage.getNumber() - 1, 0));
		model.addAttribute("historyPageSize", historyPageSize);
		model.addAttribute("historyTotalPages", historyTotalPages);
		model.addAttribute("userEmail", userEmail);
	}
}
