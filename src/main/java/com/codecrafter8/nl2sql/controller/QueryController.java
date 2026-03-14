package com.codecrafter8.nl2sql.controller;

import com.codecrafter8.nl2sql.dto.QueryRequest;
import com.codecrafter8.nl2sql.dto.QueryResponse;
import com.codecrafter8.nl2sql.model.QueryLog;
import com.codecrafter8.nl2sql.service.QueryExecutionService;
import com.codecrafter8.nl2sql.service.QueryLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling natural language to SQL queries
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/query")
public class QueryController {

    private final QueryExecutionService queryExecutionService;
    private final QueryLogService queryLogService;

    /**
     * Execute a natural language query and return SQL and results
     */
    @PostMapping("/execute")
    public ResponseEntity<QueryResponse> executeQuery(@Valid @RequestBody QueryRequest request) {
        log.info("Received query request: {}", request.getNaturalLanguageQuery());

        QueryResponse response = queryExecutionService.executeQuery(request);

        if ("FAILED".equals(response.getStatus()) || "ERROR".equals(response.getStatus())) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get query history
     */
    @GetMapping("/history")
    public ResponseEntity<?> getQueryHistory(@RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching query history with limit: {}", limit);

        var history = queryLogService.getHistory(limit);

        return ResponseEntity.ok(history);
    }

    /**
     * Get a specific query log by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<QueryLog> getQueryLog(@PathVariable Long id) {
        log.info("Fetching query log with id: {}", id);

        QueryLog queryLog = queryLogService.getLogById(id);

        if (queryLog == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(queryLog);
    }

}
