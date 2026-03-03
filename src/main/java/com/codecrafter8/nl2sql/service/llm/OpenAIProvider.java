package com.codecrafter8.nl2sql.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * OpenAI LLM Provider implementation
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAIProvider implements LLMProvider {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    public static final String MISSING_API_KEY_MSG = "OpenAI API key is not configured.";
    @Value("${llm.openai.model:gpt-3.5-turbo}")
    private String model;
    @Value("${llm.openai.temperature:0.7}")
    private double temperature;
    @Value("${llm.openai.max-tokens:2000}")
    private int maxTokens;
    @Value("${llm.openai.api-key:}")
    private String apiKey;

    @Override
    public String generateSQL(String naturalLanguageQuery, String schemaContext) throws LLMException {
        log.debug("Generating SQL from natural language using OpenAI");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new LLMException(MISSING_API_KEY_MSG);
        }

        try {
            String prompt = buildSQLGenerationPrompt(naturalLanguageQuery, schemaContext);

            log.debug("Prepared prompt for OpenAI API");

            // TODO: Implement actual OpenAI API call
            // For now, return a dummy SQL query

            return generateDummySQL(naturalLanguageQuery);

        } catch (Exception e) {
            log.error("Error generating SQL from LLM", e);
            throw new LLMException("Failed to generate SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public String explainSQL(String sqlQuery) throws LLMException {
        log.debug("Generating SQL explanation using OpenAI");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new LLMException(MISSING_API_KEY_MSG);
        }

        try {
            String prompt = buildExplanationPrompt(sqlQuery);

            // TODO: Implement actual OpenAI API call

            return "To zapytanie pobiera dane z bazy danych. Rzeczywiste wyjaśnienie będzie wygenerowane przez LLM.";

        } catch (Exception e) {
            log.error("Error explaining SQL", e);
            throw new LLMException("Failed to explain SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateQuery(String sqlQuery) throws LLMException {
        log.debug("Validating SQL query using OpenAI");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new LLMException(MISSING_API_KEY_MSG);
        }

        try {
            // Basic validation - checking for dangerous operations
            String upperQuery = sqlQuery.toUpperCase();

            if (upperQuery.contains("DROP") || upperQuery.contains("DELETE") ||
                    upperQuery.contains("TRUNCATE") || upperQuery.contains("ALTER")) {
                log.warn("Query contains potentially dangerous operations");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Error validating SQL", e);
            throw new LLMException("Failed to validate SQL: " + e.getMessage(), e);
        }
    }

    private String buildSQLGenerationPrompt(String naturalLanguageQuery, String schemaContext) {
        return String.format("""
                Jesteś ekspertem SQL. Na podstawie następującego schematu bazy danych i naturalnego języka zapytania,
                wygeneruj poprawne zapytanie SQL.
                
                Schemat bazy danych:
                %s
                
                Zapytanie w języku naturalnym: %s
                
                Wygeneruj TYLKO zapytanie SQL bez żadnego wyjaśnienia. Zapytanie powinno być poprawne i bezpieczne do wykonania.
                Zacznij odpowiedź bezpośrednio od SELECT, FROM, WHERE lub innych słów kluczowych SQL.
                """, schemaContext, naturalLanguageQuery);
    }

    private String buildExplanationPrompt(String sqlQuery) {
        return String.format("""
                Wyjaśnij następujące zapytanie SQL prostym językiem:
                
                %s
                
                Podaj krótkie wyjaśnienie tego, co to zapytanie robi.
                """, sqlQuery);
    }

    private String generateDummySQL(String naturalLanguageQuery) {
        // Dummy implementation for testing
        log.info("Generating dummy SQL for: {}", naturalLanguageQuery);
        return "SELECT * FROM users LIMIT 10";
    }
}
