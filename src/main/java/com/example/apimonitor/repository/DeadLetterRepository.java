package com.example.apimonitor.repository;

import com.example.apimonitor.entity.DeadLetterJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterJob, UUID> {

    Optional<DeadLetterJob> findByOriginalJobId(UUID originalJobId);

    boolean existsByOriginalJobId(UUID originalJobId);

    List<DeadLetterJob> findAllByOrderByFailedAtDesc();
}
