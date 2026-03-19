package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.dto.QueryRequest;
import com.codecrafter8.nl2sql.dto.QueryResponse;
import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.service.llm.LLMException;
import com.codecrafter8.nl2sql.service.llm.LLMProvider;
import com.codecrafter8.nl2sql.service.llm.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for converting natural language queries to SQL
 * and executing them against the database.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class QueryExecutionService {

    private static final String SELF_CORRECT_SQL_USER_PROMPT = "prompts/openai/self-correct-sql-user.txt";
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

        try {
            LLMProvider provider = getProviderOrThrow();
            String schemaContext = tableContextRetriever.retrieveRelevantSchemaContext(request.getNaturalLanguageQuery());

            String sql = provider.generateSQL(request.getNaturalLanguageQuery(), schemaContext);
            log.info("Generated SQL: {}", sql);
            queryLog.setGeneratedSql(sql);

            validateSqlOrThrow(sql);

            List<Map<String, Object>> results = executeWithRetry(sql, request, schemaContext, provider, queryLog);

            String explanation = request.isExplainSql() ? provider.explainSQL(queryLog.getGeneratedSql()) : null;

            queryLogService.logSuccess(queryLog, queryLog.getGeneratedSql(), results, startTime);

            return buildSuccessResponse(queryLog, results, explanation);

        } catch (Exception e) {
            queryLogService.logError(queryLog, e, queryLog.getGeneratedSql(), startTime);
            return buildErrorResponse(queryLog);
        }
    }

    private List<Map<String, Object>> executeWithRetry(
            String sql,
            QueryRequest request,
            String schema,
            LLMProvider provider,
            QueryLog queryLog) {

        try {
            return sqlExecutionService.executeQuery(sql);
        } catch (SQLExecutionService.SQLExecutionException e) {
            log.warn("First execution failed, attempting self-correction. Error: {}", e.getMessage());

            String correctionPrompt = buildSelfCorrectionUserPrompt(request.getNaturalLanguageQuery(), sql, e.getMessage());
            String correctedSql = provider.generateSQL(correctionPrompt, schema);

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

    private QueryResponse buildSuccessResponse(QueryLog queryLog, List<Map<String, Object>> results, String explanation) {
        return QueryResponse.builder()
                .id(queryLog.getId())
                .naturalLanguageQuery(queryLog.getNaturalLanguageQuery())
                .generatedSql(queryLog.getGeneratedSql())
                .sqlExplanation(explanation)
                .results(results)
                .rowCount(results != null ? results.size() : 0)
                .status(queryLog.getStatus().toString())
                .executionTimeMs(queryLog.getExecutionTimeMs())
                .build();
    }

    private QueryResponse buildErrorResponse(QueryLog queryLog) {
        return QueryResponse.builder()
                .id(queryLog.getId())
                .naturalLanguageQuery(queryLog.getNaturalLanguageQuery())
                .generatedSql(queryLog.getGeneratedSql())
                .status(queryLog.getStatus().toString())
                .error(queryLog.getError())
                .executionTimeMs(queryLog.getExecutionTimeMs())
                .build();
    }
}
