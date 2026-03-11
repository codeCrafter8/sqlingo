package com.codecrafter8.nl2sql.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for validating SQL queries before execution
 */
@Slf4j
@Service
public class SQLValidationService {

    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "\\b(DROP|DELETE|TRUNCATE|ALTER|GRANT|REVOKE|CREATE|INSERT|UPDATE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VALID_SQL_PATTERN = Pattern.compile(
            "^\\s*(SELECT|WITH)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Validate that the SQL query is safe to execute
     * - Only allows SELECT queries (read-only)
     * - Rejects queries with dangerous operations
     */
    public boolean validateSQL(String sqlQuery) {
        log.debug("Validating SQL query");

        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            log.warn("SQL query is empty");
            return false;
        }

        // Check if query starts with SELECT or WITH (CTE)
        if (!VALID_SQL_PATTERN.matcher(sqlQuery).find()) {
            log.warn("SQL query does not start with SELECT or WITH");
            return false;
        }

        // Check for dangerous keywords (outside SELECT...FROM...WHERE context)
        if (DANGEROUS_KEYWORDS.matcher(sqlQuery).find()) {
            log.warn("SQL query contains dangerous keywords");
            return false;
        }

        // Check for SQL injection patterns
        if (containsSuspiciousPatterns(sqlQuery)) {
            log.warn("SQL query contains suspicious patterns");
            return false;
        }

        return true;
    }

    /**
     * Check for common SQL injection patterns
     */
    private boolean containsSuspiciousPatterns(String sqlQuery) {
        String[] suspiciousPatterns = {
                "--;",           // SQL comment
                "/*",            // Multi-line comment start
                "*/",            // Multi-line comment end
                "xp_",           // Extended stored procedures
                "sp_",           // System stored procedures
                "exec",          // Dynamic execution
                "execute"        // Dynamic execution
        };

        String lowerQuery = sqlQuery.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (lowerQuery.contains(pattern)) {
                return true;
            }
        }

        return false;
    }
}
