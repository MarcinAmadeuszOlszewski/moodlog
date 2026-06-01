package com.amadeuszx.moodlog;

import java.util.Locale;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MoodClassifierConfiguration {

	@Bean
	MoodClassifier moodClassifier(
		@Value("${moodlog.ai.enabled:false}") boolean aiEnabled,
		@Value("${moodlog.ai.provider:stub}") String provider,
		@Value("${moodlog.ai.default-model:gpt-4o-mini}") String defaultModel,
		@Value("${spring.ai.openai.api-key:disabled}") String openAiApiKey,
		ObjectProvider<OpenAiChatModel> openAiChatModelProvider
	) {
		final String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);

		if (!"stub".equals(normalizedProvider) && !"openai".equals(normalizedProvider)) {
			throw new IllegalStateException("Unsupported mood classifier provider: " + provider);
		}
		if (aiEnabled && "openai".equals(normalizedProvider)) {
			if ("disabled".equals(openAiApiKey)) {
				throw new IllegalStateException("OpenAI API key is required when moodlog.ai.provider=openai.");
			}

			final OpenAiChatModel openAiChatModel = openAiChatModelProvider.getIfAvailable();

			if (openAiChatModel == null) {
				throw new IllegalStateException(
					"OpenAI chat model is unavailable. Set spring.ai.model.chat=openai and configure OpenAI credentials."
				);
			}

			return new OpenAiMoodClassifier(openAiChatModel, defaultModel);
		}

		return new StubMoodClassifier();
	}
}
