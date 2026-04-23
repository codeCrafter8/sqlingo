package com.codecrafter8.nl2sql.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;

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
        public String level;                // Poziom trudności (Easy/Medium/Hard)
        public double executionAccuracy;    // EX: Dokładność wykonania (0.0-1.0)
        public double exactMatch;           // EM: Dokładne dopasowanie SQL (0.0-1.0)
        public int inputTokens;
        public int outputTokens;                  // Liczba tokenów w pytaniu
        public double cost;                 // Szacunkowy koszt API w USD
        public long executionTimeMs;        // Czas wykonania w milisekundach

        @Override
        public String toString() {
            return String.format(
                    "ID:%d [%-10s] | EX:%.2f | EM:%.2f | In:%d Out:%d | Cost:$%.6f | Time:%dms",
                    id, level, executionAccuracy, exactMatch,
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
        public int totalInputTokens = 0;
        public int totalOutputTokens = 0;
        public double totalCost = 0.0;
        public long totalTimeMs = 0;

        public void addMetrics(QueryMetrics metrics) {
            total++;
            if (metrics.executionAccuracy >= 0.99) correctEx++;
            if (metrics.exactMatch >= 0.99) correctEm++;
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
    }
}
