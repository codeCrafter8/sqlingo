package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.config.RagProperties;
import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Service for retrieving relevant tables using RAG (Retrieval-Augmented Generation)
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TableContextRetriever {

    private final VectorStore vectorStore;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final RagProperties ragProperties;

    public record SchemaContextResult(String schemaContext, List<String> selectedTables) {
    }

    public SchemaContextResult retrieveRelevantSchemaContext(String naturalLanguageQuery, SchemaContextMode mode) {
        if (mode == SchemaContextMode.FULL_SCHEMA) {
            log.debug("Schema context mode: FULL_SCHEMA");
            return new SchemaContextResult(schemaIntrospectionService.getSchemaContextForLLM(), List.of());
        }
        return retrieveRelevantSchemaContext(naturalLanguageQuery);
    }

    /**
     * Retrieve relevant tables for a given natural language query
     * Returns a focused schema context containing only the most relevant tables
     *
     * @param naturalLanguageQuery User's query in natural language
     * @return Schema context + selected tables, or full schema if RAG is disabled/fails
     */
    public SchemaContextResult retrieveRelevantSchemaContext(String naturalLanguageQuery) {
        if (!ragProperties.isEnabled()) {
            log.debug("RAG is disabled, returning full schema");
            return new SchemaContextResult(schemaIntrospectionService.getSchemaContextForLLM(), List.of());
        }

        try {
            log.debug("Retrieving top-{} relevant tables for query: {}", ragProperties.getTopK(), naturalLanguageQuery);

            // Build search request
            SearchRequest searchRequest = SearchRequest.query(naturalLanguageQuery)
                    .withTopK(ragProperties.getTopK())
                    .withSimilarityThreshold(ragProperties.getSimilarityThreshold());

            // Search vector store
            List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
            relevantDocs.forEach(doc -> log.info("SCORE_DEBUG | table={} | score={}",
                    doc.getMetadata().get("table_name"),
                    doc.getMetadata().get("distance")));

            if (relevantDocs.isEmpty()) {
                log.warn("No relevant tables found above threshold {}. Using fallback strategy.",
                        ragProperties.getSimilarityThreshold());

                if (ragProperties.isFallbackToFullSchema()) {
                    log.debug("Fallback: returning full schema");
                    return new SchemaContextResult(schemaIntrospectionService.getSchemaContextForLLM(), List.of());
                }

                log.warn("Fallback disabled - returning empty context");
                return new SchemaContextResult("Brak pasujących tabel.", List.of());
            }

            // Extract table names from documents
            List<String> relevantTables = relevantDocs.stream()
                    .map(doc -> doc.getMetadata().get("table_name"))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();

            log.info("Retrieved {} relevant tables: {}", relevantTables.size(), relevantTables);

            // Build focused schema context
            return new SchemaContextResult(buildFocusedSchemaContext(relevantDocs), relevantTables);

        } catch (Exception e) {
            log.error("Error during RAG retrieval, falling back to full schema", e);
            return new SchemaContextResult(schemaIntrospectionService.getSchemaContextForLLM(), List.of());
        }
    }

    /**
     * Build a focused schema context containing only the specified tables
     */
    private String buildFocusedSchemaContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        context.append("Schemat bazy danych (tabele istotne dla zapytania):\n\n");

        for (Document doc : documents) {
            String fullSchema = (String) doc.getMetadata().get("full_schema_prompt");

            if (fullSchema != null) {
                context.append(fullSchema).append("\n\n---\n\n");
            } else {
                context.append(doc.getContent()).append("\n\n---\n\n");
            }
        }

        return context.toString();
    }

}
