package com.codecrafter8.nl2sql.config;

import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RAG-based table selection
 */
@Data
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /**
     * Enable RAG-based table selection
     */
    private boolean enabled = true;

    /**
     * Number of top tables to retrieve (top-k)
     */
    private int topK = 5;

    /**
     * Minimum similarity threshold (0.0 - 1.0)
     */
    private double similarityThreshold = 0.3;

    /**
     * Fallback to full schema if no tables meet threshold
     */
    private boolean fallbackToFullSchema = true;

    /**
     * Index schema on application startup
     */
    private boolean indexOnStartup = true;

    /**
     * Default schema context mode used when request does not override it.
     */
    private SchemaContextMode mode = SchemaContextMode.RAG;
}
