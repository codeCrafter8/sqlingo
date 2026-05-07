package com.codecrafter8.nl2sql.evaluation.support;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public abstract class AbstractResearchEvaluationIT {

    private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String DEFAULT_WARM_UP_QUERY = "Ilu jest lekarzy?";

    private String runId;

    @Autowired
    protected QueryExecutionService queryExecutionService;

    @Autowired
    protected SQLExecutionService sqlExecutionService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Value("classpath:spider-hospital-pl.json")
    protected Resource testDataResource;

    protected void runCampaign(String reportPrefix, String campaignTitle, List<SchemaContextMode> modes) throws IOException {
        runId = LocalDateTime.now().format(RUN_ID_FORMATTER);

        List<TestCase> testCases = loadTestCases();
        performWarmUp();

        System.out.println("\n" + "=".repeat(120));
        System.out.println(campaignTitle);
        System.out.printf("MODEL: %s | PROMPT: %s%n",
                resolvedModel(),
                resolvedSystemPrompt());
        System.out.println("=".repeat(120) + "\n");

        for (SchemaContextMode mode : modes) {
            runEvaluationForMode(reportPrefix, campaignTitle, mode, testCases);
        }
    }

    protected List<TestCase> loadTestCases() throws IOException {
        try (var inputStream = testDataResource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<TestCase>>() {
            });
        }
    }

    protected void runEvaluationForMode(String reportPrefix,
                                        String campaignTitle,
                                        SchemaContextMode mode,
                                        List<TestCase> testCases) {
        Map<String, DifficultyStats> statsMap = new LinkedHashMap<>();
        statsMap.put("Easy", new DifficultyStats());
        statsMap.put("Medium", new DifficultyStats());
        statsMap.put("Hard", new DifficultyStats());

        List<QueryMetrics> allMetrics = new ArrayList<>();

        System.out.println("\n" + "=".repeat(120));
        System.out.println(campaignTitle + " - " + mode);
        System.out.println("=".repeat(120) + "\n");

        for (TestCase test : testCases) {
            long startTime = System.currentTimeMillis();
            QueryResponse response = null;

            try {
                QueryRequest request = QueryRequest.builder()
                        .naturalLanguageQuery(test.getQuestion())
                        .explainSql(false)
                        .schemaContextMode(mode)
                        .build();

                response = queryExecutionService.executeQuery(request);
                long executionTime = System.currentTimeMillis() - startTime;

                if (isFailureResponse(response)) {
                    QueryMetrics metrics = buildFailureMetrics(test, response, executionTime,
                            buildResponseFailure(response));
                    allMetrics.add(metrics);
                    statsMap.computeIfAbsent(test.getLevel(), level -> new DifficultyStats()).addMetrics(metrics);
                    System.out.println(metrics);
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> generatedResults = (List<Map<String, Object>>) response.getResults();
                List<Map<String, Object>> goldResults = sqlExecutionService.executeQuery(test.getGoldSql());

                double executionAccuracy = ResearchMetrics.calculateExecutionAccuracy(generatedResults, goldResults);
                double exactMatch = ResearchMetrics.calculateExactMatch(response.getGeneratedSql(), test.getGoldSql());
                double tableRecall = mode == SchemaContextMode.RAG
                        ? ResearchMetrics.calculateTableRecall(response.getSelectedTables(), test.getRequiredTables())
                        : 1.0;

                int promptTokens = safeTokenCount(response.getPromptTokens());
                int completionTokens = safeTokenCount(response.getCompletionTokens());
                double cost = ResearchMetrics.calculateCost(promptTokens, completionTokens);

                QueryMetrics metrics = QueryMetrics.success(
                        test.getId(),
                        test.getQuestion(),
                        test.getLevel(),
                        executionAccuracy,
                        exactMatch,
                        tableRecall,
                        response.getSelectedTables(),
                        test.getRequiredTables(),
                        promptTokens,
                        completionTokens,
                        cost,
                        executionTime,
                        response.getGeneratedSql(),
                        response.getAnalysis(),
                        test.getGoldSql()
                );

                allMetrics.add(metrics);
                statsMap.computeIfAbsent(test.getLevel(), level -> new DifficultyStats()).addMetrics(metrics);
                System.out.println(metrics);

            } catch (Exception e) {
                long executionTime = System.currentTimeMillis() - startTime;
                int promptTokens = response != null ? safeTokenCount(response.getPromptTokens()) : 0;
                int completionTokens = response != null ? safeTokenCount(response.getCompletionTokens()) : 0;
                double cost = ResearchMetrics.calculateCost(promptTokens, completionTokens);

                QueryMetrics metrics = QueryMetrics.failure(
                        test.getId(),
                        test.getQuestion(),
                        test.getLevel(),
                        promptTokens,
                        completionTokens,
                        cost,
                        executionTime,
                        test.getGoldSql(),
                        response != null ? response.getGeneratedSql() : null,
                        response != null ? response.getAnalysis() : null,
                        response != null ? response.getSelectedTables() : List.of(),
                        test.getRequiredTables(),
                        e
                );

                allMetrics.add(metrics);
                statsMap.computeIfAbsent(test.getLevel(), level -> new DifficultyStats()).addMetrics(metrics);
                log.error("Błąd podczas przetwarzania zapytania ID: {} [{}]", test.getId(), mode, e);
                System.err.printf("ID: %d [%-6s] [%s] | BLAD: %s%n",
                        test.getId(), test.getLevel(), mode, e.getMessage());
                System.out.println(metrics);
            }
        }

        printFinalSummary(campaignTitle, mode, statsMap, allMetrics);
        saveResultsToFile(reportPrefix, campaignTitle, mode, statsMap, allMetrics);
    }

    protected void printFinalSummary(String campaignTitle,
                                     SchemaContextMode mode,
                                     Map<String, DifficultyStats> statsMap,
                                     List<QueryMetrics> allMetrics) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println(campaignTitle + " - PODSUMOWANIE METRYK BADAWCZYCH - " + mode);
        System.out.println("=".repeat(120));
        System.out.printf("%-10s | %-11s | %-11s | %-8s | %-10s | %-10s | %-11s | %-10s | %-8s%n",
                "POZIOM", "EX (ACC)", "EM (ACC)", "SR.TR", "SR. IN", "SR. OUT", "S. KOSZT", "S. CZAS", "PROBY");
        System.out.println("-".repeat(120));

        statsMap.forEach((level, stats) -> {
            System.out.printf("%-10s | %-10.2f%% | %-10.2f%% | %-8.2f | %-10.1f | %-10.1f | $%-10.6f | %-8.2fms | %-8d%n",
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

        System.out.printf("%-10s | %-10.2f%% | %-10.2f%% | %-8.2f | %-10.1f | %-10.1f | $%-10.6f | %-8.2fms | %-8d%n",
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

        int totalInputTokens = allMetrics.stream().mapToInt(m -> m.inputTokens).sum();
        int totalOutputTokens = allMetrics.stream().mapToInt(m -> m.outputTokens).sum();
        int totalTokens = totalInputTokens + totalOutputTokens;
        double totalCost = allMetrics.stream().mapToDouble(m -> m.cost).sum();
        long totalTime = allMetrics.stream().mapToLong(m -> m.executionTimeMs).sum();

        System.out.println("PODSUMOWANIE ZASOBOW - " + campaignTitle + " - " + mode + ":");
        System.out.printf("  - Lacznie tokenow input:   %,d%n", totalInputTokens);
        System.out.printf("  - Lacznie tokenow output:  %,d%n", totalOutputTokens);
        System.out.printf("  - Lacznie tokenow razem:   %,d%n", totalTokens);
        System.out.printf("  - Laczny koszt API:        $%.6f%n", totalCost);
        System.out.printf("  - Laczny czas wykonania:   %,dms (%.2f sekund)%n", totalTime, totalTime / 1000.0);
        System.out.printf("  - Sredni czas na zapytanie: %.2fms%n",
                allMetrics.isEmpty() ? 0 : totalTime / (double) allMetrics.size());
        System.out.println("=".repeat(120) + "\n");
    }

    protected void saveResultsToFile(String reportPrefix,
                                     String campaignTitle,
                                     SchemaContextMode mode,
                                     Map<String, DifficultyStats> statsMap,
                                     List<QueryMetrics> allMetrics) {
        try {
            Path outputDir = Paths.get("research_results");
            Files.createDirectories(outputDir);

            String currentRunId = runId != null ? runId : LocalDateTime.now().format(RUN_ID_FORMATTER);
            String filePrefix = reportPrefix + "-" + currentRunId + "-" + mode.name().toLowerCase(Locale.ROOT);
            Path csvPath = outputDir.resolve(filePrefix + "-metrics.csv");
            Path jsonPath = outputDir.resolve(filePrefix + "-summary.json");

            writeMetricsCsv(csvPath, allMetrics);
            writeSummaryJson(jsonPath, reportPrefix, campaignTitle, mode, statsMap, allMetrics, currentRunId);

            log.info("Wyniki ewaluacji [{} / {}] zapisane do: {} i {}",
                    campaignTitle,
                    mode,
                    csvPath.toAbsolutePath(),
                    jsonPath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Nie udalo sie zapisac wynikow ewaluacji [{} / {}] do pliku.", campaignTitle, mode, e);
        }
    }

    protected void writeMetricsCsv(Path csvPath, List<QueryMetrics> allMetrics) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("id,question,level,status,execution_accuracy,execution_accuracy_pct,exact_match,exact_match_pct,table_recall,selected_tables,required_tables,input_tokens,output_tokens,cost_usd,execution_time_ms,generated_sql,analysis,gold_sql,error_type,error_message,error_details")
                .append(System.lineSeparator());

        for (QueryMetrics metric : allMetrics) {
            csv.append(metric.getId()).append(',')
                    .append(escapeCsv(metric.getQuestion())).append(',')
                    .append(escapeCsv(metric.getLevel())).append(',')
                    .append(escapeCsv(metric.getStatus())).append(',')
                    .append(String.format(Locale.US, "%.2f", metric.getExecutionAccuracy())).append(',')
                    .append(String.format(Locale.US, "%.2f", metric.getExecutionAccuracyPercent())).append(',')
                    .append(String.format(Locale.US, "%.2f", metric.getExactMatch())).append(',')
                    .append(String.format(Locale.US, "%.2f", metric.getExactMatchPercent())).append(',')
                    .append(String.format(Locale.US, "%.2f", metric.getTableRecall())).append(',')
                    .append(escapeCsv(joinTables(metric.getSelectedTables()))).append(',')
                    .append(escapeCsv(joinTables(metric.getRequiredTables()))).append(',')
                    .append(metric.getInputTokens()).append(',')
                    .append(metric.getOutputTokens()).append(',')
                    .append(String.format(Locale.US, "%.6f", metric.getCost())).append(',')
                    .append(metric.getExecutionTimeMs()).append(',')
                    .append(escapeCsv(metric.getGeneratedSql())).append(',')
                    .append(escapeCsv(metric.getAnalysis())).append(',')
                    .append(escapeCsv(metric.getGoldSql())).append(',')
                    .append(escapeCsv(metric.getErrorType())).append(',')
                    .append(escapeCsv(metric.getErrorMessage())).append(',')
                    .append(System.lineSeparator());
        }

        Files.writeString(csvPath, csv.toString());
    }

    protected void writeSummaryJson(Path jsonPath,
                                    String reportPrefix,
                                    String campaignTitle,
                                    SchemaContextMode mode,
                                    Map<String, DifficultyStats> statsMap,
                                    List<QueryMetrics> allMetrics,
                                    String currentRunId) throws IOException {
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

        long successCount = allMetrics.stream().filter(metric -> !metric.isFailure()).count();
        long failureCount = allMetrics.size() - successCount;

        Map<String, Object> totalsSummary = new LinkedHashMap<>();
        totalsSummary.put("total", totalStats.total);
        totalsSummary.put("correctEx", totalStats.correctEx);
        totalsSummary.put("correctEm", totalStats.correctEm);
        totalsSummary.put("totalTableRecall", totalStats.totalTableRecall);
        totalsSummary.put("totalInputTokens", totalStats.totalInputTokens);
        totalsSummary.put("totalOutputTokens", totalStats.totalOutputTokens);
        totalsSummary.put("totalCost", totalStats.totalCost);
        totalsSummary.put("totalTimeMs", totalStats.totalTimeMs);
        totalsSummary.put("averageTableRecall", totalStats.getAverageTableRecall());
        totalsSummary.put("accuracyEx", totalStats.getAccuracyEx());
        totalsSummary.put("accuracyExPercent", totalStats.getAccuracyEx());
        totalsSummary.put("accuracyEm", totalStats.getAccuracyEm());
        totalsSummary.put("accuracyEmPercent", totalStats.getAccuracyEm());
        totalsSummary.put("averageCost", totalStats.getAverageCost());
        totalsSummary.put("averageTimeMs", totalStats.getAverageTimeMs());
        totalsSummary.put("avgInputTokens", totalStats.getAvgInputTokens());
        totalsSummary.put("avgOutputTokens", totalStats.getAvgOutputTokens());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", currentRunId);
        summary.put("reportPrefix", reportPrefix);
        summary.put("campaignTitle", campaignTitle);
        summary.put("mode", mode.name());
        summary.put("queryCount", allMetrics.size());
        summary.put("successCount", successCount);
        summary.put("failureCount", failureCount);
        summary.put("systemPrompt", resolvedSystemPrompt());
        summary.put("model", resolvedModel());
        summary.put("levels", statsMap);
        summary.put("totals", totalsSummary);
        summary.put("results", allMetrics);
        summary.put("failedQueries", allMetrics.stream().filter(QueryMetrics::isFailure).toList());

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), summary);
    }

    protected String resolvedSystemPrompt() {
        return environment.getProperty("app.sql-generation.system-prompt", "prompts/openai/generate-sql-system-zero-shot.txt");
    }

    protected String resolvedModel() {
        return environment.getProperty("spring.ai.openai.chat.options.model", "gpt-4o-mini");
    }

    protected void performWarmUp() {
        log.info("Rozpoczynanie fazy rozgrzewki (warm-up) systemu...");
        try {
            QueryRequest warmUpRequest = QueryRequest.builder()
                    .naturalLanguageQuery(DEFAULT_WARM_UP_QUERY)
                    .explainSql(false)
                    .schemaContextMode(SchemaContextMode.RAG)
                    .build();

            queryExecutionService.executeQuery(warmUpRequest);

            log.info("Faza rozgrzewki zakończona pomyślnie. System gotowy do pomiarów.");
        } catch (Exception e) {
            log.warn("Ostrzeżenie: Błąd podczas rozgrzewki. Pierwsze wyniki czasowe mogą być zawyżone.", e);
        }
    }

    protected String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    protected String joinTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return "";
        }
        return String.join("; ", tables);
    }

    private boolean isFailureResponse(QueryResponse response) {
        return response == null || response.getError() != null;
    }

    private Throwable buildResponseFailure(QueryResponse response) {
        String errorMessage = response != null && response.getError() != null
                ? response.getError()
                : "Query execution failed";
        return new IllegalStateException(errorMessage);
    }

    private QueryMetrics buildFailureMetrics(TestCase test,
                                             QueryResponse response,
                                             long executionTime,
                                             Throwable error) {
        int promptTokens = response != null ? safeTokenCount(response.getPromptTokens()) : 0;
        int completionTokens = response != null ? safeTokenCount(response.getCompletionTokens()) : 0;
        double cost = ResearchMetrics.calculateCost(promptTokens, completionTokens);
        return QueryMetrics.failure(
                test.getId(),
                test.getQuestion(),
                test.getLevel(),
                promptTokens,
                completionTokens,
                cost,
                executionTime,
                test.getGoldSql(),
                response != null ? response.getGeneratedSql() : null,
                response != null ? response.getAnalysis() : null,
                response != null ? response.getSelectedTables() : List.of(),
                test.getRequiredTables(),
                error
        );
    }

    private int safeTokenCount(Integer value) {
        return value != null ? value : 0;
    }

    @Data
    protected static class TestCase {
        private int id;
        private String level;
        private String question;
        private String goldSql;
        private List<String> requiredTables;
    }
}
