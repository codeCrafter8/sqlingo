package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.config.RagProperties;
import com.codecrafter8.nl2sql.model.TableSchemaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service responsible for indexing database schema into vector store
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SchemaEmbeddingIndexer {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final SchemaIntrospectionService schemaIntrospectionService;

    /**
     * Index schema on application startup if enabled
     */
    @EventListener(ApplicationReadyEvent.class)
    public void indexSchemaOnStartup() {
        if (ragProperties.isIndexOnStartup()) {
            log.info("RAG auto-indexing enabled - indexing database schema...");
            try {
                indexSchema();
                log.info("Schema indexing completed successfully");
            } catch (Exception e) {
                log.error("Failed to index schema on startup", e);
            }
        } else {
            log.info("RAG auto-indexing disabled - schema must be indexed manually");
        }
    }

    /**
     * Index all tables in the database schema into vector store
     */
    public void indexSchema() {
        log.info("Starting schema indexing process");

        List<String> tableNames = schemaIntrospectionService.getAllTables();
        List<Document> documents = new ArrayList<>();

        for (String tableName : tableNames) {
            try {
                TableSchemaDocument schemaDoc = buildTableSchemaDocument(tableName);
                String embeddingText = schemaDoc.format();

                String deterministicId = UUID.nameUUIDFromBytes(tableName.getBytes()).toString();

                // Create Spring AI Document for vector store
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("table_name", tableName);
                metadata.put("column_count", schemaDoc.getColumns().size());
                metadata.put("has_fk", !schemaDoc.getForeignKeys().isEmpty());

                Document doc = new Document(
                        deterministicId,
                        embeddingText,
                        metadata
                );

                documents.add(doc);
                log.debug("Prepared document for table: {} (ID: {})", tableName, deterministicId);

            } catch (Exception e) {
                log.error("Failed to build schema document for table: {}", tableName, e);
            }
        }

        // Add all documents to vector store
        if (!documents.isEmpty()) {
            try {
                // Re-index is idempotent: remove existing vectors for the same table IDs first.
                List<String> idsToDelete = tableNames.stream()
                        .map(name -> UUID.nameUUIDFromBytes(name.getBytes()).toString())
                        .toList();

                log.debug("Deleting {} existing entries from vector store before re-indexing", idsToDelete.size());
                vectorStore.delete(idsToDelete);
            } catch (Exception e) {
                log.debug("Could not delete existing vectors before re-index; continuing with add", e);
            }

            vectorStore.add(documents);
            log.info("Successfully indexed {} tables into vector store", documents.size());
        } else {
            log.warn("No tables were indexed");
        }
    }

    /**
     * Build a TableSchemaDocument for a specific table
     */
    private TableSchemaDocument buildTableSchemaDocument(String tableName) {
        Map<String, String> columns = schemaIntrospectionService.getTableSchema(tableName);
        List<String> primaryKeys = schemaIntrospectionService.getPrimaryKeys(tableName);
        Map<String, String> foreignKeys = schemaIntrospectionService.getForeignKeys(tableName);
        String description = schemaIntrospectionService.generateTableDescription(tableName, columns);

        return TableSchemaDocument.builder()
                .tableName(tableName)
                .columns(columns)
                .primaryKeys(primaryKeys)
                .foreignKeys(foreignKeys)
                .description(description)
                .build();
    }

}
