package com.codecrafter8.nl2sql.service.llm;

/**
 * Interface for LLM providers (OpenAI, etc.)
 */
public interface LLMProvider {

    /**
     * Generate SQL from natural language query
     *
     * @param naturalLanguageQuery The user's query in natural language
     * @param schemaContext        The database schema context
     * @return The generated SQL query
     */
    String generateSQL(String naturalLanguageQuery, String schemaContext) throws LLMException;

    /**
     * Generate an explanation for a given SQL query
     *
     * @param sqlQuery The SQL query to explain
     * @return An explanation of what the query does
     */
    String explainSQL(String sqlQuery) throws LLMException;

    /**
     * Validate if a query is safe to execute
     *
     * @param sqlQuery The SQL query to validate
     * @return true if the query is safe to execute
     */
    boolean validateQuery(String sqlQuery) throws LLMException;
}
