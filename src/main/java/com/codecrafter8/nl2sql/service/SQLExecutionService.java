package com.codecrafter8.nl2sql.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for executing SQL queries against the database
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SQLExecutionService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.sql-generation.max-rows-limit:1000}")
    private int maxRowsLimit;

    /**
     * Execute a SELECT query and return results as a list of maps
     *
     * @param sql The SQL query to execute
     * @return List of result rows, where each row is a map of column name to value
     * @throws SQLExecutionException if the query execution fails
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        log.debug("Executing SQL query: {}", sql);

        try {
            // Add row limit if not present in the query
            String limitedSql = ensureRowLimit(sql);

            log.debug("Executing limited SQL: {}", limitedSql);

            // Execute query and return results
            List<Map<String, Object>> results = jdbcTemplate.queryForList(limitedSql);

            log.info("Query executed successfully. Returned {} rows", results.size());

            return results;

        } catch (Exception e) {
            log.error("Error executing SQL query: {}", sql, e);
            throw new SQLExecutionException("Failed to execute SQL query: " + e.getMessage(), e);
        }
    }

    /**
     * Ensure the query has a row limit to prevent returning too many rows
     */
    private String ensureRowLimit(String sql) {
        String normalizedSql = sql.trim().toUpperCase();

        // Check if query already has a LIMIT clause
        if (normalizedSql.contains("LIMIT")) {
            return sql;
        }

        // Add LIMIT clause
        return sql.trim() + " LIMIT " + maxRowsLimit;
    }

    /**
     * Check if a query is a read-only SELECT query
     */
    public boolean isReadOnlyQuery(String sql) {
        String normalizedSql = sql.trim().toUpperCase();
        return normalizedSql.startsWith("SELECT");
    }

    /**
     * Custom exception for SQL execution errors
     */
    public static class SQLExecutionException extends RuntimeException {
        public SQLExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
