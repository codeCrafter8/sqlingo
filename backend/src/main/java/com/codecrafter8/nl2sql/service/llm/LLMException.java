package com.codecrafter8.nl2sql.service.llm;

/**
 * Custom exception for LLM-related errors
 */
public class LLMException extends RuntimeException {

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
