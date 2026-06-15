package com.amadeuszx.moodlog.classification;

import java.time.Instant;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;

import com.openai.errors.OpenAIIoException;
import com.amadeuszx.moodlog.journal.JournalEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.StringUtils;

@Slf4j
public class OpenAiMoodClassifier implements MoodClassifier {

	private static final String PROVIDER_NAME = "openai";
	private static final String USER_SAFE_ERROR_MESSAGE = "Nie udało się sklasyfikować wpisu.";

	private final OpenAiChatModel openAiChatModel;
	private final String defaultModel;
	private final String responseFormatType;

	public OpenAiMoodClassifier(OpenAiChatModel openAiChatModel, String defaultModel, String responseFormatType) {
		if (!"none".equals(responseFormatType) && !"json_object".equals(responseFormatType) && !"json_schema".equals(responseFormatType)) {
			throw new IllegalArgumentException("Unrecognised responseFormatType: " + responseFormatType);
		}
		this.openAiChatModel = openAiChatModel;
		this.defaultModel = defaultModel;
		this.responseFormatType = responseFormatType;
	}

	@Override
	public MoodClassification classify(String entryText) {
		final String sanitizedText = (entryText != null && entryText.length() > JournalEntry.MAX_CONTENT_LENGTH)
			? entryText.substring(0, JournalEntry.MAX_CONTENT_LENGTH)
			: entryText;

		final BeanOutputConverter<OpenAiMoodResponse> outputConverter = new BeanOutputConverter<>(OpenAiMoodResponse.class);
		final OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(defaultModel);
		if ("json_object".equals(responseFormatType)) {
			optionsBuilder.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
		} else if ("json_schema".equals(responseFormatType)) {
			optionsBuilder.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA).jsonSchema(outputConverter.getJsonSchema()).build());
		}
		final OpenAiChatOptions chatOptions = optionsBuilder.build();
		final String contents = buildPrompt(sanitizedText);
		log.info("Sending classification request, entryLength={}, model={}", sanitizedText != null ? sanitizedText.length() : 0, defaultModel);
		final Prompt prompt = new Prompt(contents, chatOptions);
		final ChatResponse response = callProvider(prompt);
		final String responseText = extractResponseText(response);
		log.info("Received classification response, responseLength={}", responseText.length());
		try {
			final OpenAiMoodResponse openAiMoodResponse = outputConverter.convert(responseText);

			if (openAiMoodResponse == null) {
				throw buildFailureException(MoodClassificationFailureReason.INVALID_RESPONSE);
			}

			if (openAiMoodResponse.moodScore() == null) {
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

	private ChatResponse callProvider(Prompt prompt) {
		try {
			return openAiChatModel.call(prompt);
		}
		catch (OpenAIIoException exception) {
			if (isTimeoutFailure(exception)) {
				throw buildFailureException(MoodClassificationFailureReason.PROVIDER_TIMEOUT, exception);
			}
			throw buildFailureException(MoodClassificationFailureReason.PROVIDER_ERROR, exception);
		}
		catch (RuntimeException exception) {
			throw buildFailureException(MoodClassificationFailureReason.PROVIDER_ERROR, exception);
		}
	}

	private MoodClassificationFailedException buildFailureException(MoodClassificationFailureReason reason) {
		return new MoodClassificationFailedException(USER_SAFE_ERROR_MESSAGE, reason, PROVIDER_NAME, defaultModel);
	}

	private MoodClassificationFailedException buildFailureException(MoodClassificationFailureReason reason, Throwable cause) {
		return new MoodClassificationFailedException(USER_SAFE_ERROR_MESSAGE, reason, PROVIDER_NAME, defaultModel, cause);
	}

	private boolean isTimeoutFailure(Throwable throwable) {
		Throwable currentThrowable = throwable;

		while (currentThrowable != null) {
			if (currentThrowable instanceof InterruptedIOException || currentThrowable instanceof HttpTimeoutException) {
				return true;
			}

			final String message = currentThrowable.getMessage();

			if (message != null) {
				final String normalizedMessage = message.toLowerCase(Locale.ROOT);

				if (normalizedMessage.contains("timeout") || normalizedMessage.contains("timed out")) {
					return true;
				}
			}

			currentThrowable = currentThrowable.getCause();
		}

		return false;
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
			<entry>
			%s
			</entry>
			""".formatted(entryText);
	}

	private record OpenAiMoodResponse(MoodTag moodTag, Integer moodScore) {
	}
}
