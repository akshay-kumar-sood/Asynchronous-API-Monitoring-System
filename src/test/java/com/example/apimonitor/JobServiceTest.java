package com.example.apimonitor;

import com.example.apimonitor.dto.CreateJobRequest;
import com.example.apimonitor.dto.JobResponse;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.exception.ResourceNotFoundException;
import com.example.apimonitor.repository.JobRepository;
import com.example.apimonitor.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    private ObjectMapper objectMapper;
    private JobService jobService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jobService = new JobService(jobRepository, objectMapper, 3);
    }

    @Test
    @DisplayName("Should submit a new job successfully and set status to QUEUED")
    void testSubmitNewJob() {
        Map<String, Object> payload = Map.of("recipient", "demo@example.com", "message", "Hello");
        CreateJobRequest request = new CreateJobRequest("SEND_EMAIL", payload, "key-123");

        when(jobRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.empty());

        UUID generatedId = UUID.randomUUID();
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(generatedId);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        });

        JobResponse response = jobService.submitJob(request);

        assertNotNull(response);
        assertEquals(generatedId, response.getId());
        assertEquals("SEND_EMAIL", response.getType());
        assertEquals("key-123", response.getIdempotencyKey());
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals(0, response.getAttemptCount());
        assertEquals(3, response.getMaxAttempts());

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).saveAndFlush(jobCaptor.capture());
        Job saved = jobCaptor.getValue();
        assertEquals("SEND_EMAIL", saved.getType());
        assertEquals("key-123", saved.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should return existing job when same idempotency key is submitted again (Feature 6)")
    void testIdempotentSubmission() {
        UUID existingId = UUID.randomUUID();
        Job existingJob = new Job("SEND_EMAIL", "{\"recipient\":\"demo@example.com\"}", "key-duplicate", 3);
        existingJob.setId(existingId);
        existingJob.setStatus(JobStatus.PROCESSING);
        existingJob.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        existingJob.setUpdatedAt(LocalDateTime.now());

        when(jobRepository.findByIdempotencyKey("key-duplicate")).thenReturn(Optional.of(existingJob));

        CreateJobRequest duplicateRequest = new CreateJobRequest(
                "SEND_EMAIL",
                Map.of("recipient", "demo@example.com"),
                "key-duplicate"
        );

        JobResponse response = jobService.submitJob(duplicateRequest);

        assertEquals(existingId, response.getId());
        assertEquals(JobStatus.PROCESSING, response.getStatus());
        verify(jobRepository, never()).saveAndFlush(any(Job.class));
    }

    @Test
    @DisplayName("Should retrieve job details by ID (Feature 3)")
    void testGetJobByIdSuccess() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job("PROCESS_PAYMENT", "{\"amount\":100}", null, 3);
        job.setId(jobId);
        job.setStatus(JobStatus.SUCCESS);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        JobResponse response = jobService.getJobById(jobId);

        assertNotNull(response);
        assertEquals(jobId, response.getId());
        assertEquals(JobStatus.SUCCESS, response.getStatus());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when job ID does not exist")
    void testGetJobByIdNotFound() {
        UUID randomId = UUID.randomUUID();
        when(jobRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> jobService.getJobById(randomId));
    }

    @Test
    @DisplayName("Should filter jobs by status")
    void testListJobsWithStatusFilter() {
        Job job = new Job("SEND_EMAIL", "{}", null, 3);
        job.setId(UUID.randomUUID());
        job.setStatus(JobStatus.FAILED);
        job.setCreatedAt(LocalDateTime.now());

        when(jobRepository.findByStatusOrderByCreatedAtDesc(JobStatus.FAILED)).thenReturn(List.of(job));

        List<JobResponse> result = jobService.listJobs(JobStatus.FAILED);

        assertEquals(1, result.size());
        assertEquals(JobStatus.FAILED, result.get(0).getStatus());
        verify(jobRepository).findByStatusOrderByCreatedAtDesc(JobStatus.FAILED);
        verify(jobRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Should recover and return winning job when concurrent duplicate throws DataIntegrityViolationException (Feature D)")
    void testConcurrentDuplicateCollisionRecovery() {
        CreateJobRequest request = new CreateJobRequest("HTTP_CHECK", Map.of("url", "https://api.com"), "race-key");

        when(jobRepository.findByIdempotencyKey("race-key"))
                .thenReturn(Optional.empty()) // Initial check passes for both threads
                .thenReturn(Optional.of(new Job("HTTP_CHECK", "{}", "race-key", 3))); // Subsequent check finds winner

        when(jobRepository.saveAndFlush(any(Job.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint"));

        JobResponse response = jobService.submitJob(request, "race-key");

        assertNotNull(response);
        assertEquals("race-key", response.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should prioritize Idempotency-Key HTTP header over request body key")
    void testHeaderPrecedenceOverBodyKey() {
        CreateJobRequest request = new CreateJobRequest("HTTP_CHECK", Map.of("url", "https://api.com"), "body-key");

        when(jobRepository.findByIdempotencyKey("header-priority-key")).thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(i -> {
            Job j = i.getArgument(0);
            j.setId(UUID.randomUUID());
            j.setCreatedAt(LocalDateTime.now());
            return j;
        });

        JobResponse response = jobService.submitJob(request, "header-priority-key");

        assertNotNull(response);
        assertEquals("header-priority-key", response.getIdempotencyKey());
    }
}
