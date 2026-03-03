package com.codecrafter8.nl2sql.controller;

import com.codecrafter8.nl2sql.model.SchemaTable;
import com.codecrafter8.nl2sql.service.SchemaIntrospectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for database schema management
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/schema")
public class SchemaController {

    private final SchemaIntrospectionService schemaIntrospectionService;

    /**
     * Get all tables in the database
     */
    @GetMapping("/tables")
    public ResponseEntity<List<SchemaTable>> getTables() {
        log.info("Fetching all database tables");

        List<SchemaTable> schemaTables = schemaIntrospectionService.getAllTables();

        return ResponseEntity.ok(schemaTables);
    }

    /**
     * Get schema context formatted for LLM
     */
    @GetMapping("/context")
    public ResponseEntity<String> getSchemaContext() {
        log.info("Fetching schema context for LLM");

        String context = schemaIntrospectionService.getSchemaContextForLLM();

        return ResponseEntity.ok(context);
    }

    /**
     * Register a new table schema
     */
    @PostMapping("/tables/register")
    public ResponseEntity<SchemaTable> registerTable(
            @RequestParam String tableName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String schemaJson) {
        log.info("Registering table schema: {}", tableName);

        SchemaTable schemaTable = schemaIntrospectionService.registerTableSchema(
                tableName,
                description,
                schemaJson
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(schemaTable);
    }
}
