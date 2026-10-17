package com.example.apimonitor;

import com.example.apimonitor.controller.JobController;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.exception.GlobalExceptionHandler;
import com.example.apimonitor.repository.JobRepository;
import com.example.apimonitor.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    private MockMvc mockMvc;

    @Mock
    private JobRepository jobRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JobService jobService = new JobService(jobRepository, objectMapper, 3);
        JobController jobController = new JobController(jobService);

        mockMvc = MockMvcBuilders.standaloneSetup(jobController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/jobs returns HTTP 202 Accepted on valid request")
    void testSubmitJobAccepted() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findByIdempotencyKey("key-001")).thenReturn(Optional.empty());
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(jobId);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        });

        String jsonBody = """
                {
                    "type": "SEND_EMAIL",
                    "payload": {
                        "recipient": "demo@example.com",
                        "message": "Hello"
                    },
                    "idempotencyKey": "key-001"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.type").value("SEND_EMAIL"));
    }

    @Test
    @DisplayName("POST /api/jobs returns HTTP 400 Bad Request when job type is missing")
    void testSubmitJobValidationMissingType() throws Exception {
        String invalidJson = """
                {
                    "payload": { "message": "hello" }
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value("type: Job type is required"));
    }

    @Test
    @DisplayName("POST /api/jobs returns HTTP 400 Bad Request when payload is missing")
    void testSubmitJobValidationMissingPayload() throws Exception {
        String invalidJson = """
                {
                    "type": "SEND_EMAIL"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value("payload: Job payload is required"));
    }

    @Test
    @DisplayName("GET /api/jobs/{id} returns HTTP 404 when job does not exist")
    void testGetJobNotFound() throws Exception {
        UUID randomId = UUID.randomUUID();
        when(jobRepository.findById(randomId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/jobs/" + randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Job not found with ID: " + randomId));
    }

    @Test
    @DisplayName("POST /api/jobs with Idempotency-Key header returns HTTP 202 and existing job on repeated request")
    void testSubmitJobWithIdempotencyKeyHeader() throws Exception {
        UUID existingId = UUID.randomUUID();
        Job existingJob = new Job("HTTP_MONITOR", "{\"url\":\"https://api.example.com\"}", "header-key-777", 3);
        existingJob.setId(existingId);
        existingJob.setStatus(JobStatus.PROCESSING);
        existingJob.setCreatedAt(LocalDateTime.now());
        existingJob.setUpdatedAt(LocalDateTime.now());

        when(jobRepository.findByIdempotencyKey("header-key-777")).thenReturn(Optional.of(existingJob));

        String jsonBody = """
                {
                    "type": "HTTP_MONITOR",
                    "payload": {
                        "url": "https://api.example.com"
                    }
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .header("Idempotency-Key", "header-key-777")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(existingId.toString()))
                .andExpect(jsonPath("$.idempotencyKey").value("header-key-777"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }
}
