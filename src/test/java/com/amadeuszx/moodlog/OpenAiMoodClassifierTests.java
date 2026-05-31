package com.amadeuszx.moodlog;

import java.time.Duration;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiMoodClassifierTests {

	@Mock
	private OpenAiChatModel openAiChatModel;

	@Test
	@DisplayName("maps missing provider responses to an invalid response reason")
	void missingProviderResponseBecomesInvalidResponse() {
		when(openAiChatModel.call(any(Prompt.class))).thenReturn(null);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", Duration.ofSeconds(1));

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.INVALID_RESPONSE, exception.getReason());
		assertEquals("openai", exception.getProvider());
		assertEquals("gpt-4o-mini", exception.getModel());
	}

	@Test
	@DisplayName("maps provider call failures to a provider error reason")
	void providerCallFailureBecomesProviderError() {
		when(openAiChatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider raw payload"));
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", Duration.ofSeconds(1));

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.PROVIDER_ERROR, exception.getReason());
		assertEquals("openai", exception.getProvider());
		assertEquals("gpt-4o-mini", exception.getModel());
	}

	@Test
	@DisplayName("maps slow provider calls to a timeout reason")
	void slowProviderCallBecomesProviderTimeout() {
		when(openAiChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
			Thread.sleep(100);
			return null;
		});
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", Duration.ofMillis(10));

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.PROVIDER_TIMEOUT, exception.getReason());
		assertEquals("openai", exception.getProvider());
		assertEquals("gpt-4o-mini", exception.getModel());
	}
}
