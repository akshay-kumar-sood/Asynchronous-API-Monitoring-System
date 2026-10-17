package com.example.apimonitor.controller;

import com.example.apimonitor.dto.CreateJobRequest;
import com.example.apimonitor.dto.JobResponse;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Feature 1, 6 & D: Submit a new job or return existing one via idempotency key.
     * Accepts optional Idempotency-Key HTTP header (RFC standard) with fallback to body.
     * Returns HTTP 202 Accepted.
     */
    @PostMapping
    public ResponseEntity<JobResponse> submitJob(
            @Valid @RequestBody CreateJobRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        JobResponse response = jobService.submitJob(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Feature 3: Get job status and details by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable UUID id) {
        JobResponse response = jobService.getJobById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Feature 3: List all jobs with optional status filter.
     */
    @GetMapping
    public ResponseEntity<List<JobResponse>> listJobs(@RequestParam(required = false) JobStatus status) {
        List<JobResponse> jobs = jobService.listJobs(status);
        return ResponseEntity.ok(jobs);
    }
}
