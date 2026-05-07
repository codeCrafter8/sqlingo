package com.codecrafter8.nl2sql.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Metryki: Execution Accuracy (EX), Exact Match (EM), Tokeny, Koszt, Czas
 */
public class ResearchMetrics {

    // Stałe dla kosztów API
    private static final double INPUT_COST_PER_1M = 0.15;  // $0.15 / 1M input tokens
    private static final double OUTPUT_COST_PER_1M = 0.60;  // $0.60 / 1M output tokens

    /**
     * Oblicza Execution Accuracy (EX)
     * Porównuje rzeczywiste wyniki z oczekiwanymi wynikami zapytania
     *
     * @param actualResults   Wyniki zwrócone przez wygenerowane zapytanie
     * @param expectedResults Wyniki spodziewane (gold standard)
     * @return Dokładność od 0.0 do 1.0
     */
    public static double calculateExecutionAccuracy(
            List<Map<String, Object>> actualResults,
            List<Map<String, Object>> expectedResults) {

        if (expectedResults == null || expectedResults.isEmpty()) {
            return actualResults == null || actualResults.isEmpty() ? 1.0 : 0.0;
        }
        if (actualResults == null) {
            return 0.0;
        }

        // Porównanie na podstawie reprezentacji tekstowej
        List<String> actualSorted = actualResults.stream()
                .map(row -> new TreeMap<>(row).toString())
                .sorted()
                .toList();

        List<String> expectedSorted = expectedResults.stream()
                .map(row -> new TreeMap<>(row).toString())
                .sorted()
                .toList();

        return actualSorted.equals(expectedSorted) ? 1.0 : 0.0;
    }

    /**
     * Oblicza Exact Match (EM)
     * Porównuje wygenerowane SQL z gold standard SQL po normalizacji
     *
     * @param generatedSql SQL wygenerowany przez system
     * @param goldSql      Oczekiwane SQL (gold standard)
     * @return 1.0 jeśli dokładnie pasuje, 0.0 w przeciwnym razie
     */
    public static double calculateExactMatch(String generatedSql, String goldSql) {
        if (generatedSql == null || goldSql == null) return 0.0;

        String normalized1 = normalizeSql(generatedSql);
        String normalized2 = normalizeSql(goldSql);

        return normalized1.equals(normalized2) ? 1.0 : 0.0;
    }

    /**
     * Oblicza szacunkowy koszt API (OpenAI GPT-4)
     *
     * @return Szacunkowy koszt w USD
     */
    public static double calculateCost(int inputTokens, int outputTokens) {
        return (inputTokens / 1_000_000.0) * INPUT_COST_PER_1M
                + (outputTokens / 1_000_000.0) * OUTPUT_COST_PER_1M;
    }

    /**
     * Sprawdza czy RAG wybrał wszystkie tabele potrzebne do poprawnego SQL.
     * Kluczowa metryka dla rozdziału 8.2.
     *
     * @param selectedTables Tabele wybrane przez TableContextRetriever
     * @param requiredTables Tabele wymagane (z pola requiredTables w TestCase)
     * @return recall od 0.0 do 1.0
     */
    public static double calculateTableRecall(
            List<String> selectedTables,
            List<String> requiredTables) {

        if (requiredTables == null || requiredTables.isEmpty()) return 1.0;
        if (selectedTables == null || selectedTables.isEmpty()) return 0.0;

        Set<String> selectedLower = selectedTables.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        long matched = requiredTables.stream()
                .map(String::toLowerCase)
                .filter(selectedLower::contains)
                .count();

        return (double) matched / requiredTables.size();
    }

    public static String normalizeSql(String sql) {
        return SqlNormalizer.normalize(sql);
    }

    /**
     * Metryki dla pojedynczego zapytania
     */
    @Data
    @AllArgsConstructor
    public static class QueryMetrics {
        public int id;                      // ID zapytania
        public String question;             // Pytanie w języku naturalnym
        public String level;                // Poziom trudności (Easy/Medium/Hard)
        public String status;               // SUCCESS / FAILURE
        public double executionAccuracy;    // EX: Dokładność wykonania (0.0-1.0)
        public double exactMatch;           // EM: Dokładne dopasowanie SQL (0.0-1.0)
        public double tableRecall;          // Recall tabel wybranych przez RAG (0.0-1.0)
        public List<String> selectedTables; // Tabele odnalezione przez RAG / użyte do kontekstu
        public List<String> requiredTables; // Tabele, które powinny zostać odnalezione
        public int inputTokens;
        public int outputTokens;            // Liczba tokenów w odpowiedzi
        public double cost;                 // Szacunkowy koszt API w USD
        public long executionTimeMs;        // Czas wykonania w milisekundach
        public String generatedSql;         // SQL wygenerowany przez LLM
        public String analysis;             // Analiza / wyjaśnienie wygenerowane przez model
        public String goldSql;              // SQL referencyjny z pliku testowego
        public String errorType;            // Typ błędu (np. IllegalStateException)
        public String errorMessage;         // Krótka wiadomość błędu

        public static QueryMetrics success(int id,
                                           String question,
                                           String level,
                                           double executionAccuracy,
                                           double exactMatch,
                                           double tableRecall,
                                           List<String> selectedTables,
                                           List<String> requiredTables,
                                           int inputTokens,
                                           int outputTokens,
                                           double cost,
                                           long executionTimeMs,
                                           String generatedSql,
                                           String analysis,
                                           String goldSql) {
            return new QueryMetrics(
                    id,
                    question,
                    level,
                    "SUCCESS",
                    executionAccuracy,
                    exactMatch,
                    tableRecall,
                    selectedTables,
                    requiredTables,
                    inputTokens,
                    outputTokens,
                    cost,
                    executionTimeMs,
                    generatedSql,
                    analysis,
                    goldSql,
                    null,
                    null
            );
        }

        public static QueryMetrics failure(int id,
                                           String question,
                                           String level,
                                           long executionTimeMs,
                                           String goldSql,
                                           String generatedSql,
                                           String analysis,
                                           List<String> selectedTables,
                                           List<String> requiredTables,
                                           Throwable error) {
            return failure(id, question, level, 0, 0, 0.0, executionTimeMs, goldSql, generatedSql, analysis, selectedTables, requiredTables, error);
        }

        public static QueryMetrics failure(int id,
                                           String question,
                                           String level,
                                           int inputTokens,
                                           int outputTokens,
                                           double cost,
                                           long executionTimeMs,
                                           String goldSql,
                                           String generatedSql,
                                           String analysis,
                                           List<String> selectedTables,
                                           List<String> requiredTables,
                                           Throwable error) {
            return new QueryMetrics(
                    id,
                    question,
                    level,
                    "FAILURE",
                    0.0,
                    0.0,
                    0.0,
                    selectedTables,
                    requiredTables,
                    inputTokens,
                    outputTokens,
                    cost,
                    executionTimeMs,
                    generatedSql,
                    analysis,
                    goldSql,
                    error == null ? null : error.getClass().getSimpleName(),
                    error == null ? null : error.getMessage()
            );
        }

        public boolean isFailure() {
            return "FAILURE".equalsIgnoreCase(status);
        }

        public double getExecutionAccuracyPercent() {
            return executionAccuracy * 100;
        }

        public double getExactMatchPercent() {
            return exactMatch * 100;
        }

        private static String stackTraceToString(Throwable error) {
            StringWriter stringWriter = new StringWriter();
            error.printStackTrace(new PrintWriter(stringWriter));
            return stringWriter.toString();
        }

        @Override
        public String toString() {
            String tablesInfo = String.format("Tables found:%s | Tables expected:%s",
                    selectedTables,
                    requiredTables);

            if (isFailure()) {
                return String.format(
                        "ID:%d [%-10s] | %s | %s | Error:%s - %s | Time:%dms",
                        id,
                        level,
                        status,
                        tablesInfo,
                        errorType,
                        errorMessage,
                        executionTimeMs
                );
            }

            return String.format(
                    "ID:%d [%-10s] | %s | EX:%.2f%% | EM:%.2f%% | TR:%.2f | %s | In:%d Out:%d | Cost:$%.6f | Time:%dms",
                    id, level, status, getExecutionAccuracyPercent(), getExactMatchPercent(), tableRecall,
                    tablesInfo,
                    inputTokens, outputTokens, cost, executionTimeMs
            );
        }
    }

    /**
     * Statystyki dla poziomu trudności
     */
    @Data
    public static class DifficultyStats {
        public int total = 0;
        public int correctEx = 0;
        public int correctEm = 0;
        public double totalTableRecall = 0.0;
        public int totalInputTokens = 0;
        public int totalOutputTokens = 0;
        public double totalCost = 0.0;
        public long totalTimeMs = 0;

        public void addMetrics(QueryMetrics metrics) {
            total++;
            if (metrics.executionAccuracy >= 0.99) correctEx++;
            if (metrics.exactMatch >= 0.99) correctEm++;
            totalTableRecall += metrics.tableRecall;
            totalInputTokens += metrics.inputTokens;
            totalOutputTokens += metrics.outputTokens;
            totalCost += metrics.cost;
            totalTimeMs += metrics.executionTimeMs;
        }

        public double getAccuracyEx() {
            return total > 0 ? (double) correctEx / total * 100 : 0;
        }

        public double getAccuracyEm() {
            return total > 0 ? (double) correctEm / total * 100 : 0;
        }

        public double getAvgInputTokens() {
            return total > 0 ? (double) totalInputTokens / total : 0;
        }

        public double getAvgOutputTokens() {
            return total > 0 ? (double) totalOutputTokens / total : 0;
        }

        public double getAverageCost() {
            return total > 0 ? totalCost / total : 0;
        }

        public double getAverageTimeMs() {
            return total > 0 ? (double) totalTimeMs / total : 0;
        }

        public double getAverageTableRecall() {
            return total > 0 ? totalTableRecall / total : 0;
        }
    }
}
