package com.codecrafter8.nl2sql.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a table schema information for formatting and embedding.
 * Used for both RAG (vector embeddings) and full schema context generation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableSchemaDocument {

    /**
     * Unique identifier for the table
     */
    private String tableName;

    /**
     * Table columns with their types
     */
    private Map<String, String> columns;

    /**
     * Primary key columns
     */
    private List<String> primaryKeys;

    /**
     * Foreign key relationships
     */
    private Map<String, String> foreignKeys;

    /**
     * Human-readable description for the table
     */
    private String description;

    public String toEmbeddingText() {
        StringBuilder text = new StringBuilder();

        text.append("Tabela: ").append(tableName).append("\n");

        if (description != null && !description.isEmpty()) {
            text.append("Opis: ").append(description).append("\n");
        }

        if (columns != null && !columns.isEmpty()) {
            text.append("Zawartość (kolumny): ")
                    .append(String.join(", ", columns.keySet()))
                    .append("\n");
        }

        return text.toString();
    }

    public String toPromptText() {
        StringBuilder text = new StringBuilder();

        text.append("Tabela: ").append(tableName).append("\n");

        if (description != null && !description.isEmpty()) {
            text.append("Opis: ").append(description).append("\n");
        }

        text.append("----------------------------------------\n");

        if (columns != null && !columns.isEmpty()) {
            text.append("Kolumny:\n");
            columns.forEach((name, type) ->
                    text.append("  - ").append(name).append(": ").append(type).append("\n")
            );
        }

        if (primaryKeys != null && !primaryKeys.isEmpty()) {
            text.append("Klucz główny: ").append(String.join(", ", primaryKeys)).append("\n");
        }

        if (foreignKeys != null && !foreignKeys.isEmpty()) {
            text.append("Klucze obce:\n");
            foreignKeys.forEach((fk, ref) ->
                    text.append("  - ").append(fk).append(" -> ").append(ref).append("\n")
            );
        }

        return text.toString();
    }

}
