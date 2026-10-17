package com.example.apimonitor.entity;

/**
 * Lifecycle states for an asynchronous job.
 */
public enum JobStatus {
    QUEUED,
    PROCESSING,
    SUCCESS,
    RETRYING,
    FAILED,
    DEAD
}
