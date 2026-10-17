package com.example.apimonitor;

import com.example.apimonitor.entity.DeadLetterJob;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.repository.DeadLetterRepository;
import com.example.apimonitor.repository.JobRepository;
import com.example.apimonitor.service.HttpJobExecutor;
import com.example.apimonitor.service.HttpSender;
import com.example.apimonitor.service.JobWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private DeadLetterRepository deadLetterRepository;

    private final Executor jobWorkerExecutor = Runnable::run;

    private ObjectMapper objectMapper;
    private HttpJobExecutor httpJobExecutor;
    private JobWorker jobWorker;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        httpJobExecutor = new HttpJobExecutor(objectMapper, 1000, 1000, true);
        jobWorker = new JobWorker(jobRepository, deadLetterRepository, jobWorkerExecutor, objectMapper, httpJobExecutor);
        ReflectionTestUtils.setField(jobWorker, "batchSize", 5);
        ReflectionTestUtils.setField(jobWorker, "initialIntervalSeconds", 1L);
        ReflectionTestUtils.setField(jobWorker, "backoffMultiplier", 2.0);
    }

    @Test
    @DisplayName("Should claim eligible jobs atomically and update status to PROCESSING")
    void testClaimEligibleJobs() {
        Job job1 = new Job("SEND_EMAIL", "{}", null, 3);
        job1.setId(UUID.randomUUID());
        job1.setStatus(JobStatus.QUEUED);

        when(jobRepository.findEligibleJobsForUpdate(any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of(job1));

        List<UUID> claimed = jobWorker.claimEligibleJobs();

        assertEquals(1, claimed.size());
        assertEquals(job1.getId(), claimed.get(0));
        assertEquals(JobStatus.PROCESSING, job1.getStatus());
        verify(jobRepository).save(job1);
    }

    @Test
    @DisplayName("Should record job SUCCESS when execution completes without errors")
    void testRecordSuccess() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job("SEND_EMAIL", "{\"message\":\"hello\"}", null, 3);
        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        jobWorker.recordSuccess(jobId);

        assertEquals(JobStatus.SUCCESS, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertNull(job.getLastError());
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("Should schedule retry with exponential backoff on transient failure (Feature 5)")
    void testRecordFailureTransientRetry() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job("SEND_EMAIL", "{}", null, 3);
        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);
        job.setAttemptCount(0); // Attempt 1 fails

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        jobWorker.recordFailure(jobId, "Simulated network timeout");

        assertEquals(1, job.getAttemptCount());
        assertEquals(JobStatus.RETRYING, job.getStatus());
        assertEquals("Simulated network timeout", job.getLastError());
        assertNotNull(job.getNextRetryAt());
        // For attempt 1: delay = 1 * (2 ^ 0) = 1s
        assertTrue(job.getNextRetryAt().isAfter(LocalDateTime.now().minusSeconds(1)));

        verify(jobRepository).save(job);
        verify(deadLetterRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should transition to DEAD and store in DLQ when max retry attempts are exhausted (Feature 7)")
    void testRecordFailureMaxAttemptsExhausted() {
        UUID jobId = UUID.randomUUID();
        Job job = new Job("SEND_EMAIL", "{\"fail\":true}", null, 3);
        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);
        job.setAttemptCount(2); // Current attempts = 2, so next is 3 (maxAttempts)

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(deadLetterRepository.existsByOriginalJobId(jobId)).thenReturn(false);

        jobWorker.recordFailure(jobId, "Permanent external API failure");

        assertEquals(3, job.getAttemptCount());
        assertEquals(JobStatus.DEAD, job.getStatus());
        assertEquals("Permanent external API failure", job.getLastError());
        assertNotNull(job.getCompletedAt());

        verify(jobRepository).save(job);

        ArgumentCaptor<DeadLetterJob> dlqCaptor = ArgumentCaptor.forClass(DeadLetterJob.class);
        verify(deadLetterRepository).save(dlqCaptor.capture());
        DeadLetterJob dlq = dlqCaptor.getValue();
        assertEquals(jobId, dlq.getOriginalJobId());
        assertEquals("SEND_EMAIL", dlq.getType());
        assertEquals(3, dlq.getAttemptCount());
        assertEquals("Permanent external API failure", dlq.getFailureReason());
    }

    @Test
    @DisplayName("Should execute real HTTP job, record responseStatusCode 200, and mark SUCCESS")
    void testExecuteJobWithHttpSuccess() {
        HttpSender mockSender = req -> new HttpSender.SimpleHttpResponse(200, "{\"ok\":true}");
        HttpJobExecutor testExecutor = new HttpJobExecutor(objectMapper, mockSender, 5000, true);
        JobWorker customWorker = new JobWorker(jobRepository, deadLetterRepository, jobWorkerExecutor, objectMapper, testExecutor);

        UUID jobId = UUID.randomUUID();
        Job job = new Job("HTTP_MONITOR", "{\"url\":\"http://127.0.0.1:8080/health\",\"method\":\"GET\"}", null, 3);
        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        customWorker.executeJob(jobId);

        assertEquals(JobStatus.SUCCESS, job.getStatus());
        assertEquals(200, job.getResponseStatusCode());
        assertNull(job.getLastError());
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("Should execute HTTP job, handle 502 Bad Gateway, record responseStatusCode 502, and schedule retry")
    void testExecuteJobWithHttpFailureRetry() {
        HttpSender mockSender = req -> new HttpSender.SimpleHttpResponse(502, "Bad Gateway");
        HttpJobExecutor testExecutor = new HttpJobExecutor(objectMapper, mockSender, 5000, true);
        JobWorker customWorker = new JobWorker(jobRepository, deadLetterRepository, jobWorkerExecutor, objectMapper, testExecutor);
        ReflectionTestUtils.setField(customWorker, "initialIntervalSeconds", 1L);
        ReflectionTestUtils.setField(customWorker, "backoffMultiplier", 2.0);

        UUID jobId = UUID.randomUUID();
        Job job = new Job("HTTP_MONITOR", "{\"url\":\"http://127.0.0.1:8080/service\",\"method\":\"GET\"}", null, 3);
        job.setId(jobId);
        job.setStatus(JobStatus.PROCESSING);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        customWorker.executeJob(jobId);

        assertEquals(JobStatus.RETRYING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals(502, job.getResponseStatusCode());
        assertTrue(job.getLastError().contains("502"));
        assertNotNull(job.getNextRetryAt());
        verify(jobRepository).save(job);
    }
}
