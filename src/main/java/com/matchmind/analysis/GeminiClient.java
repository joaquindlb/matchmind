package com.matchmind.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// Talks to the Google Gemini API.
@Component
public class GeminiClient {

    private final RestClient rest;
    private final String model;

    public GeminiClient(RestClient.Builder builder,
                        @Value("${gemini.api.base-url}") String baseUrl,
                        @Value("${gemini.api.model}") String model,
                        @Value("${gemini.api.key}") String apiKey) {
        this.model = model;
        this.rest = builder
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    public record GeminiResponse(List<Candidate> candidates) {}
    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}

    public String generate(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt)))));

        GeminiResponse response = rest.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()
                || response.candidates().get(0).content() == null
                || response.candidates().get(0).content().parts() == null) {
            throw new IllegalStateException("Gemini returned an empty response");
        }

        String text = response.candidates().get(0).content().parts().stream()
                .map(Part::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .trim();

        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini returned no text");
        }
        return text;
    }
}