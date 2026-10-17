package com.example.apimonitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class CreateJobRequest {

    @NotBlank(message = "Job type is required")
    private String type;

    @NotNull(message = "Job payload is required")
    private Map<String, Object> payload;

    private String idempotencyKey;

    public CreateJobRequest() {
    }

    public CreateJobRequest(String type, Map<String, Object> payload, String idempotencyKey) {
        this.type = type;
        this.payload = payload;
        this.idempotencyKey = idempotencyKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
