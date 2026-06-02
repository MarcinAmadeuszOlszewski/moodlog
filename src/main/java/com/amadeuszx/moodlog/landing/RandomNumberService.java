package com.amadeuszx.moodlog.landing;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

@Service
public class RandomNumberService {

	public int nextGuestNumber() {
		return ThreadLocalRandom.current().nextInt(1, 100_001);
	}
}
