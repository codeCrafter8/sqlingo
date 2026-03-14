package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.dto.QueryRequest;
import com.codecrafter8.nl2sql.dto.QueryResponse;
import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.repository.QueryLogRepository;
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

    private final QueryLogRepository queryLogRepository;
    private final SQLValidationService sqlValidationService;
    private final SQLExecutionService sqlExecutionService;
    private final ObjectProvider<LLMProvider> llmProvider;
    private final TableContextRetriever tableContextRetriever;
    private final PromptLoader promptLoader;

    private static final String SELF_CORRECT_SQL_USER_PROMPT = "prompts/openai/self-correct-sql-user.txt";

    /**
     * Execute a natural language query and return results
     */
    @Transactional
    public QueryResponse executeQuery(QueryRequest request) {
        log.info("Executing natural language query: {}", request.getNaturalLanguageQuery());

        long startTime = System.currentTimeMillis();
        QueryLog queryLog = new QueryLog();
        queryLog.setNaturalLanguageQuery(request.getNaturalLanguageQuery());

        try {
            // Get LLM provider
            LLMProvider provider = llmProvider.getIfAvailable();
            if (provider == null) {
                throw new LLMException("No LLM provider configured");
            }

            // Get relevant schema context using RAG (Vector-based retrieval)
            String schemaContext = tableContextRetriever.retrieveRelevantSchemaContext(
                    request.getNaturalLanguageQuery()
            );

            // Generate SQL using LLM with focused schema context
            String generatedSQL = provider.generateSQL(
                    request.getNaturalLanguageQuery(),
                    schemaContext
            );

            log.info("Generated SQL: {}", generatedSQL);
            queryLog.setGeneratedSql(generatedSQL);

            // Validate SQL
            if (!sqlValidationService.validateSQL(generatedSQL)) {
                queryLog.setStatus(QueryLog.QueryStatus.INVALID_SQL);
                queryLog.setError("Generated SQL failed validation");
                queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                queryLogRepository.save(queryLog);

                return buildErrorResponse(queryLog);
            }

            // Check if query is read-only
            if (!sqlExecutionService.isReadOnlyQuery(generatedSQL)) {
                queryLog.setStatus(QueryLog.QueryStatus.INVALID_SQL);
                queryLog.setError("Only SELECT queries are allowed");
                queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                queryLogRepository.save(queryLog);

                return buildErrorResponse(queryLog);
            }

            List<Map<String, Object>> results;
            try {
                // First execution attempt
                results = sqlExecutionService.executeQuery(generatedSQL);
            } catch (SQLExecutionService.SQLExecutionException firstExecutionException) {
                log.warn("First SQL execution failed. Trying self-correction with LLM. Error: {}",
                        firstExecutionException.getMessage());

                String correctionUserPrompt = buildSelfCorrectionUserPrompt(
                        request.getNaturalLanguageQuery(),
                        generatedSQL,
                        firstExecutionException.getMessage()
                );

                String correctedSql = provider.generateSQL(correctionUserPrompt, schemaContext);

                log.info("Corrected SQL after self-correction: {}", correctedSql);
                queryLog.setGeneratedSql(correctedSql);

                if (!sqlValidationService.validateSQL(correctedSql)) {
                    queryLog.setStatus(QueryLog.QueryStatus.INVALID_SQL);
                    queryLog.setError("Self-corrected SQL failed validation");
                    queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                    queryLogRepository.save(queryLog);

                    return buildErrorResponse(queryLog);
                }

                if (!sqlExecutionService.isReadOnlyQuery(correctedSql)) {
                    queryLog.setStatus(QueryLog.QueryStatus.INVALID_SQL);
                    queryLog.setError("Self-corrected SQL is not read-only");
                    queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                    queryLogRepository.save(queryLog);

                    return buildErrorResponse(queryLog);
                }

                // Second execution attempt after LLM correction
                results = sqlExecutionService.executeQuery(correctedSql);
            }

            // Get SQL explanation if requested
            String explanation = null;
            if (request.isExplainSql()) {
                explanation = provider.explainSQL(queryLog.getGeneratedSql());
            }

            queryLog.setStatus(QueryLog.QueryStatus.SUCCESS);
            queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            queryLogRepository.save(queryLog);

            return buildSuccessResponse(queryLog, results, explanation);

        } catch (SQLExecutionService.SQLExecutionException e) {
            log.error("SQL execution error: {}", e.getMessage());
            queryLog.setStatus(QueryLog.QueryStatus.ERROR);
            queryLog.setError("SQL execution failed after self-correction: " + e.getMessage());
            queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            queryLogRepository.save(queryLog);

            return buildErrorResponse(queryLog);
        } catch (LLMException e) {
            log.error("LLM error: {}", e.getMessage());
            queryLog.setStatus(QueryLog.QueryStatus.ERROR);
            queryLog.setError(e.getMessage());
            queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            queryLogRepository.save(queryLog);

            return buildErrorResponse(queryLog);
        } catch (Exception e) {
            log.error("Unexpected error during query execution", e);
            queryLog.setStatus(QueryLog.QueryStatus.ERROR);
            queryLog.setError(e.getMessage());
            queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            queryLogRepository.save(queryLog);

            return buildErrorResponse(queryLog);
        }
    }

    /**
     * Get query history
     */
    public List<QueryLog> getQueryHistory(int limit) {
        log.debug("Fetching query history (limit: {})", limit);
        return queryLogRepository.findAll()
                .stream()
                .limit(limit)
                .toList();
    }

    /**
     * Get a specific query log by ID
     */
    public QueryLog getQueryLog(Long id) {
        log.debug("Fetching query log with id: {}", id);
        return queryLogRepository.findById(id).orElse(null);
    }

    private String buildSelfCorrectionUserPrompt(
            String naturalLanguageQuery,
            String failedSql,
            String executionError) {

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
