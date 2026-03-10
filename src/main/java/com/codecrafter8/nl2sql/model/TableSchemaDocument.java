package com.codecrafter8.nl2sql.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a table schema document for embedding and retrieval
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
     * Human-readable description for embedding
     */
    private String description;

    /**
     * Generate a text representation suitable for embedding
     */
    public String toEmbeddingText() {
        StringBuilder text = new StringBuilder();

        text.append("Table: ").append(tableName).append("\n");

        if (description != null && !description.isEmpty()) {
            text.append("Description: ").append(description).append("\n");
        }

        text.append("Columns: ");
        if (columns != null && !columns.isEmpty()) {
            text.append(String.join(", ", columns.keySet())).append("\n");

            // Add column details
            columns.forEach((name, type) ->
                    text.append("  - ").append(name).append(": ").append(type).append("\n")
            );
        }

        if (primaryKeys != null && !primaryKeys.isEmpty()) {
            text.append("Primary Keys: ").append(String.join(", ", primaryKeys)).append("\n");
        }

        if (foreignKeys != null && !foreignKeys.isEmpty()) {
            text.append("Foreign Key Relationships: \n");
            foreignKeys.forEach((fk, ref) ->
                    text.append("  - ").append(fk).append(" references ").append(ref).append("\n")
            );
        }

        return text.toString();
    }
}
