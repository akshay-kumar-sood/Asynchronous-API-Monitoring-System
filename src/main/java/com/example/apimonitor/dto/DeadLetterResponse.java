package com.example.apimonitor.dto;

import com.example.apimonitor.entity.DeadLetterJob;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class DeadLetterResponse {

    private UUID id;
    private UUID originalJobId;
    private String type;
    private Object payload;
    private int attemptCount;
    private String failureReason;
    private LocalDateTime failedAt;

    public DeadLetterResponse() {
    }

    public static DeadLetterResponse fromEntity(DeadLetterJob entity, ObjectMapper objectMapper) {
        DeadLetterResponse response = new DeadLetterResponse();
        response.setId(entity.getId());
        response.setOriginalJobId(entity.getOriginalJobId());
        response.setType(entity.getType());
        response.setAttemptCount(entity.getAttemptCount());
        response.setFailureReason(entity.getFailureReason());
        response.setFailedAt(entity.getFailedAt());

        if (entity.getPayload() != null && objectMapper != null) {
            try {
                response.setPayload(objectMapper.readValue(entity.getPayload(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                response.setPayload(entity.getPayload());
            }
        } else {
            response.setPayload(entity.getPayload());
        }

        return response;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOriginalJobId() {
        return originalJobId;
    }

    public void setOriginalJobId(UUID originalJobId) {
        this.originalJobId = originalJobId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }
}
