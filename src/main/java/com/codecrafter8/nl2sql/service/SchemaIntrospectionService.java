package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.model.SchemaTable;
import com.codecrafter8.nl2sql.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for introspecting the database schema
 * and providing schema information to the LLM for SQL generation.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SchemaIntrospectionService {

    private final TableRepository tableRepository;

    /**
     * Get all tables from the database schema
     */
    public List<SchemaTable> getAllTables() {
        log.debug("Fetching all tables from database schema");
        //todo: different approach
        return tableRepository.findAll();
    }

    /**
     * Get schema as formatted string for LLM context
     */
    public String getSchemaContextForLLM() {
        log.debug("Generating schema context for LLM");
        List<SchemaTable> schemaTables = getAllTables();

        StringBuilder schemaContext = new StringBuilder();
        schemaContext.append("Database Schema:\n\n");

        for (SchemaTable schemaTable : schemaTables) {
            schemaContext.append(String.format("Table: %s\n", schemaTable.getName()));
            if (schemaTable.getDescription() != null) {
                schemaContext.append(String.format("Description: %s\n", schemaTable.getDescription()));
            }
            if (schemaTable.getSchemaJson() != null) {
                schemaContext.append(String.format("Columns: %s\n", schemaTable.getSchemaJson()));
            }
            schemaContext.append("\n");
        }

        return schemaContext.toString();
    }

    /**
     * Register a new table schema
     */
    public SchemaTable registerTableSchema(String tableName, String description, String schemaJson) {
        log.info("Registering table schema for: {}", tableName);

        SchemaTable schemaTable = SchemaTable.builder()
                .name(tableName)
                .description(description)
                .schemaJson(schemaJson)
                .build();

        return tableRepository.save(schemaTable);
    }
}
