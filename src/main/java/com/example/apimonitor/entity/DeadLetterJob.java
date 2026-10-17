package com.example.apimonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_jobs", indexes = {
    @Index(name = "idx_dlq_original_job_id", columnList = "original_job_id", unique = true),
    @Index(name = "idx_dlq_failed_at", columnList = "failed_at")
})
public class DeadLetterJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_job_id", nullable = false, unique = true)
    private UUID originalJobId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    public DeadLetterJob() {
    }

    public DeadLetterJob(UUID originalJobId, String type, String payload, int attemptCount, String failureReason, LocalDateTime failedAt) {
        this.originalJobId = originalJobId;
        this.type = type;
        this.payload = payload;
        this.attemptCount = attemptCount;
        this.failureReason = failureReason;
        this.failedAt = failedAt;
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

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
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
