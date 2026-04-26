package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.dto.LlmResponse;
import com.codecrafter8.nl2sql.dto.QueryRequest;
import com.codecrafter8.nl2sql.dto.QueryResponse;
import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.service.llm.LLMException;
import com.codecrafter8.nl2sql.service.llm.LLMProvider;
import com.codecrafter8.nl2sql.service.llm.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for converting natural language queries to SQL
 * and executing them against the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

    private static final String SELF_CORRECT_SQL_USER_PROMPT = "prompts/openai/self-correct-sql-user.txt";

    @Value("${app.llm.self-correction-enabled:false}")
    private boolean selfCorrectionEnabled = false;

    private final QueryLogService queryLogService;
    private final SQLValidationService sqlValidationService;
    private final SQLExecutionService sqlExecutionService;
    private final ObjectProvider<LLMProvider> llmProvider;
    private final TableContextRetriever tableContextRetriever;
    private final PromptLoader promptLoader;

    /**
     * Execute a natural language query and return results
     */
    @Transactional
    public QueryResponse executeQuery(QueryRequest request) {
        log.info("Executing natural language query: {}", request.getNaturalLanguageQuery());
        long startTime = System.currentTimeMillis();

        QueryLog queryLog = queryLogService.initializeLog(request.getNaturalLanguageQuery());
        List<String> selectedTables = List.of();

        try {
            LLMProvider provider = getProviderOrThrow();
            TableContextRetriever.SchemaContextResult contextResult = tableContextRetriever.retrieveRelevantSchemaContext(
                    request.getNaturalLanguageQuery(),
                    request.getSchemaContextMode());

            String schemaContext = contextResult.schemaContext();
            selectedTables = contextResult.selectedTables();

            LlmResponse llmResponse = provider.generateSQL(request.getNaturalLanguageQuery(), schemaContext);
            String initialSql = llmResponse.sql();
            log.info("Generated SQL: {}", initialSql);
            queryLog.setGeneratedSql(initialSql);
            queryLog.setPromptTokens(llmResponse.promptTokens());
            queryLog.setCompletionTokens(llmResponse.completionTokens());

            validateSqlOrThrow(initialSql);

            List<Map<String, Object>> results = executeWithOptionalRetry(
                    initialSql, request, schemaContext, provider, queryLog);

            String explanation = request.isExplainSql()
                    ? provider.explainSQL(queryLog.getGeneratedSql())
                    : null;

            queryLogService.logSuccess(queryLog, queryLog.getGeneratedSql(), results, startTime);

            return buildSuccessResponse(queryLog, results, explanation, selectedTables);

        } catch (Exception e) {
            queryLogService.logError(queryLog, e, queryLog.getGeneratedSql(), startTime);
            return buildErrorResponse(queryLog, selectedTables);
        }
    }

    private List<Map<String, Object>> executeWithOptionalRetry(
            String sql,
            QueryRequest request,
            String schema,
            LLMProvider provider,
            QueryLog queryLog) {

        try {
            return sqlExecutionService.executeQuery(sql);
        } catch (SQLExecutionService.SQLExecutionException e) {
            if (!selfCorrectionEnabled) {
                log.warn("Self-correction is disabled. Returning original execution error: {}", e.getMessage());
                throw e;
            }

            log.warn("First execution failed, attempting self-correction. Error: {}", e.getMessage());

            String correctionPrompt = buildSelfCorrectionUserPrompt(
                    request.getNaturalLanguageQuery(), sql, e.getMessage());

            LlmResponse correctionResponse = provider.generateSQL(correctionPrompt, schema);
            String correctedSql = correctionResponse.sql();

            log.info("Otrzymano poprawiony SQL: {}", correctedSql);

            queryLog.setPromptTokens(queryLog.getPromptTokens() + correctionResponse.promptTokens());
            queryLog.setCompletionTokens(queryLog.getCompletionTokens() + correctionResponse.completionTokens());
            queryLog.setGeneratedSql(correctedSql);

            validateSqlOrThrow(correctedSql);

            return sqlExecutionService.executeQuery(correctedSql);
        }
    }

    private void validateSqlOrThrow(String sql) {
        if (!sqlValidationService.validateSQL(sql)) {
            throw new IllegalArgumentException("Generated SQL failed safety validation");
        }
        if (!sqlExecutionService.isReadOnlyQuery(sql)) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }
    }

    private LLMProvider getProviderOrThrow() {
        return llmProvider.getIfAvailable(() -> {
            throw new LLMException("No LLM provider configured");
        });
    }

    private String buildSelfCorrectionUserPrompt(String naturalLanguageQuery, String failedSql, String executionError) {
        String template = promptLoader.loadPrompt(SELF_CORRECT_SQL_USER_PROMPT);
        return template
                .replace("{{naturalLanguageQuery}}", naturalLanguageQuery)
                .replace("{{failedSql}}", failedSql)
                .replace("{{executionError}}", executionError);
    }

    private QueryResponse buildSuccessResponse(QueryLog queryLog, List<Map<String, Object>> results, String explanation, List<String> selectedTables) {
        return QueryResponse.builder()
                .id(queryLog.getId())
                .naturalLanguageQuery(queryLog.getNaturalLanguageQuery())
                .generatedSql(queryLog.getGeneratedSql())
                .sqlExplanation(explanation)
                .results(results)
                .rowCount(results != null ? results.size() : 0)
                .status(queryLog.getStatus().toString())
                .executionTimeMs(queryLog.getExecutionTimeMs())
                .promptTokens(queryLog.getPromptTokens())
                .completionTokens(queryLog.getCompletionTokens())
                .totalTokens(queryLog.getPromptTokens() != null && queryLog.getCompletionTokens() != null ?
                        queryLog.getPromptTokens() + queryLog.getCompletionTokens() : 0)
                .selectedTables(selectedTables)
                .build();
    }

    private QueryResponse buildErrorResponse(QueryLog queryLog, List<String> selectedTables) {
        return QueryResponse.builder()
                .id(queryLog.getId())
                .naturalLanguageQuery(queryLog.getNaturalLanguageQuery())
                .generatedSql(queryLog.getGeneratedSql())
                .status(queryLog.getStatus().toString())
                .error(queryLog.getError())
                .executionTimeMs(queryLog.getExecutionTimeMs())
                .selectedTables(selectedTables)
                .build();
    }
}
