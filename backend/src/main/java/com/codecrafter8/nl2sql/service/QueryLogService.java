package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.repository.QueryLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryLogService {

    private final QueryLogRepository queryLogRepository;
    private final ObjectMapper objectMapper;

    public QueryLog initializeLog(String naturalLanguageQuery) {
        QueryLog queryLog = new QueryLog();
        queryLog.setNaturalLanguageQuery(naturalLanguageQuery);
        return queryLogRepository.save(queryLog);
    }

    public void logSuccess(QueryLog log, String sql, List<Map<String, Object>> results, long startTime) {
        log.setGeneratedSql(sql);
        log.setResults(serializeResults(results));
        log.setStatus(QueryLog.QueryStatus.SUCCESS);
        log.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        queryLogRepository.save(log);
    }

    public void logError(QueryLog log, Exception e, String sql, long startTime) {
        log.setGeneratedSql(sql);
        log.setError(e.getMessage());
        log.setExecutionTimeMs(System.currentTimeMillis() - startTime);

        var status = (e instanceof IllegalArgumentException)
                ? QueryLog.QueryStatus.INVALID_SQL
                : QueryLog.QueryStatus.ERROR;

        log.setStatus(status);
        queryLogRepository.save(log);
    }

    public List<QueryLog> getHistory(int limit) {
        return queryLogRepository.findAll().stream().limit(limit).toList();
    }

    public QueryLog getLogById(Long id) {
        return queryLogRepository.findById(id).orElse(null);
    }

    private String serializeResults(List<Map<String, Object>> results) {
        if (results == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize query results for history log, falling back to toString().", e);
            return results.toString();
        }
    }
}
