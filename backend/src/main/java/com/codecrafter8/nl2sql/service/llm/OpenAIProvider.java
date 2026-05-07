package com.codecrafter8.nl2sql.service.llm;

import com.codecrafter8.nl2sql.dto.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAIProvider implements LLMProvider {

    private static final String EXPLAIN_SQL_SYSTEM_PROMPT = "prompts/openai/explain-sql-system.txt";
    private static final String VALIDATE_QUERY_SYSTEM_PROMPT = "prompts/openai/validate-query-system.txt";

    private final ChatClient chatClient;
    private final PromptLoader promptLoader;

    @Value("${app.sql-generation.system-prompt:prompts/openai/generate-sql-system-zero-shot.txt}")
    private String generateSqlSystemPrompt;

    /**
     * Extracts SQL from model response that may contain "Analiza:" and "SQL:" sections.
     * If response contains both sections, returns only the SQL part.
     * Otherwise, returns the entire response.
     *
     * @param response The full response from the model
     * @return The extracted SQL query
     */
    private String extractSqlFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        // Check if response contains SQL: section
        int sqlIndex = response.indexOf("SQL:");
        if (sqlIndex != -1) {
            // Extract content after "SQL:" and clean it
            String sqlContent = response.substring(sqlIndex + 4).trim();

            // Remove any remaining markdown code block markers if present
            sqlContent = sqlContent.replace("```sql", "").replace("```", "").trim();

            log.debug("Extracted SQL from model response with Analysis and SQL sections");
            return sqlContent;
        }

        // If no "SQL:" section found, check if there's "Analiza:" section
        int analysisIndex = response.indexOf("Analiza:");
        if (analysisIndex != -1) {
            // Find the content between "Analiza:" and "SQL:" if exists
            int nextSectionIndex = response.indexOf("\n\n", analysisIndex);
            if (nextSectionIndex != -1) {
                // Return content after the next section
                String remaining = response.substring(nextSectionIndex).trim();
                if (!remaining.isEmpty()) {
                    return remaining;
                }
            }
        }

        // Return original response if no special sections found
        return response;
    }

    @Override
    public LlmResponse generateSQL(String naturalLanguageQuery, String schemaContext) throws LLMException {
        log.debug("Generating SQL from natural language using OpenAI");

        try {
            var response = chatClient.prompt()
                    .system(promptLoader.loadPrompt(generateSqlSystemPrompt))
                    .user(u -> u.text("Schemat bazy danych:\n{schema}\n\nZapytanie: {query}")
                            .param("schema", schemaContext)
                            .param("query", naturalLanguageQuery))
                    .call()
                    .chatResponse();

            var usage = response.getMetadata().getUsage();
            String rawContent = response.getResult().getOutput().getContent();
            String extractedSql = extractSqlFromResponse(rawContent);

            return LlmResponse.builder()
                    .sql(extractedSql)
                    .promptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0)
                    .completionTokens(usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0)
                    .build();

        } catch (Exception e) {
            log.error("Error generating SQL from LLM", e);
            throw new LLMException("Failed to generate SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public String explainSQL(String sqlQuery) throws LLMException {
        log.debug("Generating SQL explanation using OpenAI");

        try {
            return chatClient.prompt()
                    .system(promptLoader.loadPrompt(EXPLAIN_SQL_SYSTEM_PROMPT))
                    .user(u -> u.text("Wyjaśnij poniższe zapytanie:\n\n{sql}")
                            .param("sql", sqlQuery))
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("Error explaining SQL", e);
            throw new LLMException("Failed to explain SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateQuery(String sqlQuery) throws LLMException {
        log.debug("Validating SQL query using OpenAI");

        try {
            //todo
            String upperQuery = sqlQuery.toUpperCase();

            if (upperQuery.contains("DROP") || upperQuery.contains("DELETE") ||
                    upperQuery.contains("TRUNCATE") || upperQuery.contains("ALTER")) {
                log.warn("Query contains potentially dangerous operations");
                return false;
            }

            return chatClient.prompt()
                    .system(promptLoader.loadPrompt(VALIDATE_QUERY_SYSTEM_PROMPT))
                    .user(sqlQuery)
                    .call()
                    .entity(Boolean.class);

        } catch (Exception e) {
            log.error("Error validating SQL", e);
            throw new LLMException("Failed to validate SQL: " + e.getMessage(), e);
        }
    }
}
