package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.dto.QueryRequest;
import com.codecrafter8.nl2sql.dto.QueryResponse;
import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.repository.QueryLogRepository;
import com.codecrafter8.nl2sql.service.llm.LLMException;
import com.codecrafter8.nl2sql.service.llm.LLMProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for converting natural language queries to SQL
 * and executing them against the database.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class QueryExecutionService {

    private final QueryLogRepository queryLogRepository;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final SQLValidationService sqlValidationService;
    private final ObjectProvider<LLMProvider> llmProvider;

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

            // Get database schema context
            String schemaContext = schemaIntrospectionService.getSchemaContextForLLM();

            // Generate SQL using LLM
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

            // Execute SQL - stub for now
            // List<Map<String, Object>> results = sqlExecutionService.executeQuery(generatedSQL);

            if (request.isExplainSql()) {
                String explanation = provider.explainSQL(generatedSQL);
                // Include in response
            }

            queryLog.setStatus(QueryLog.QueryStatus.SUCCESS);
            queryLog.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            queryLogRepository.save(queryLog);

            return buildSuccessResponse(queryLog);

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

    private QueryResponse buildSuccessResponse(QueryLog queryLog) {
        return QueryResponse.builder()
                .id(queryLog.getId())
                .naturalLanguageQuery(queryLog.getNaturalLanguageQuery())
                .generatedSql(queryLog.getGeneratedSql())
                .status(queryLog.getStatus().toString())
                .executionTimeMs(queryLog.getExecutionTimeMs())
                .rowCount(0) // TODO: Set actual row count
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
