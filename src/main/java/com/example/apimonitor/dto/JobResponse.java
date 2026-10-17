package com.example.apimonitor.dto;

import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class JobResponse {

    private UUID id;
    private String type;
    private Object payload;
    private String idempotencyKey;
    private JobStatus status;
    private int attemptCount;
    private int maxAttempts;
    private LocalDateTime nextRetryAt;
    private Integer responseStatusCode;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public JobResponse() {
    }

    public static JobResponse fromEntity(Job job, ObjectMapper objectMapper) {
        JobResponse response = new JobResponse();
        response.setId(job.getId());
        response.setType(job.getType());
        response.setIdempotencyKey(job.getIdempotencyKey());
        response.setStatus(job.getStatus());
        response.setAttemptCount(job.getAttemptCount());
        response.setMaxAttempts(job.getMaxAttempts());
        response.setNextRetryAt(job.getNextRetryAt());
        response.setResponseStatusCode(job.getResponseStatusCode());
        response.setLastError(job.getLastError());
        response.setCreatedAt(job.getCreatedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        response.setCompletedAt(job.getCompletedAt());

        if (job.getPayload() != null && objectMapper != null) {
            try {
                response.setPayload(objectMapper.readValue(job.getPayload(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                response.setPayload(job.getPayload());
            }
        } else {
            response.setPayload(job.getPayload());
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public void setResponseStatusCode(Integer responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
