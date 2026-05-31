package com.amadeuszx.moodlog;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.StringUtils;

public class OpenAiMoodClassifier implements MoodClassifier {

	private static final String PROVIDER_NAME = "openai";
	private static final String USER_SAFE_ERROR_MESSAGE = "Nie udało się sklasyfikować wpisu.";

	private final OpenAiChatModel openAiChatModel;
	private final String defaultModel;
	private final Duration timeout;

	public OpenAiMoodClassifier(OpenAiChatModel openAiChatModel, String defaultModel, Duration timeout) {
		this.openAiChatModel = openAiChatModel;
		this.defaultModel = defaultModel;
		this.timeout = timeout;
	}

	@Override
	public MoodClassification classify(String entryText) {
		final BeanOutputConverter<OpenAiMoodResponse> outputConverter = new BeanOutputConverter<>(OpenAiMoodResponse.class);
		final OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
			.model(defaultModel)
			.responseFormat(ResponseFormat.builder()
				.type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(outputConverter.getJsonSchema())
				.build())
			.build();
		final Prompt prompt = new Prompt(buildPrompt(entryText), chatOptions);
		final ChatResponse response = callWithinTimeout(prompt);
		final String responseText = extractResponseText(response);

		try {
			final OpenAiMoodResponse openAiMoodResponse = outputConverter.convert(responseText);

			if (openAiMoodResponse == null) {
				throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
			}

			return new MoodClassification(
				openAiMoodResponse.moodTag(),
				openAiMoodResponse.moodScore(),
				PROVIDER_NAME,
				defaultModel,
				Instant.now()
			);
		}
		catch (MoodClassificationFailedException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE, exception);
		}
	}

	private ChatResponse callWithinTimeout(Prompt prompt) {
		final CompletableFuture<ChatResponse> responseFuture = CompletableFuture.supplyAsync(() -> openAiChatModel.call(prompt));

		try {
			return responseFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException exception) {
			responseFuture.cancel(true);
			Thread.currentThread().interrupt();
			throw buildFailureException(MoodClassificationFailureReason.PROVIDER_ERROR, exception);
		}
		catch (ExecutionException exception) {
			responseFuture.cancel(true);
			final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
			throw buildFailureException(MoodClassificationFailureReason.PROVIDER_ERROR, cause);
		}
		catch (TimeoutException exception) {
			responseFuture.cancel(true);
			throw buildFailureException(MoodClassificationFailureReason.PROVIDER_TIMEOUT, exception);
		}
	}

	private MoodClassificationFailedException buildFailureException(MoodClassificationFailureReason reason) {
		return new MoodClassificationFailedException(USER_SAFE_ERROR_MESSAGE, reason, PROVIDER_NAME, defaultModel);
	}

	private MoodClassificationFailedException buildFailureException(MoodClassificationFailureReason reason, Throwable cause) {
		return new MoodClassificationFailedException(USER_SAFE_ERROR_MESSAGE, reason, PROVIDER_NAME, defaultModel, cause);
	}

	private String extractResponseText(ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
		}

		final String responseText = response.getResult().getOutput().getText();

		if (!StringUtils.hasText(responseText)) {
			throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
		}

		return responseText;
	}

	private String buildPrompt(String entryText) {
		return """
			Classify a single Polish-language journal entry into one dominant mood.
			Return JSON only.
			Use exactly one moodTag from: JOY, CALM, NEUTRAL, SADNESS, ANXIETY, ANGER, OVERWHELMED.
			Set moodScore to an integer from 0 to 100 that represents the intensity of the selected mood.
			Focus on the dominant emotional tone of the entry instead of every minor feeling.

			Journal entry:
			%s
			""".formatted(entryText);
	}

	private record OpenAiMoodResponse(MoodTag moodTag, int moodScore) {
	}
}
