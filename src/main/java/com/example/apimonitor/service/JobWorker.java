
package com.example.apimonitor.service;

import com.example.apimonitor.dto.HttpExecutionResult;
import com.example.apimonitor.entity.DeadLetterJob;
import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import com.example.apimonitor.exception.HttpExecutionException;
import com.example.apimonitor.repository.DeadLetterRepository;
import com.example.apimonitor.repository.JobRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class JobWorker {

    private static final Logger log =
            LoggerFactory.getLogger(JobWorker.class);

    private final JobRepository jobRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final Executor jobWorkerExecutor;
    private final ObjectMapper objectMapper;
    private final HttpJobExecutor httpJobExecutor;

    @Value("${app.worker.batch-size:5}")
    private int batchSize;

    @Value("${app.retry.initial-interval-seconds:1}")
    private long initialIntervalSeconds;

    @Value("${app.retry.backoff-multiplier:2}")
    private double backoffMultiplier;

    public JobWorker(
            JobRepository jobRepository,
            DeadLetterRepository deadLetterRepository,
            @Qualifier("jobWorkerExecutor")
            Executor jobWorkerExecutor,
            ObjectMapper objectMapper,
            HttpJobExecutor httpJobExecutor) {

        this.jobRepository = jobRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.jobWorkerExecutor = jobWorkerExecutor;
        this.objectMapper = objectMapper;
        this.httpJobExecutor = httpJobExecutor;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.worker.poll-interval-ms:1000}"
    )
    public void pollAndProcessJobs() {
        List<UUID> claimedJobIds = claimEligibleJobs();

        if (claimedJobIds.isEmpty()) {
            return;
        }

        log.debug(
                "Claimed {} jobs for processing",
                claimedJobIds.size()
        );

        for (UUID jobId : claimedJobIds) {
            jobWorkerExecutor.execute(
                    () -> executeJob(jobId)
            );
        }
    }

    @Transactional
    public List<UUID> claimEligibleJobs() {
        LocalDateTime now = LocalDateTime.now();

        List<Job> eligibleJobs =
                jobRepository.findEligibleJobsForUpdate(
                        now, batchSize
                );

        for (Job job : eligibleJobs) {
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
        }

        return eligibleJobs.stream()
                .map(Job::getId)
                .toList();
    }

    public void executeJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElse(null);

        if (job == null
                || job.getStatus() != JobStatus.PROCESSING) {
            return;
        }

        log.info(
                "Worker thread [{}] starting job ID: {}, type: {}, attempts: {}",
                Thread.currentThread().getName(),
                job.getId(),
                job.getType(),
                job.getAttemptCount()
        );

        try {
            Map<String, Object> payload =
                    job.getPayload() != null
                            && !job.getPayload().isBlank()
                            ? objectMapper.readValue(
                            job.getPayload(),
                            new TypeReference<Map<String, Object>>() {}
                    )
                            : Collections.emptyMap();

            if (payload.containsKey("url")) {
                log.info(
                        "Executing HTTP job {}: URL={}, method={}",
                        jobId,
                        payload.get("url"),
                        payload.getOrDefault("method", "GET")
                );

                HttpExecutionResult result =
                        httpJobExecutor.execute(payload);

                recordSuccess(
                        jobId, result.getStatusCode()
                );

                log.info(
                        "HTTP job {} succeeded with status {}",
                        jobId, result.getStatusCode()
                );

            } else {
                simulateTaskExecution(job, payload);
                recordSuccess(jobId, 200);

                log.info(
                        "Demonstration job {} succeeded",
                        jobId
                );
            }

        } catch (HttpExecutionException ex) {
            log.warn(
                    "HTTP job {} failed; status={}, reason={}",
                    jobId, ex.getStatusCode(), ex.getMessage()
            );

            recordFailure(
                    jobId,
                    ex.getMessage(),
                    ex.getStatusCode()
            );

        } catch (Exception ex) {
            log.warn(
                    "Job {} failed: {}",
                    jobId, ex.getMessage(), ex
            );

            recordFailure(
                    jobId, ex.getMessage(), null
            );
        }
    }

    private void simulateTaskExecution(
            Job job,
            Map<String, Object> payload) {

        if (payload == null || payload.isEmpty()) {
            return;
        }

        boolean failAlways =
                Boolean.TRUE.equals(payload.get("simulateFailure"))
                        || Boolean.TRUE.equals(payload.get("fail"));

        if (failAlways) {
            throw new RuntimeException(
                    "Simulated processing failure"
            );
        }

        Object failAttemptsObject =
                payload.get("simulateFailureAttempts");

        if (failAttemptsObject instanceof Number num) {
            int failUntilAttempt = num.intValue();
            int currentAttempt =
                    job.getAttemptCount() + 1;

            if (currentAttempt <= failUntilAttempt) {
                throw new RuntimeException(
                        "Simulated transient failure on attempt "
                                + currentAttempt
                                + " of " + failUntilAttempt
                );
            }
        }
    }

    @Transactional
    public void recordSuccess(
            UUID jobId, Integer statusCode) {

        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.SUCCESS);
            job.setCompletedAt(LocalDateTime.now());
            job.setResponseStatusCode(statusCode);
            job.setLastError(null);
            job.setNextRetryAt(null);

            jobRepository.save(job);
        });
    }

    @Transactional
    public void recordSuccess(UUID jobId) {
        recordSuccess(jobId, 200);
    }

    @Transactional
    public void recordFailure(
            UUID jobId,
            String errorMessage,
            Integer statusCode) {

        jobRepository.findById(jobId).ifPresent(job -> {
            int attempts = job.getAttemptCount() + 1;

            job.setAttemptCount(attempts);
            job.setLastError(errorMessage);
            job.setResponseStatusCode(statusCode);

            if (attempts >= job.getMaxAttempts()) {
                job.setStatus(JobStatus.DEAD);
                job.setCompletedAt(LocalDateTime.now());
                job.setNextRetryAt(null);

                jobRepository.save(job);

                if (!deadLetterRepository
                        .existsByOriginalJobId(job.getId())) {

                    DeadLetterJob dlq = new DeadLetterJob(
                            job.getId(),
                            job.getType(),
                            job.getPayload(),
                            attempts,
                            errorMessage,
                            LocalDateTime.now()
                    );

                    deadLetterRepository.save(dlq);

                    log.error(
                            "Job {} exhausted {} attempts and moved to DLQ",
                            job.getId(),
                            job.getMaxAttempts()
                    );
                }

            } else {
                long backoffSeconds = (long) (
                        initialIntervalSeconds
                                * Math.pow(
                                backoffMultiplier,
                                attempts - 1
                        )
                );

                LocalDateTime nextRetry =
                        LocalDateTime.now()
                                .plusSeconds(backoffSeconds);

                job.setStatus(JobStatus.RETRYING);
                job.setNextRetryAt(nextRetry);
                job.setCompletedAt(null);

                jobRepository.save(job);

                log.info(
                        "Job {} scheduled for retry attempt {} in {} seconds",
                        job.getId(),
                        attempts + 1,
                        backoffSeconds
                );
            }
        });
    }

    @Transactional
    public void recordFailure(
            UUID jobId, String errorMessage) {

        recordFailure(jobId, errorMessage, null);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void recoverStuckJobs() {
        LocalDateTime threshold =
                LocalDateTime.now().minusMinutes(5);

        List<Job> stuckJobs =
                jobRepository.findByStatusAndUpdatedAtBefore(
                        JobStatus.PROCESSING, threshold
                );

        for (Job job : stuckJobs) {
            log.warn(
                    "Recovering stuck job {} last updated at {}",
                    job.getId(), job.getUpdatedAt()
            );

            job.setStatus(JobStatus.RETRYING);
            job.setNextRetryAt(LocalDateTime.now());

            jobRepository.save(job);
        }
    }
}
