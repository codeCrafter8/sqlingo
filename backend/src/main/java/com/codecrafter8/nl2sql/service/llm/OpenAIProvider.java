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

            return LlmResponse.builder()
                    .sql(response.getResult().getOutput().getContent())
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
