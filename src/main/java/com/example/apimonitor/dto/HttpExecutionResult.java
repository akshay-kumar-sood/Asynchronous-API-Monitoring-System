package com.example.apimonitor.dto;

/**
 * Result details from an outbound HTTP job execution.
 */
public class HttpExecutionResult {

    private final int statusCode;
    private final String responseBody;
    private final long executionDurationMs;

    public HttpExecutionResult(int statusCode, String responseBody, long executionDurationMs) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.executionDurationMs = executionDurationMs;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public long getExecutionDurationMs() {
        return executionDurationMs;
    }
}
