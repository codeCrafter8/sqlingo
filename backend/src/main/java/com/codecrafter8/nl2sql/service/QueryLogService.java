package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.repository.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryLogService {

    private final QueryLogRepository queryLogRepository;

    public QueryLog initializeLog(String naturalLanguageQuery) {
        QueryLog queryLog = new QueryLog();
        queryLog.setNaturalLanguageQuery(naturalLanguageQuery);
        return queryLogRepository.save(queryLog);
    }

    public void logSuccess(QueryLog log, String sql, long startTime) {
        log.setGeneratedSql(sql);
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
}
