package com.codecrafter8.nl2sql.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryRequest {
    @NotBlank(message = "Query cannot be empty")
    private String naturalLanguageQuery;

    private boolean explainSql;
    private int maxRows = 100;
}
