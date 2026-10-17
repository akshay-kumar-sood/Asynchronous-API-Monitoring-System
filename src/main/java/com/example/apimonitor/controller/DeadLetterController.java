package com.example.apimonitor.controller;

import com.example.apimonitor.dto.DeadLetterResponse;
import com.example.apimonitor.dto.JobResponse;
import com.example.apimonitor.service.DeadLetterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dlq")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    public DeadLetterController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    /**
     * Feature 7: List all dead-letter jobs.
     */
    @GetMapping
    public ResponseEntity<List<DeadLetterResponse>> listDeadLetterJobs() {
        List<DeadLetterResponse> response = deadLetterService.listDeadLetterJobs();
        return ResponseEntity.ok(response);
    }

    /**
     * Feature 7: Manually requeue a dead-letter job.
     */
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<JobResponse> retryDeadLetterJob(@PathVariable UUID jobId) {
        JobResponse response = deadLetterService.retryDeadLetterJob(jobId);
        return ResponseEntity.ok(response);
    }
}
