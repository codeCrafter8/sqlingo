package com.codecrafter8.nl2sql;

import com.codecrafter8.nl2sql.dto.QueryRequest;
import com.codecrafter8.nl2sql.dto.QueryResponse;
import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import com.codecrafter8.nl2sql.metrics.ResearchMetrics;
import com.codecrafter8.nl2sql.metrics.ResearchMetrics.DifficultyStats;
import com.codecrafter8.nl2sql.metrics.ResearchMetrics.QueryMetrics;
import com.codecrafter8.nl2sql.service.QueryExecutionService;
import com.codecrafter8.nl2sql.service.SQLExecutionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class QueryEvaluationIT {

    @Autowired
    private QueryExecutionService queryExecutionService;

    @Autowired
    private SQLExecutionService sqlExecutionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("classpath:spider-hospital-pl.json")
    private Resource testDataResource;

    @Test
    void runFullEvaluation() throws IOException {
        List<TestCase> testCases = objectMapper.readValue(
                testDataResource.getInputStream(),
                new TypeReference<>() {
                });

        performWarmUp();

        runEvaluationForMode(SchemaContextMode.FULL_SCHEMA, testCases);
        runEvaluationForMode(SchemaContextMode.RAG, testCases);
    }

    private void runEvaluationForMode(SchemaContextMode mode, List<TestCase> testCases) {
        Map<String, DifficultyStats> statsMap = new LinkedHashMap<>();
        statsMap.put("Easy", new DifficultyStats());
        statsMap.put("Medium", new DifficultyStats());
        statsMap.put("Hard", new DifficultyStats());

        List<QueryMetrics> allMetrics = new ArrayList<>();

        System.out.println("\n" + "=".repeat(120));
        System.out.println("EWALUACJA SYSTEMU NL2SQL - " + mode);
        System.out.println("=".repeat(120) + "\n");

        for (TestCase test : testCases) {
            try {
                QueryRequest request = QueryRequest.builder()
                        .naturalLanguageQuery(test.getQuestion())
                        .explainSql(false)
                        .schemaContextMode(mode)
                        .build();

                long startTime = System.currentTimeMillis();
                QueryResponse response = queryExecutionService.executeQuery(request);
                long executionTime = System.currentTimeMillis() - startTime;

                List<Map<String, Object>> goldResults = sqlExecutionService.executeQuery(test.getGoldSql());

                double executionAccuracy = ResearchMetrics.calculateExecutionAccuracy(
                        (List<Map<String, Object>>) response.getResults(), goldResults);

                double exactMatch = ResearchMetrics.calculateExactMatch(
                        response.getGeneratedSql(), test.getGoldSql());

                double tableRecall = mode == SchemaContextMode.RAG
                        ? ResearchMetrics.calculateTableRecall(response.getSelectedTables(), test.getRequiredTables())
                        : 1.0;

                int promptTokens = response.getPromptTokens() != null ? response.getPromptTokens() : 0;
                int completionTokens = response.getCompletionTokens() != null ? response.getCompletionTokens() : 0;
                double cost = ResearchMetrics.calculateCost(promptTokens, completionTokens);

                QueryMetrics metrics = new QueryMetrics(
                        test.getId(),
                        test.getLevel(),
                        executionAccuracy,
                        exactMatch,
                        tableRecall,
                        promptTokens,
                        completionTokens,
                        cost,
                        executionTime
                );

                allMetrics.add(metrics);
                statsMap.get(test.getLevel()).addMetrics(metrics);
                System.out.println(metrics);

            } catch (Exception e) {
                log.error("Błąd podczas przetwarzania zapytania ID: {} [{}]", test.getId(), mode, e);
                System.err.printf("ID: %d [%-6s] [%s] | BLAD: %s\n",
                        test.getId(), test.getLevel(), mode, e.getMessage());
            }
        }

        printFinalSummary(mode, statsMap, allMetrics);
    }

    private void printFinalSummary(SchemaContextMode mode, Map<String, DifficultyStats> statsMap, List<QueryMetrics> allMetrics) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("PODSUMOWANIE METRYK BADAWCZYCH - " + mode);
        System.out.println("=".repeat(120));
        System.out.printf("%-10s | %-11s | %-11s | %-8s | %-10s | %-10s | %-11s | %-10s | %-8s\n",
                "POZIOM", "EX (ACC)", "EM (ACC)", "SR.TR", "SR. IN", "SR. OUT", "S. KOSZT", "S. CZAS", "PROBY");
        System.out.println("-".repeat(120));

        statsMap.forEach((level, stats) -> {
            System.out.printf("%-10s | %-10.2f%% | %-10.2f%% | %-8.2f | %-10.1f | %-10.1f | $%-10.6f | %-8.2fms | %-8d\n",
                    level,
                    stats.getAccuracyEx(),
                    stats.getAccuracyEm(),
                    stats.getAverageTableRecall(),
                    stats.getAvgInputTokens(),
                    stats.getAvgOutputTokens(),
                    stats.getAverageCost(),
                    stats.getAverageTimeMs(),
                    stats.total);
        });
        System.out.println("-".repeat(120));

        DifficultyStats totalStats = new DifficultyStats();
        statsMap.values().forEach(stats -> {
            totalStats.total += stats.total;
            totalStats.correctEx += stats.correctEx;
            totalStats.correctEm += stats.correctEm;
            totalStats.totalTableRecall += stats.totalTableRecall;
            totalStats.totalInputTokens += stats.totalInputTokens;
            totalStats.totalOutputTokens += stats.totalOutputTokens;
            totalStats.totalCost += stats.totalCost;
            totalStats.totalTimeMs += stats.totalTimeMs;
        });

        System.out.printf("%-10s | %-10.2f%% | %-10.2f%% | %-8.2f | %-10.1f | %-10.1f | $%-10.6f | %-8.2fms | %-8d\n",
                "RAZEM",
                totalStats.getAccuracyEx(),
                totalStats.getAccuracyEm(),
                totalStats.getAverageTableRecall(),
                totalStats.getAvgInputTokens(),
                totalStats.getAvgOutputTokens(),
                totalStats.getAverageCost(),
                totalStats.getAverageTimeMs(),
                totalStats.total);
        System.out.println("=".repeat(120) + "\n");

        // Statystyki tokenów i kosztów
        int totalInputTokens = allMetrics.stream().mapToInt(m -> m.inputTokens).sum();
        int totalOutputTokens = allMetrics.stream().mapToInt(m -> m.outputTokens).sum();
        int totalTokens = totalInputTokens + totalOutputTokens;
        double totalCost = allMetrics.stream().mapToDouble(m -> m.cost).sum();
        long totalTime = allMetrics.stream().mapToLong(m -> m.executionTimeMs).sum();

        System.out.println("PODSUMOWANIE ZASOBOW - " + mode + ":");
        System.out.printf("  - Lacznie tokenow input:   %,d\n", totalInputTokens);
        System.out.printf("  - Lacznie tokenow output:  %,d\n", totalOutputTokens);
        System.out.printf("  - Lacznie tokenow razem:   %,d\n", totalTokens);
        System.out.printf("  - Laczny koszt API:        $%.6f\n", totalCost);
        System.out.printf("  - Laczny czas wykonania:   %,dms (%.2f sekund)\n", totalTime, totalTime / 1000.0);
        System.out.printf("  - Sredni czas na zapytanie: %.2fms\n",
                allMetrics.isEmpty() ? 0 : totalTime / (double) allMetrics.size());
        System.out.println("=".repeat(120) + "\n");
    }

    @Data
    static class TestCase {
        private int id;
        private String level;
        private String question;
        private String goldSql;
        private List<String> requiredTables;
    }

    private void performWarmUp() {
        log.info("Rozpoczynanie fazy rozgrzewki (warm-up) systemu...");
        try {
            QueryRequest warmUpRequest = QueryRequest.builder()
                    .naturalLanguageQuery("Ilu jest lekarzy?") // Dowolne proste pytanie
                    .explainSql(false)
                    .schemaContextMode(SchemaContextMode.RAG)
                    .build();

            // Wykonujemy zapytanie, ale nie zapisujemy jego metryk do raportu
            queryExecutionService.executeQuery(warmUpRequest);

            log.info("Faza rozgrzewki zakończona pomyślnie. System gotowy do pomiarów.");
        } catch (Exception e) {
            log.warn("Ostrzeżenie: Błąd podczas rozgrzewki. Pierwsze wyniki czasowe mogą być zawyżone.", e);
        }
    }
}
