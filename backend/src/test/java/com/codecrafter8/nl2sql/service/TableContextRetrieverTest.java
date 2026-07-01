package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.config.RagProperties;
import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TableContextRetrieverTest {

    private VectorStore vectorStore;
    private SchemaIntrospectionService schemaIntrospectionService;
    private RagProperties ragProperties;
    private TableContextRetriever tableContextRetriever;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        ragProperties = mock(RagProperties.class);
        tableContextRetriever = new TableContextRetriever(vectorStore, schemaIntrospectionService, ragProperties);
    }

    @Test
    void retrieveRelevantSchemaContextWithFullSchemaModeReturnsFullSchema() {
        when(schemaIntrospectionService.getSchemaContextForLLM()).thenReturn("FULL_SCHEMA");

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "jacy są lekarze", SchemaContextMode.FULL_SCHEMA);

        assertThat(result.schemaContext()).isEqualTo("FULL_SCHEMA");
        assertThat(result.selectedTables()).isEmpty();
        verify(schemaIntrospectionService).getSchemaContextForLLM();
        verifyNoRagInteractions();
    }

    @Test
    void retrieveRelevantSchemaContextWithDelegatingModeUsesRagFlow() {
        when(ragProperties.isEnabled()).thenReturn(false);
        when(schemaIntrospectionService.getSchemaContextForLLM()).thenReturn("FULL_SCHEMA");

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "jacy są lekarze", SchemaContextMode.RAG);

        assertThat(result.schemaContext()).isEqualTo("FULL_SCHEMA");
        assertThat(result.selectedTables()).isEmpty();
        verify(schemaIntrospectionService).getSchemaContextForLLM();
        verifyNoRagInteractions();
    }

    @Test
    void retrieveRelevantSchemaContextWhenRagDisabledReturnsFullSchema() {
        when(ragProperties.isEnabled()).thenReturn(false);
        when(schemaIntrospectionService.getSchemaContextForLLM()).thenReturn("FULL_SCHEMA");

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "jacy są lekarze");

        assertThat(result.schemaContext()).isEqualTo("FULL_SCHEMA");
        assertThat(result.selectedTables()).isEmpty();
        verify(schemaIntrospectionService).getSchemaContextForLLM();
        verifyNoRagInteractions();
    }

    @Test
    void retrieveRelevantSchemaContextWhenRagEnabledBuildsFocusedContextAndSelectedTables() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragProperties.getTopK()).thenReturn(3);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.42);

        Document physicians = document("physicians-id", "Physician content",
                Map.of("table_name", "physician"));
        Document departments = document("departments-id", "Department content",
                Map.of("table_name", "department"));
        Document unnamed = document("unnamed-id", "Unmapped content",
                Map.of());

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(physicians, departments, unnamed));

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "znajdź lekarzy i oddziały");

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchRequestCaptor.capture());
        SearchRequest searchRequest = searchRequestCaptor.getValue();

        assertThat(searchRequest.getQuery()).isEqualTo("znajdź lekarzy i oddziały");
        assertThat(searchRequest.getTopK()).isEqualTo(3);
        assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(0.42);

        assertThat(result.selectedTables()).containsExactly("physician", "department");
        assertThat(result.schemaContext())
                .contains("Schemat bazy danych (tabele istotne dla zapytania):")
                .contains("Physician content")
                .contains("Department content")
                .contains("---");
    }

    @Test
    void retrieveRelevantSchemaContextWhenNoDocumentsAndFallbackEnabledReturnsFullSchema() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragProperties.getTopK()).thenReturn(5);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.3);
        when(ragProperties.isFallbackToFullSchema()).thenReturn(true);
        when(schemaIntrospectionService.getSchemaContextForLLM()).thenReturn("FULL_SCHEMA");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "brak wyników");

        assertThat(result.schemaContext()).isEqualTo("FULL_SCHEMA");
        assertThat(result.selectedTables()).isEmpty();
        verify(schemaIntrospectionService).getSchemaContextForLLM();
    }

    @Test
    void retrieveRelevantSchemaContextWhenNoDocumentsAndFallbackDisabledReturnsEmptyContextMessage() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragProperties.getTopK()).thenReturn(5);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.3);
        when(ragProperties.isFallbackToFullSchema()).thenReturn(false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "brak wyników");

        assertThat(result.schemaContext()).isEqualTo("Brak pasujących tabel.");
        assertThat(result.selectedTables()).isEmpty();
        verify(schemaIntrospectionService, never()).getSchemaContextForLLM();
    }

    @Test
    void retrieveRelevantSchemaContextWhenVectorSearchFailsFallsBackToFullSchema() {
        when(ragProperties.isEnabled()).thenReturn(true);
        when(ragProperties.getTopK()).thenReturn(5);
        when(ragProperties.getSimilarityThreshold()).thenReturn(0.3);
        when(schemaIntrospectionService.getSchemaContextForLLM()).thenReturn("FULL_SCHEMA");
        doThrow(new RuntimeException("vector store unavailable"))
                .when(vectorStore).similaritySearch(any(SearchRequest.class));

        TableContextRetriever.SchemaContextResult result = tableContextRetriever.retrieveRelevantSchemaContext(
                "jacy są lekarze");

        assertThat(result.schemaContext()).isEqualTo("FULL_SCHEMA");
        assertThat(result.selectedTables()).isEmpty();
        verify(schemaIntrospectionService).getSchemaContextForLLM();
    }

    private void verifyNoRagInteractions() {
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    private static Document document(String id, String content, Map<String, Object> metadata) {
        return new Document(id, content, new HashMap<>(metadata));
    }
}
