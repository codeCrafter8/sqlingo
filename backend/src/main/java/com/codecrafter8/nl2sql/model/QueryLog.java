package com.codecrafter8.nl2sql.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "query_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String naturalLanguageQuery;

    @Column(columnDefinition = "TEXT")
    private String generatedSql;

    @Column(columnDefinition = "TEXT")
    private String results;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Enumerated(EnumType.STRING)
    private QueryStatus status;

    private Long executionTimeMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Integer promptTokens;
    private Integer completionTokens;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum QueryStatus {
        SUCCESS,
        FAILED,
        INVALID_SQL,
        ERROR
    }
}
