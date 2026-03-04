package com.codecrafter8.nl2sql.service.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAIProvider implements LLMProvider {

    private final ChatClient chatClient;

    @Override
    public String generateSQL(String naturalLanguageQuery, String schemaContext) throws LLMException {
        log.debug("Generating SQL from natural language using OpenAI");

        try {
            return chatClient.prompt()
                    .system("""
                            Jesteś ekspertem SQL. Na podstawie następującego schematu bazy danych i naturalnego języka zapytania,
                            wygeneruj poprawne zapytanie SQL.
                            Zwracaj WYŁĄCZNIE czysty kod zapytania SQL, bez formatowania Markdown (np. ```sql) i bez żadnych wyjaśnień.
                            Zapytanie musi być poprawne i bezpieczne.""")
                    .user(u -> u.text("Schemat bazy danych:\n{schema}\n\nZapytanie: {query}")
                            .param("schema", schemaContext)
                            .param("query", naturalLanguageQuery))
                    .call()
                    .content();

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
                    .system("Jesteś analitykiem baz danych. Wyjaśniaj zapytania SQL krótko, zwięźle i bardzo prostym językiem zrozumiałym dla biznesu.")
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
                    .system("Jesteś rygorystycznym systemem bezpieczeństwa bazy danych. Twoim zadaniem jest ocenić, czy zapytanie służy WYŁĄCZNIE do odczytu danych (SELECT). Odpowiedz 'true' jeśli jest bezpieczne, lub 'false' jeśli zawiera próby modyfikacji struktury lub danych (np. DROP, DELETE, INSERT, UPDATE, ALTER).")
                    .user(sqlQuery)
                    .call()
                    .entity(Boolean.class);

        } catch (Exception e) {
            log.error("Error validating SQL", e);
            throw new LLMException("Failed to validate SQL: " + e.getMessage(), e);
        }
    }
}
