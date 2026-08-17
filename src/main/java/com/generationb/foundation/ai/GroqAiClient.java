package com.generationb.foundation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Groq's OpenAI-compatible chat-completions endpoint. Free tier, which is why it was chosen.
 *
 * <p>The request body is built with Jackson rather than String.format — brand guidelines and
 * creator names go into these prompts, and a quote in a name used to break the JSON.
 */
@Slf4j
@Service
public class GroqAiClient implements AiClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${ai.timeout-seconds:20}")
    private int timeoutSeconds;

    @Value("${ai.max-tokens:1200}")
    private int maxTokens;

    public GroqAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Optional<String> generate(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            log.debug("No AI key configured; the caller will use its own draft");
            return Optional.empty();
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return Optional.empty();
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "temperature", 0.7,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    systemPrompt == null ? "You are a helpful assistant." : systemPrompt),
                            Map.of("role", "user", "content", userPrompt))));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                // The body echoes the prompt back, which can contain personal data — status only.
                log.warn("The AI provider returned {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");

            if (content.isMissingNode() || content.asText().isBlank()) {
                log.warn("The AI provider returned an empty completion");
                return Optional.empty();
            }
            return Optional.of(content.asText().trim());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("The AI provider call failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
