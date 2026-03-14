package com.codecrafter8.nl2sql.service.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PromptLoader {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String loadPrompt(String classpathLocation) {
        return cache.computeIfAbsent(classpathLocation, this::readPrompt);
    }

    private String readPrompt(String classpathLocation) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathLocation);

        if (!resource.exists()) {
            throw new IllegalStateException("Prompt file not found: " + classpathLocation);
        }

        try (var inputStream = resource.getInputStream()) {
            String prompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();

            if (prompt.isBlank()) {
                throw new IllegalStateException("Prompt file is empty: " + classpathLocation);
            }

            return prompt;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt file: " + classpathLocation, e);
        }
    }
}
