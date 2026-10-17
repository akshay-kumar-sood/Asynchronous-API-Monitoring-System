package com.example.apimonitor.repository;

import com.example.apimonitor.entity.Job;
import com.example.apimonitor.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);

    List<Job> findAllByOrderByCreatedAtDesc();

    List<Job> findByStatusAndUpdatedAtBefore(JobStatus status, LocalDateTime threshold);

    /**
     * Atomically selects and locks eligible jobs using PostgreSQL's FOR UPDATE SKIP LOCKED.
     * Prevents multiple concurrent workers from claiming the same job simultaneously.
     */
    @Query(value = """
            SELECT * FROM jobs 
            WHERE status IN ('QUEUED', 'RETRYING') 
              AND (next_retry_at IS NULL OR next_retry_at <= :now) 
            ORDER BY created_at ASC 
            LIMIT :limit 
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> findEligibleJobsForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
