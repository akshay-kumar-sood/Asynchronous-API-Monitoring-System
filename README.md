# Asynchronous API Monitoring and Background Job Processing System

A production-grade, fault-tolerant asynchronous job processing and HTTP API monitoring system built with **Java 21**, **Spring Boot 3.3.5**, and **PostgreSQL (compatible with local PostgreSQL or cloud Neon PostgreSQL)**.

---

## 1. Architectural Highlights

```
                                [ Client / HTTP Caller ]
                                            │
                                            ▼
                    POST /api/jobs (Header: Idempotency-Key: abc-123)
                                            │
                                            ▼
                                   [ JobController ]
                                            │
                                            ▼
                                    [ JobService ]
                                            │ (Persists job with status=QUEUED)
                                            ▼
                            ┌───────────────────────────────┐
                            │    PostgreSQL (jobs table)    │
                            └───────────────────────────────┘
                                            ▲
                                            │ SELECT ... FOR UPDATE SKIP LOCKED
                                            │ (Atomic claiming in short transaction)
                                            ▼
                                   [ JobWorker Poller ]
                                            │
                                            ▼ (Non-blocking async task dispatch)
                               [ ThreadPoolTaskExecutor ]
                                 (Bounded: 4 core, 8 max)
                                            │
                                            ▼
                                   [ HttpJobExecutor ]
                            (SSRF mitigation + Timeout checks)
                                            │
                             ┌──────────────┴──────────────┐
                             │                             │
                     HTTP 2xx (Success)            HTTP 4xx/5xx / Timeout (Failure)
                             │                             │
                             ▼                             ▼
                 Update status = SUCCESS       Attempts < max? ──Yes──► Update status = RETRYING
                 Record response_status_code                            Calculate next_retry_at (1s, 2s, 4s)
                                                           │
                                                           └──No───► Update status = DEAD
                                                                     Insert into dead_letter_jobs (DLQ)
                                                                     Requeue via POST /api/dlq/{id}/retry
```

### Key Engineering Features:
- **Decoupled Job Ingestion**: `POST /api/jobs` returns `HTTP 202 Accepted` immediately with the assigned UUID. The HTTP request never blocks waiting for the task to finish.
- **Real Outbound HTTP Monitoring**: Supports executing GET, POST, PUT, DELETE, and PATCH requests to remote endpoints, tracking response status codes and durations.
- **SSRF Attack Mitigation**: Automatically blocks requests directed to loopback (`127.0.0.1`, `localhost`, `::1`), private networks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local IPs, and cloud metadata services (`169.254.169.254`, `metadata.google.internal`).
- **Sensitive Header Redaction**: Credentials, tokens, cookies, and authorization headers are masked before logging (`******`).
- **Concurrency-Safe Job Claiming**: Uses PostgreSQL's native `FOR UPDATE SKIP LOCKED`. Multiple workers or instances never block each other or claim the same job twice.
- **Connection Isolation**: Database transactions are committed *before* invoking external HTTP endpoints. Database connections are not tied up during external network latency.
- **Non-Blocking Exponential Backoff**: Transient failures schedule `next_retry_at` using $1 \times 2^{\text{attempt}-1}$ seconds. Worker threads are freed immediately (no `Thread.sleep`).
- **Idempotency with Header & DB Constraints**: Accepts the standard `Idempotency-Key` HTTP header. Deduplication is enforced at the database layer via a unique constraint. Concurrent duplicates are handled safely via `DataIntegrityViolationException` recovery.
- **Dead Letter Queue (DLQ)**: Jobs exhausting all retry attempts are moved to the `dead_letter_jobs` table. Authorized callers can requeue dead jobs atomically with `POST /api/dlq/{jobId}/retry`.

---

## 2. Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.3.5
- **Persistence**: Spring Data JPA / Hibernate
- **Database**: PostgreSQL / Neon Cloud PostgreSQL
- **HTTP Client**: Java 21 `java.net.http.HttpClient` with `HttpSender` abstraction
- **Testing**: JUnit 5, Mockito, Spring Boot Starter Test (27 automated tests)
- **API Tooling**: Postman Collection (`postman_collection.json`)

---

## 3. Database Schema & Migration Instructions

Spring Boot automatically creates and updates tables using `spring.jpa.hibernate.ddl-auto=update`.

If creating or altering the schema manually in PostgreSQL or Neon, execute:

```sql
-- 1. Jobs Table
CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,
    response_status_code INTEGER,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jobs_status_next_retry ON jobs (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_jobs_created_at ON jobs (created_at);

-- 2. Dead Letter Jobs Table
CREATE TABLE IF NOT EXISTS dead_letter_jobs (
    id UUID PRIMARY KEY,
    original_job_id UUID NOT NULL UNIQUE,
    type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    attempt_count INTEGER NOT NULL,
    failure_reason TEXT,
    failed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dlq_failed_at ON dead_letter_jobs (failed_at);

-- 3. Non-destructive migration if upgrading existing jobs table:
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS response_status_code INTEGER;
```

---

## 4. Environment Variables Configuration

Set database credentials in your environment without committing secrets:

### For Local PostgreSQL:
```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/apimonitor"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your-postgres-password"
```

### For Neon Cloud PostgreSQL:
```powershell
$env:DB_URL="jdbc:postgresql://ep-example-pool.us-east-2.aws.neon.tech/neondb?sslmode=require"
$env:DB_USERNAME="neondb_owner"
$env:DB_PASSWORD="your-neon-password"
```

---

## 5. Build and Run Commands (Windows PowerShell)

### Run Automated Tests:
```powershell
.\mvnw.cmd test
```

### Start Application Locally:
```powershell
.\mvnw.cmd spring-boot:run
```

---

## 6. REST API Reference

### 6.1. Submit a Job
`POST /api/jobs`  
**Headers**:
- `Content-Type: application/json`
- `Idempotency-Key: <optional-unique-string>`

#### Request Body (Outbound HTTP Monitor Job):
```json
{
  "type": "HTTP_MONITOR",
  "payload": {
    "url": "https://httpbin.org/status/200",
    "method": "GET"
  }
}
```

#### Request Body (Outbound HTTP Webhook with Headers & Body):
```json
{
  "type": "WEBHOOK_DISPATCH",
  "payload": {
    "url": "https://httpbin.org/post",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json",
      "X-Api-Key": "secret-token"
    },
    "body": {
      "event": "PAYMENT_CONFIRMED",
      "amount": 250.00
    }
  }
}
```

#### Response (`HTTP 202 Accepted`):
```json
{
  "id": "e4b95f2d-8ac1-432a-bc90-df85764019ab",
  "type": "HTTP_MONITOR",
  "payload": {
    "url": "https://httpbin.org/status/200",
    "method": "GET"
  },
  "idempotencyKey": "monitor-health-001",
  "status": "QUEUED",
  "attemptCount": 0,
  "maxAttempts": 3,
  "nextRetryAt": "2026-10-17T20:25:00",
  "responseStatusCode": null,
  "lastError": null,
  "createdAt": "2026-10-17T20:25:00",
  "updatedAt": "2026-10-17T20:25:00",
  "completedAt": null
}
```

---

### 6.2. Retrieve Job Status
`GET /api/jobs/{id}`

#### Response (`HTTP 200 OK`):
```json
{
  "id": "e4b95f2d-8ac1-432a-bc90-df85764019ab",
  "type": "HTTP_MONITOR",
  "payload": {
    "url": "https://httpbin.org/status/200",
    "method": "GET"
  },
  "idempotencyKey": "monitor-health-001",
  "status": "SUCCESS",
  "attemptCount": 0,
  "maxAttempts": 3,
  "nextRetryAt": "2026-10-17T20:25:00",
  "responseStatusCode": 200,
  "lastError": null,
  "createdAt": "2026-10-17T20:25:00",
  "updatedAt": "2026-10-17T20:25:01",
  "completedAt": "2026-10-17T20:25:01"
}
```

---

### 6.3. List Jobs (with Optional Status Filter)
- List all jobs: `GET /api/jobs`
- Filter by status: `GET /api/jobs?status=RETRYING` (options: `QUEUED`, `PROCESSING`, `SUCCESS`, `RETRYING`, `FAILED`, `DEAD`)

---

### 6.4. List Dead Letter Queue (DLQ) Records
`GET /api/dlq`

#### Response (`HTTP 200 OK`):
```json
[
  {
    "id": "7a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d",
    "originalJobId": "9f1e2d3c-4b5a-6789-0123-abcdef456789",
    "type": "HTTP_MONITOR",
    "payload": {
      "url": "https://httpbin.org/status/500",
      "method": "GET"
    },
    "attemptCount": 3,
    "failureReason": "HTTP request failed with status code 500",
    "failedAt": "2026-10-17T20:25:07"
  }
]
```

---

### 6.5. Manually Requeue a Dead Job from DLQ
`POST /api/dlq/{jobId}/retry`

#### Response (`HTTP 200 OK`):
```json
{
  "id": "9f1e2d3c-4b5a-6789-0123-abcdef456789",
  "type": "HTTP_MONITOR",
  "status": "QUEUED",
  "attemptCount": 0,
  "responseStatusCode": null,
  "lastError": null,
  "completedAt": null
}
```
*Note: The DLQ entry is deleted inside the same transaction, guaranteeing that the job is not duplicated in the DLQ.*

---

## 7. How the 7 Core Features Work

1. **Job Submission API**: Validates incoming payload using Jakarta Bean Validation. Submissions return `HTTP 202 Accepted` immediately with the assigned UUID.
2. **Asynchronous Job Processing**: Jobs are executed in a Spring-managed `ThreadPoolTaskExecutor` (4 core, 8 max threads). Job submission and job execution are completely decoupled.
3. **Job Status Tracking**: Tracks `QUEUED`, `PROCESSING`, `SUCCESS`, `RETRYING`, `FAILED`, and `DEAD`. Non-existent IDs return `HTTP 404 Not Found`.
4. **Multiple Concurrent Workers**: Bounded thread pool claims jobs using PostgreSQL's `FOR UPDATE SKIP LOCKED`. Multiple workers claim disjoint batches without locking each other or executing duplicates.
5. **Automatic Retries with Exponential Backoff**: Transient HTTP errors (5xx, 429, timeouts) calculate `next_retry_at = NOW() + (initialInterval * multiplier^(attempts-1))` (1s, 2s, 4s). Worker threads are never blocked with `Thread.sleep`.
6. **Idempotency**: Enforced via PostgreSQL `UNIQUE` constraint on `idempotency_key`. Repeating the same key returns the existing job ID. Concurrent collisions are caught via `DataIntegrityViolationException` and return the winning record.
7. **Dead Letter Queue (DLQ)**: Permanently failed jobs are stored in `dead_letter_jobs`. `POST /api/dlq/{jobId}/retry` transactionally resets the job to `QUEUED` and removes the DLQ entry.
