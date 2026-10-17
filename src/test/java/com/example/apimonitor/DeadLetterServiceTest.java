package com.example.apimonitor;

import com.example.apimonitor.dto.DeadLetterResponse;
import com.example.apimonitor.dto.JobResponse;
import com.example.apimonitor.entity.DeadLetterJob;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.exception.ResourceNotFoundException;
import com.example.apimonitor.repository.DeadLetterRepository;
import com.example.apimonitor.repository.JobRepository;
import com.example.apimonitor.service.DeadLetterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterServiceTest {

    @Mock
    private DeadLetterRepository deadLetterRepository;

    @Mock
    private JobRepository jobRepository;

    private ObjectMapper objectMapper;
    private DeadLetterService deadLetterService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        deadLetterService = new DeadLetterService(deadLetterRepository, jobRepository, objectMapper);
    }

    @Test
    @DisplayName("Should list all dead letter jobs")
    void testListDeadLetterJobs() {
        DeadLetterJob dlq = new DeadLetterJob(
                UUID.randomUUID(),
                "SEND_EMAIL",
                "{\"recipient\":\"test@example.com\"}",
                3,
                "Connection timed out",
                LocalDateTime.now()
        );
        dlq.setId(UUID.randomUUID());

        when(deadLetterRepository.findAllByOrderByFailedAtDesc()).thenReturn(List.of(dlq));

        List<DeadLetterResponse> result = deadLetterService.listDeadLetterJobs();

        assertEquals(1, result.size());
        assertEquals(dlq.getOriginalJobId(), result.get(0).getOriginalJobId());
        assertEquals("Connection timed out", result.get(0).getFailureReason());
    }

    @Test
    @DisplayName("Should requeue a dead job and remove it from DLQ (Feature 7 manual retry)")
    void testRetryDeadLetterJobSuccess() {
        UUID originalJobId = UUID.randomUUID();
        UUID dlqId = UUID.randomUUID();

        DeadLetterJob dlq = new DeadLetterJob(
                originalJobId,
                "PROCESS_PAYMENT",
                "{\"amount\":500}",
                3,
                "Gateway rejected payment",
                LocalDateTime.now()
        );
        dlq.setId(dlqId);

        Job deadJob = new Job("PROCESS_PAYMENT", "{\"amount\":500}", null, 3);
        deadJob.setId(originalJobId);
        deadJob.setStatus(JobStatus.DEAD);
        deadJob.setAttemptCount(3);
        deadJob.setLastError("Gateway rejected payment");
        deadJob.setCompletedAt(LocalDateTime.now());

        when(deadLetterRepository.findByOriginalJobId(originalJobId)).thenReturn(Optional.of(dlq));
        when(jobRepository.findById(originalJobId)).thenReturn(Optional.of(deadJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        JobResponse response = deadLetterService.retryDeadLetterJob(originalJobId);

        assertNotNull(response);
        assertEquals(JobStatus.QUEUED, response.getStatus());
        assertEquals(0, response.getAttemptCount());
        assertNull(response.getLastError());
        assertNull(response.getCompletedAt());

        // Verify DLQ record was deleted
        verify(deadLetterRepository).delete(dlq);
        // Verify job state was saved
        verify(jobRepository).save(deadJob);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when dead letter job ID does not exist")
    void testRetryDeadLetterJobNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(deadLetterRepository.findByOriginalJobId(unknownId)).thenReturn(Optional.empty());
        when(deadLetterRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> deadLetterService.retryDeadLetterJob(unknownId));
    }
}
