package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /**
     * Retrieve relevant tables for a given natural language query
     * Returns a focused schema context containing only the most relevant tables
     *
     * @param naturalLanguageQuery User's query in natural language
     * @return Schema context string containing relevant tables, or full schema if RAG is disabled/fails
     */
    public String retrieveRelevantSchemaContext(String naturalLanguageQuery) {
        if (!ragProperties.isEnabled()) {
            log.debug("RAG is disabled, returning full schema");
            return schemaIntrospectionService.getSchemaContextForLLM();
        }

        try {
            log.debug("Retrieving top-{} relevant tables for query: {}", ragProperties.getTopK(), naturalLanguageQuery);

            // Build search request
            SearchRequest searchRequest = SearchRequest.query(naturalLanguageQuery)
                    .withTopK(ragProperties.getTopK())
                    .withSimilarityThreshold(ragProperties.getSimilarityThreshold());

            // Search vector store
            List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

            if (relevantDocs.isEmpty()) {
                log.warn("No relevant tables found above threshold {}. Using fallback strategy.",
                        ragProperties.getSimilarityThreshold());

                if (ragProperties.isFallbackToFullSchema()) {
                    log.debug("Fallback: returning full schema");
                    return schemaIntrospectionService.getSchemaContextForLLM();
                } else {
                    log.warn("Fallback disabled - returning empty context");
                    return "No relevant tables found for this query.";
                }
            }

            // Extract table names from documents
            List<String> relevantTables = relevantDocs.stream()
                    .map(doc -> doc.getMetadata().get("table_name"))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());

            log.info("Retrieved {} relevant tables: {}", relevantTables.size(), relevantTables);

            // Build focused schema context
            return buildFocusedSchemaContext(relevantTables, relevantDocs);

        } catch (Exception e) {
            log.error("Error during RAG retrieval, falling back to full schema", e);
            return schemaIntrospectionService.getSchemaContextForLLM();
        }
    }

    /**
     * Build a focused schema context containing only the specified tables
     */
    private String buildFocusedSchemaContext(List<String> tableNames, List<Document> documents) {
        StringBuilder context = new StringBuilder();

        context.append("Database Schema (Spider Hospital Database - Focused Context):\n\n");
        context.append("This is a hospital management database. ");
        context.append("The following tables are most relevant to your query:\n\n");

        // Add each relevant table's full schema
        for (int i = 0; i < tableNames.size(); i++) {
            Document doc = documents.get(i);

            // Add relevance indicator
            //todo: think of it
            context.append(String.format("[Relevance Rank: %d]\n", i + 1));

            // Add the embedded text (contains full table info)
            context.append(doc.getContent());
            context.append("\n");
        }

        context.append("\n");
        context.append("Note: Focus your SQL query on these tables as they are most relevant to the user's question.\n");

        return context.toString();
    }

}
