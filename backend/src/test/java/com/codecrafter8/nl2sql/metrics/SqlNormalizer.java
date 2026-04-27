package com.codecrafter8.nl2sql.metrics;

import java.util.Set;

/**
 * Narzedzie do normalizacji SQL na potrzeby porownan EM.
 */
public final class SqlNormalizer {

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "EXISTS",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS",
            "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET",
            "UNION", "INTERSECT", "EXCEPT", "ALL", "DISTINCT", "AS",
            "CASE", "WHEN", "THEN", "ELSE", "END", "IS", "NULL",
            "BETWEEN", "LIKE", "ASC", "DESC", "WITH", "OVER", "PARTITION",
            "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE", "NULLIF",
            "INSERT", "UPDATE", "DELETE", "SET", "VALUES", "INTO"
    );

    private SqlNormalizer() {
    }

    public static String normalize(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }

        String s = sql.trim().toLowerCase();
        s = s.replaceAll(";$", "");
        s = normalizeWhitespace(s);
        s = uppercaseKeywords(s);
        s = normalizeQuotes(s);
        s = normalizeTableAliases(s);
        s = normalizeWhitespace(s);
        return s;
    }

    private static String normalizeWhitespace(String sql) {
        return sql
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s*\\(\\s*", "(")
                .replaceAll("\\s*\\)\\s*", ")")
                .trim();
    }

    private static String uppercaseKeywords(String sql) {
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    token.append(c);
                } else {
                    if (token.length() > 0) {
                        String word = token.toString();
                        result.append(SQL_KEYWORDS.contains(word.toUpperCase()) ? word.toUpperCase() : word);
                        token.setLength(0);
                    }
                    result.append(c);
                }
            } else {
                if (token.length() > 0) {
                    result.append(token);
                    token.setLength(0);
                }
                result.append(c);
            }
        }

        if (token.length() > 0) {
            String word = token.toString();
            result.append(SQL_KEYWORDS.contains(word.toUpperCase()) ? word.toUpperCase() : word);
        }
        return result.toString();
    }

    private static String normalizeQuotes(String sql) {
        String normalized = sql.replaceAll("`([^`]+)`", "$1");
        return normalized.replaceAll("\"([\\w.]+)\"", "$1");
    }

    private static String normalizeTableAliases(String sql) {
        return sql.replaceAll(
                "(?i)((?:FROM|JOIN)\\s+\\w+)\\s+AS\\s+(\\w+)",
                "$1 $2"
        );
    }
}

