# Automated Test Plan & Test Execution Results

## 1. Overview
The test suite validates all 7 core system features and newly implemented enhancements. All 27 unit and integration tests run deterministically using **JUnit 5**, **Mockito**, and decoupled abstractions without requiring a live Neon PostgreSQL database or outbound internet access.

---

## 2. Test Verification Matrix

| # | Feature Under Test | Test Class | Test Method | Description |
|---|--------------------|------------|-------------|-------------|
| 1 | **Feature 1: Submission** | `JobControllerTest` | `testSubmitJobAccepted` | Validates `POST /api/jobs` returns HTTP 202 Accepted with assigned UUID. |
| 2 | **Validation: Missing Type** | `JobControllerTest` | `testSubmitJobValidationMissingType` | Validates HTTP 400 Bad Request with field validation errors when `type` is missing. |
| 3 | **Validation: Missing Payload**| `JobControllerTest` | `testSubmitJobValidationMissingPayload` | Validates HTTP 400 Bad Request when `payload` is missing. |
| 4 | **Feature 3: Status 404** | `JobControllerTest` | `testGetJobNotFound` | Validates HTTP 404 Not Found when requesting non-existent job UUID. |
| 5 | **Feature D: Idempotency Header**| `JobControllerTest` | `testSubmitJobWithIdempotencyKeyHeader` | Verifies `Idempotency-Key` HTTP header returns 202 and existing job ID. |
| 6 | **Feature 1: Persistence** | `JobServiceTest` | `testSubmitNewJob` | Verifies initial persistence with status `QUEUED`, attemptCount=0, maxAttempts=3. |
| 7 | **Feature 6 & D: Idempotency** | `JobServiceTest` | `testIdempotentSubmission` | Verifies identical key submission returns existing job without database write. |
| 8 | **Feature D: Concurrency Safe**| `JobServiceTest` | `testConcurrentDuplicateCollisionRecovery` | Verifies `DataIntegrityViolationException` race condition recovers winner job ID. |
| 9 | **Feature D: Header Precedence**| `JobServiceTest` | `testHeaderPrecedenceOverBodyKey` | Verifies `Idempotency-Key` header takes priority over payload body key. |
| 10 | **Feature 3: Job Tracking** | `JobServiceTest` | `testGetJobByIdSuccess` | Validates retrieving job details and status by UUID. |
| 11 | **Feature 3: Unknown Job 404** | `JobServiceTest` | `testGetJobByIdNotFound` | Validates `ResourceNotFoundException` is thrown for unknown UUID. |
| 12 | **Feature 3: Status Filter** | `JobServiceTest` | `testListJobsWithStatusFilter` | Validates filtering jobs by `JobStatus`. |
| 13 | **Feature 4 & E: Safe Claiming**| `JobWorkerTest` | `testClaimEligibleJobs` | Verifies atomic row locking and transition to `PROCESSING`. |
| 14 | **Feature 2: Task Success** | `JobWorkerTest` | `testRecordSuccess` | Verifies successful completion sets status to `SUCCESS` and records timestamp. |
| 15 | **Feature 5 & B: Backoff Retry**| `JobWorkerTest` | `testRecordFailureTransientRetry` | Verifies transient failure sets `RETRYING` and calculates exponential backoff. |
| 16 | **Feature 7 & C: DLQ Routing** | `JobWorkerTest` | `testRecordFailureMaxAttemptsExhausted`| Verifies exhausting attempts transitions to `DEAD` and inserts into DLQ. |
| 17 | **Feature A: HTTP Success** | `JobWorkerTest` | `testExecuteJobWithHttpSuccess` | Verifies real HTTP job execution sets status `SUCCESS` and records status code 200. |
| 18 | **Feature A & B: HTTP 502 Retry**| `JobWorkerTest` | `testExecuteJobWithHttpFailureRetry` | Verifies HTTP 502 Bad Gateway triggers backoff retry and records status code 502. |
| 19 | **Feature A: HTTP GET 200** | `HttpJobExecutorTest` | `testExecuteHttpGetSuccess` | Tests outbound GET request execution, duration tracking, and response parsing. |
| 20 | **Feature A: HTTP POST Body** | `HttpJobExecutorTest` | `testExecuteHttpPostWithBodyAndHeaders` | Tests outbound POST request with custom headers and JSON body serialization. |
| 21 | **Feature A: HTTP 500 Error** | `HttpJobExecutorTest` | `testExecuteHttpServerError500` | Verifies HTTP 500 throws `HttpExecutionException` with status code 500. |
| 22 | **Feature A: HTTP Timeout** | `HttpJobExecutorTest` | `testExecuteHttpTimeout` | Verifies request timeout throws `HttpExecutionException`. |
| 23 | **Security: Scheme Validation**| `HttpJobExecutorTest` | `testRejectUnsupportedScheme` | Rejects non-HTTP schemes (`ftp://`, `file:///`). |
| 24 | **Security: SSRF Protection** | `HttpJobExecutorTest` | `testSsrfProtectionBlocksLoopbackAndPrivateIps` | Blocks loopback (127.0.0.1) and AWS/GCP cloud metadata IP (169.254.169.254). |
| 25 | **Feature 7: DLQ Listing** | `DeadLetterServiceTest` | `testListDeadLetterJobs` | Validates listing dead-letter jobs ordered by failed timestamp descending. |
| 26 | **Feature 7: Manual Requeue** | `DeadLetterServiceTest` | `testRetryDeadLetterJobSuccess` | Validates atomic reset to `QUEUED`, attemptCount=0, and DLQ record deletion. |
| 27 | **Feature 7: Requeue 404** | `DeadLetterServiceTest` | `testRetryDeadLetterJobNotFound` | Validates 404 error when attempting to requeue non-existent DLQ record. |

---

## 3. Execution Command
Run the complete automated test suite via the Maven wrapper:
```powershell
.\mvnw.cmd test
```

---

## 4. Actual Maven Test Execution Log
```
[INFO] Scanning for projects...
[INFO] 
[INFO] -----------< com.example:asynchronous-api-monitoring-system >-----------
[INFO] Building asynchronous-api-monitoring-system 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ asynchronous-api-monitoring-system ---
[INFO] Copying 1 resource from src\main\resources to target\classes
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ asynchronous-api-monitoring-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.2.5:test (default-test) @ asynchronous-api-monitoring-system ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.example.apimonitor.DeadLetterServiceTest
20:19:53.824 [main] INFO com.example.apimonitor.service.DeadLetterService -- Successfully requeued dead job ID: fc234082-560a-448c-a48e-f22d297b95ae from DLQ
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.121 s -- in com.example.apimonitor.DeadLetterServiceTest
[INFO] Running com.example.apimonitor.HttpJobExecutorTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.072 s -- in com.example.apimonitor.HttpJobExecutorTest
[INFO] Running com.example.apimonitor.JobControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.895 s -- in com.example.apimonitor.JobControllerTest
[INFO] Running com.example.apimonitor.JobServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.033 s -- in com.example.apimonitor.JobServiceTest
[INFO] Running com.example.apimonitor.JobWorkerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.214 s -- in com.example.apimonitor.JobWorkerTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.121 s
[INFO] Finished at: 2026-10-17T20:19:55+05:30
[INFO] ------------------------------------------------------------------------
```
