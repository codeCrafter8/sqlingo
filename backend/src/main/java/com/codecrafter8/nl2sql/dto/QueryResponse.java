package com.codecrafter8.nl2sql.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryResponse {
    private Long id;
    private String naturalLanguageQuery;
    private String generatedSql;
    private String sqlExplanation;
    private Object results;
    private int rowCount;
    private Long executionTimeMs;
    private String status;
    private String error;
}
