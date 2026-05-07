package com.codecrafter8.nl2sql.dto;

import lombok.Builder;

@Builder
public record LlmResponse(
        String sql,
        int promptTokens,
        int completionTokens,
        String analysis) {
}
