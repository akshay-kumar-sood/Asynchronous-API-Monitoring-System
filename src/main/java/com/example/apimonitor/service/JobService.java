package com.example.apimonitor.service;

import com.example.apimonitor.dto.CreateJobRequest;
import com.example.apimonitor.dto.JobResponse;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.exception.ResourceNotFoundException;
import com.example.apimonitor.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final int defaultMaxAttempts;

    public JobService(JobRepository jobRepository,
                      ObjectMapper objectMapper,
                      @Value("${app.retry.max-attempts:3}") int defaultMaxAttempts) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    /**
     * Submits a new job or returns an existing one if an idempotency key was reused.
     * Checks both the Idempotency-Key HTTP header and the request body field.
     */
    @Transactional
    public JobResponse submitJob(CreateJobRequest request, String idempotencyKeyHeader) {
        String keyCandidate = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : request.getIdempotencyKey();

        String key = (keyCandidate != null && !keyCandidate.isBlank())
                ? keyCandidate.trim()
                : null;

        // Idempotency check: Return existing job if the key was already registered
        if (key != null) {
            Optional<Job> existing = jobRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                log.info("Duplicate submission detected for idempotencyKey='{}'. Returning existing job ID: {}",
                        key, existing.get().getId());
                return JobResponse.fromEntity(existing.get(), objectMapper);
            }
        }

        // Serialize payload to JSON string
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize job payload: " + e.getMessage(), e);
        }

        Job job = new Job(request.getType().trim(), payloadJson, key, defaultMaxAttempts);
        job.setStatus(JobStatus.QUEUED);
        job.setNextRetryAt(LocalDateTime.now());

        try {
            Job saved = jobRepository.saveAndFlush(job);
            log.info("Job submitted successfully with ID: {}, type: {}, idempotencyKey: {}",
                    saved.getId(), saved.getType(), key);
            return JobResponse.fromEntity(saved, objectMapper);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition: Another thread inserted the same idempotency key simultaneously
            if (key != null) {
                Optional<Job> existing = jobRepository.findByIdempotencyKey(key);
                if (existing.isPresent()) {
                    log.warn("Concurrent duplicate submission caught via unique constraint for idempotencyKey='{}'. Returning winner job ID: {}",
                            key, existing.get().getId());
                    return JobResponse.fromEntity(existing.get(), objectMapper);
                }
            }
            throw ex;
        }
    }

    /**
     * Overload for backwards compatibility and internal callers.
     */
    @Transactional
    public JobResponse submitJob(CreateJobRequest request) {
        return submitJob(request, null);
    }

    /**
     * Retrieves job status and details by UUID.
     */
    @Transactional(readOnly = true)
    public JobResponse getJobById(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with ID: " + id));
        return JobResponse.fromEntity(job, objectMapper);
    }

    /**
     * Lists jobs with an optional status filter.
     */
    @Transactional(readOnly = true)
    public List<JobResponse> listJobs(JobStatus status) {
        List<Job> jobs = (status != null)
                ? jobRepository.findByStatusOrderByCreatedAtDesc(status)
                : jobRepository.findAllByOrderByCreatedAtDesc();

        return jobs.stream()
                .map(j -> JobResponse.fromEntity(j, objectMapper))
                .collect(Collectors.toList());
    }
}
