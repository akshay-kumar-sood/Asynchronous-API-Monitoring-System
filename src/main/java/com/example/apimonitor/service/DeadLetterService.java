package com.example.apimonitor.service;

import com.example.apimonitor.dto.DeadLetterResponse;
import com.example.apimonitor.dto.JobResponse;
import com.example.apimonitor.entity.DeadLetterJob;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.exception.ResourceNotFoundException;
import com.example.apimonitor.repository.DeadLetterRepository;
import com.example.apimonitor.repository.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final DeadLetterRepository deadLetterRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public DeadLetterService(DeadLetterRepository deadLetterRepository,
                             JobRepository jobRepository,
                             ObjectMapper objectMapper) {
        this.deadLetterRepository = deadLetterRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all dead letter records ordered by failed time descending.
     */
    @Transactional(readOnly = true)
    public List<DeadLetterResponse> listDeadLetterJobs() {
        return deadLetterRepository.findAllByOrderByFailedAtDesc().stream()
                .map(dlq -> DeadLetterResponse.fromEntity(dlq, objectMapper))
                .collect(Collectors.toList());
    }

    /**
     * Manually requeues a dead job: atomically resets the job to QUEUED and removes the DLQ entry.
     */
    @Transactional
    public JobResponse retryDeadLetterJob(UUID jobId) {
        // Support lookup by either originalJobId or DLQ entry primary key ID
        Optional<DeadLetterJob> dlqOptional = deadLetterRepository.findByOriginalJobId(jobId);
        if (dlqOptional.isEmpty()) {
            dlqOptional = deadLetterRepository.findById(jobId);
        }

        DeadLetterJob dlq = dlqOptional.orElseThrow(() ->
                new ResourceNotFoundException("No dead letter record found for ID: " + jobId));

        UUID originalJobId = dlq.getOriginalJobId();
        Job job = jobRepository.findById(originalJobId).orElseThrow(() ->
                new ResourceNotFoundException("Original job not found with ID: " + originalJobId));

        // Requeue the job with reset attempt count and immediate retry timestamp
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setLastError(null);
        job.setCompletedAt(null);
        job.setNextRetryAt(LocalDateTime.now());
        Job saved = jobRepository.save(job);

        // Remove the dead letter entry to maintain single-source-of-truth consistency
        deadLetterRepository.delete(dlq);

        log.info("Successfully requeued dead job ID: {} from DLQ", originalJobId);
        return JobResponse.fromEntity(saved, objectMapper);
    }
}
