package com.example.apimonitor.exception;

/**
 * Exception thrown when an outbound HTTP job execution fails or returns a non-2xx status code.
 */
public class HttpExecutionException extends RuntimeException {

    private final Integer statusCode;

    public HttpExecutionException(String message) {
        super(message);
        this.statusCode = null;
    }

    public HttpExecutionException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
