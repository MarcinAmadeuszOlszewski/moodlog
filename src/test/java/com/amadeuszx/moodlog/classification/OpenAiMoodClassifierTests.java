package com.amadeuszx.moodlog.classification;

import java.io.InterruptedIOException;

import com.openai.errors.OpenAIIoException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import org.mockito.ArgumentCaptor;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

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
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.PROVIDER_ERROR, exception.getReason());
		assertEquals("openai", exception.getProvider());
		assertEquals("gpt-4o-mini", exception.getModel());
	}

	@Test
	@DisplayName("maps sdk timeout failures to a timeout reason")
	void slowProviderCallBecomesProviderTimeout() {
		when(openAiChatModel.call(any(Prompt.class))).thenThrow(
			new OpenAIIoException("Request failed", new InterruptedIOException("timeout"))
		);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.PROVIDER_TIMEOUT, exception.getReason());
		assertEquals("openai", exception.getProvider());
		assertEquals("gpt-4o-mini", exception.getModel());
	}

	@Test
	@DisplayName("maps non-null response with null result to an invalid response reason")
	void mapsNonNullResponseWithNullResultToAnInvalidResponseReason() {
		val response = Mockito.mock(ChatResponse.class);
		when(response.getResult()).thenReturn(null);
		when(openAiChatModel.call(any(Prompt.class))).thenReturn(response);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.INVALID_RESPONSE, exception.getReason());
	}

	@Test
	@DisplayName("maps blank provider response text to an invalid response reason")
	void mapsBlankProviderResponseTextToAnInvalidResponseReason() {
		val response = Mockito.mock(ChatResponse.class, Mockito.RETURNS_DEEP_STUBS);
		when(response.getResult().getOutput().getText()).thenReturn("");
		when(openAiChatModel.call(any(Prompt.class))).thenReturn(response);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.INVALID_RESPONSE, exception.getReason());
	}

	@Test
	@DisplayName("maps out-of-range mood score to an invalid response reason")
	void mapsOutOfRangeMoodScoreToAnInvalidResponseReason() {
		val response = Mockito.mock(ChatResponse.class, Mockito.RETURNS_DEEP_STUBS);
		when(response.getResult().getOutput().getText()).thenReturn("{\"moodTag\":\"CALM\",\"moodScore\":150}");
		when(openAiChatModel.call(any(Prompt.class))).thenReturn(response);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.INVALID_RESPONSE, exception.getReason());
	}

	@Test
	@DisplayName("maps missing mood score field to an invalid response reason")
	void mapsMissingMoodScoreFieldToAnInvalidResponseReason() {
		val response = Mockito.mock(ChatResponse.class, Mockito.RETURNS_DEEP_STUBS);
		when(response.getResult().getOutput().getText()).thenReturn("{\"moodTag\":\"CALM\"}");
		when(openAiChatModel.call(any(Prompt.class))).thenReturn(response);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		val exception = assertThrows(
			MoodClassificationFailedException.class,
			() -> openAiMoodClassifier.classify("Po spacerze czuję spokój.")
		);

		assertEquals(MoodClassificationFailureReason.INVALID_RESPONSE, exception.getReason());
	}

	@Test
	@DisplayName("none response format mode omits response format from built options")
	void noneResponseFormatModeOmitsResponseFormatFromOptions() {
		val response = Mockito.mock(ChatResponse.class, Mockito.RETURNS_DEEP_STUBS);
		when(response.getResult().getOutput().getText()).thenReturn("{\"moodTag\":\"CALM\",\"moodScore\":50}");
		val promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		when(openAiChatModel.call(promptCaptor.capture())).thenReturn(response);
		val openAiMoodClassifier = new OpenAiMoodClassifier(openAiChatModel, "gpt-4o-mini", "none");

		openAiMoodClassifier.classify("Po spacerze czuję spokój.");

		val options = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
		assertNull(options.getResponseFormat());
	}
}
