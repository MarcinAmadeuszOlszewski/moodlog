package com.amadeuszx.moodlog.landing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class RandomNumberController {

	private final RandomNumberService randomNumberService;

	public RandomNumberController(RandomNumberService randomNumberService) {
		this.randomNumberService = randomNumberService;
	}

	@GetMapping("/random")
	public int random() {
		return randomNumberService.nextGuestNumber();
	}
}
