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

        String lower = response.toLowerCase();

        // Find SQL: marker case-insensitive
        int sqlIndex = indexOfIgnoreCase(response, "SQL:");
        if (sqlIndex != -1) {
            String sqlContent = response.substring(sqlIndex + 4).trim();
            // Remove markdown fences
            sqlContent = sqlContent.replace("```sql", "").replace("```", "").trim();
            log.debug("Extracted SQL from model response with Analysis and SQL sections");
            return sqlContent;
        }

        // No explicit SQL: section. If there is an Analiza/Analysis section followed by blank line + SQL, try to skip analysis.
        int analysisIndex = indexOfIgnoreCase(response, "Analiza:");
        if (analysisIndex == -1) {
            analysisIndex = indexOfIgnoreCase(response, "Analysis:");
        }
        if (analysisIndex != -1) {
            // Try to find the next double newline which might separate analysis and SQL
            int nextSectionIndex = response.indexOf("\n\n", analysisIndex);
            if (nextSectionIndex != -1) {
                String remaining = response.substring(nextSectionIndex).trim();
                if (!remaining.isEmpty()) {
                    // Clean up code fences if any
                    remaining = remaining.replace("```sql", "").replace("```", "").trim();
                    return remaining;
                }
            }
        }

        // If nothing matched, return original response
        return response.trim();
    }

    private int indexOfIgnoreCase(String text, String search) {
        if (text == null || search == null) return -1;
        return text.toLowerCase().indexOf(search.toLowerCase());
    }

    /**
     * Extract analysis (explanation) part from the model response, if present.
     * Supports Polish "Analiza:" and English "Analysis:" headers and attempts
     * to return text up to the next section (e.g. "SQL:") or a blank line.
     *
     * @param response Full model response
     * @return Extracted analysis or null when not found
     */
    private String extractAnalysisFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        String lower = response.toLowerCase();
        int analysisIndex = -1;
        String foundHeader = null;

        if ((analysisIndex = lower.indexOf("analiza:")) != -1) {
            foundHeader = "Analiza:";
        } else if ((analysisIndex = lower.indexOf("analysis:")) != -1) {
            foundHeader = "Analysis:";
        }

        if (analysisIndex != -1) {
            int start = analysisIndex + (foundHeader != null ? foundHeader.length() : 0);
            // Find next section header SQL: or double newline
            int sqlIndex = indexOfIgnoreCase(response.substring(start), "SQL:");
            int doubleNewline = response.indexOf("\n\n", start);

            int end = -1;
            if (sqlIndex != -1) {
                end = start + sqlIndex;
            } else if (doubleNewline != -1) {
                end = doubleNewline;
            }

            String analysis;
            if (end != -1) {
                analysis = response.substring(start, end).trim();
            } else {
                analysis = response.substring(start).trim();
            }

            if (analysis.isBlank()) {
                return null;
            }

            // Remove code fences if any
            analysis = analysis.replace("```", "").replace("```sql", "").trim();
            return analysis;
        }

        // If no explicit header, but response contains SQL: assume text before SQL: is analysis
        int sqlIndex = indexOfIgnoreCase(response, "SQL:");
        if (sqlIndex != -1) {
            String before = response.substring(0, sqlIndex).trim();
            // Heuristic: if 'before' is longer than a short phrase, treat it as analysis
            if (before.length() > 20) {
                before = before.replace("```", "").replace("```sql", "").trim();
                return before;
            }
        }

        // Nothing found
        return null;
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

            String extractedAnalysis = extractAnalysisFromResponse(rawContent);
            String extractedSql = extractSqlFromResponse(rawContent);

            return LlmResponse.builder()
                    .sql(extractedSql)
                    .promptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0)
                    .completionTokens(usage.getGenerationTokens() != null ? usage.getGenerationTokens().intValue() : 0)
                    .analysis(extractedAnalysis)
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
