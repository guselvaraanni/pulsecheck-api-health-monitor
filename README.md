# PulseCheck — API & Service Health Monitor

A backend-only Spring Boot application that monitors the health of other services. It checks registered
URLs concurrently on a schedule, records response times, tracks outages as incidents, and reports
availability metrics through a REST API backed by PostgreSQL.

**Java 21 · Spring Boot 4 · Spring MVC · Spring Data JPA / Hibernate · PostgreSQL · Postman**

---

## Table of Contents
1. [Overview](#1-overview)
2. [Problem Statement](#2-problem-statement)
3. [Features](#3-features)
4. [Architecture](#4-architecture)
5. [Tech Stack](#5-tech-stack)
6. [Database Design](#6-database-design)
7. [API Reference](#7-api-reference)
8. [Example Requests and Responses](#8-example-requests-and-responses)
9. [Running Locally](#9-running-locally)
10. [Configuration](#10-configuration)
11. [Key Java Concepts Demonstrated](#11-key-java-concepts-demonstrated)
12. [Testing](#12-testing)
13. [Known Limitations and Future Improvements](#13-known-limitations-and-future-improvements)

---

## 1. Overview

Users register the services they want to watch (a name and a URL). PulseCheck then:

1. sends an HTTP `GET` to every active service — automatically every 30 seconds, or on demand,
2. runs those checks **in parallel** on a fixed-size thread pool,
3. stores each result (`UP`/`DOWN`, response time, error message) in PostgreSQL,
4. opens an **incident** when a service goes down and closes it when the service recovers,
5. calculates **availability %**, failure counts and average response time over a time window.

Everything is exposed through a REST API; there is no frontend.

## 2. Problem Statement

Modern systems depend on many services (payments, auth, inventory, notifications). When one fails
silently, users usually notice before engineers do. Teams need to answer:

- Is the service up **right now**?
- **When** did it fail, for **how long**, and **why**?
- How **reliable** is it over time (availability %, average latency)?

PulseCheck answers these questions with a small, understandable layered monolith.

## 3. Features

- **Service management** — create, list, view, update, delete monitored services; duplicate names rejected (case-insensitive).
- **Real HTTP health checks** — JDK `HttpClient` with connect and request timeouts. 2xx/3xx = `UP`;
  other status codes, timeouts, DNS and connection failures = `DOWN` with a readable error message.
- **Concurrent checking** — all active services are checked in parallel on a 4-thread pool
  (8 services × 1 s each: ~3.3 s with 4 threads vs ~10.7 s with 1 thread).
- **Scheduled monitoring** — `@Scheduled` background runs with a configurable interval; a guard
  guarantees only one run (scheduled or manual) at a time.
- **Incident tracking** — automatic open/resolve on state changes, never duplicate open incidents per run.
- **Check history and analysis** — paginated history, current health, and a summary with status counts,
  consecutive failures, fastest/slowest response and most common errors.
- **Metrics** — per-service and overall availability, failure counts and average response time for a 1–168 hour window.
- **Consistent error handling** — one JSON error format for every 4xx/5xx, including field-level validation errors.
- **Tests** — 42 JUnit tests (unit, JPA against real PostgreSQL, transaction rollback, concurrency)
  plus a 45-request Postman collection.

## 4. Architecture

Layered monolith: `controller → service → repository → PostgreSQL`.

```
        Client (Postman)                 MonitoringScheduler (@Scheduled)
               │                                     │
               ▼                                     │
        ┌──────────────┐                             │
        │  Controller  │  HTTP, validation, status codes, DTOs
        └──────┬───────┘                             │
               ▼                                     ▼
        ┌─────────────────────────────────────────────────┐
        │                  Service layer                  │
        │  MonitoredServiceService   HealthCheckService   │  business rules,
        │  IncidentService           MetricsService       │  transactions,
        │  ParallelHealthChecker ──▶ HttpHealthChecker ───┼──▶ monitored URLs
        │  (ExecutorService, 4 threads)                   │    (java.net.http)
        └──────────────────────┬──────────────────────────┘
                               ▼
                 ┌──────────────────────────┐
                 │ Repository (Spring Data) │
                 └────────────┬─────────────┘
                              ▼
                          PostgreSQL
```

### Monitoring cycle (manual or scheduled)

```
runAllActiveChecks()
 ├─ guard: AtomicBoolean.compareAndSet(false, true)   → 409 / skipped if a run is in progress
 ├─ load active services                              (calling thread, DB)
 ├─ submit one HTTP check per service to the pool     (health-check-1..4 threads, no DB work)
 ├─ wait for all Futures                              (total time ≈ slowest check, not the sum)
 └─ for each result: one @Transactional unit          (calling thread)
       ├─ save HealthCheck
       └─ update incident: DOWN & none open → open · UP & one open → resolve
```

Worker threads only do network I/O; all database work stays on the calling thread, so a slow
service never holds a database connection or transaction open.

### Package structure

```
com.pulsecheck
├── controller   REST endpoints — HTTP concerns only
├── service      business logic, transactions, HTTP checks, concurrency, scheduling
├── repository   Spring Data JPA repositories
├── entity       JPA entities
├── dto          request/response records (entities are never returned directly)
├── exception    custom exceptions + global exception handler
└── config       HTTP client, thread pool, scheduling and typed configuration
```

**Why layered?** Each layer has one responsibility, the REST endpoint and the scheduler reuse the same
service code, and a single deployable is the right size for this problem — microservices would add
network calls and operational cost without any benefit.

## 5. Tech Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1, Spring MVC |
| Persistence | Spring Data JPA, Hibernate 7 |
| Database | PostgreSQL |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| HTTP checks | `java.net.http.HttpClient` (JDK, no extra library) |
| Testing | JUnit 5, Mockito, Spring Boot Test, Postman / Newman |
| Build | Maven (via Maven Wrapper) |
| Version control | Git |

Intentionally **not** used: Docker, Redis, Kafka, cloud services, microservices, frontend frameworks.

## 6. Database Design

```
monitored_services 1 ────< health_checks
monitored_services 1 ────< incidents
```

Tables are generated by Hibernate from the entities (`ddl-auto=update`).

**`monitored_services`** — a service being monitored (entity `MonitoredService`; named so to avoid
confusion with Spring's `@Service`).

| Column | Type | Notes |
|---|---|---|
| id | bigint | primary key (identity) |
| name | varchar(100) | unique, required |
| url | varchar(2048) | required, `http://` or `https://` |
| description | varchar(500) | optional |
| active | boolean | only active services are checked by run-all / the scheduler |
| created_at | timestamp | set on first save (`@PrePersist`) |

**`health_checks`** — one check attempt.

| Column | Type | Notes |
|---|---|---|
| id | bigint | primary key |
| service_id | bigint | FK → monitored_services, `ON DELETE CASCADE` |
| status | varchar | `UP` / `DOWN` (stored as text, not ordinal) |
| response_time_ms | bigint | null when no response was received |
| checked_at | timestamp | |
| error_message | varchar(1000) | null when UP |

Index `(service_id, checked_at)` — supports history, latest check and time-window queries.

**`incidents`** — a period during which a service was failing.

| Column | Type | Notes |
|---|---|---|
| id | bigint | primary key |
| service_id | bigint | FK → monitored_services, `ON DELETE CASCADE` |
| started_at | timestamp | time of the first failed check |
| ended_at | timestamp | `null` while the incident is open |
| reason | varchar(1000) | error from the first failed check |

Index `(service_id, ended_at)` — finds a service's open incident quickly.

| Previous state | New check | Incident action |
|---|---|---|
| UP / no history | DOWN | open a new incident |
| DOWN | DOWN | keep the existing one open |
| DOWN | UP | resolve it (`ended_at = now`) |
| UP | UP | nothing |

Design choices:
- **Metrics are calculated** from stored checks, not stored separately, so they always match the raw data.
- **`@ManyToOne(LAZY)`** on both child tables; listing incidents uses `@EntityGraph` to fetch the service in one JOIN (no N+1 queries).
- **Database-level cascade delete** — deleting a service removes its checks and incidents in PostgreSQL without loading them into memory.

## 7. API Reference

Base path: `/api`

| Category | Method & Path | Success | Purpose |
|---|---|---|---|
| System | `GET /api/ping` | 200 | application liveness |
| Services | `POST /api/services` | 201 + `Location` | register a service |
| | `GET /api/services` | 200 | list services |
| | `GET /api/services/{id}` | 200 | get one service |
| | `PUT /api/services/{id}` | 200 | update a service (`active` optional) |
| | `DELETE /api/services/{id}` | 204 | delete a service and its history |
| Health checks | `POST /api/services/{id}/checks` | 201 | check one service now |
| | `POST /api/checks/run` | 200 | check all active services concurrently |
| | `GET /api/services/{id}/checks?page=0&size=20` | 200 | paginated history, newest first (size ≤ 100) |
| | `GET /api/services/{id}/checks/summary?limit=50` | 200 | status counts, failure streak, top errors (limit ≤ 500) |
| | `GET /api/services/{id}/health` | 200 | current status: `UP`, `DOWN` or `UNKNOWN` |
| Incidents | `GET /api/incidents?open=false&page=0&size=20` | 200 | incidents across services (`open=true` for ongoing) |
| | `GET /api/services/{id}/incidents?page=0&size=20` | 200 | incidents for one service |
| Metrics | `GET /api/services/{id}/metrics?hours=24` | 200 | availability, failures, average response time |
| | `GET /api/metrics?hours=24` | 200 | all services, worst availability first (hours 1–168) |

Error status codes: `400` invalid input · `404` unknown service or path · `405` wrong method ·
`409` duplicate name, or a check run already in progress · `500` unexpected error.

A `DOWN` result is still a successful API call (`201`): the check ran and was recorded.

## 8. Example Requests and Responses

All examples are real responses from the running application.

**Register a service** — `POST /api/services`

```json
{ "name": "payments-api", "url": "https://example.com", "description": "Payment gateway" }
```
`201 Created`, `Location: /api/services/129`
```json
{
  "id": 129,
  "name": "payments-api",
  "url": "https://example.com",
  "description": "Payment gateway",
  "active": true,
  "createdAt": "2026-10-08T07:30:03.285454600Z"
}
```

**Check a service** — `POST /api/services/129/checks` → `201 Created`
```json
{ "id": 273, "status": "UP", "responseTimeMs": 424, "checkedAt": "2026-10-08T07:30:03.364932100Z", "errorMessage": null }
```

A service that cannot be reached:
```json
{ "id": 274, "status": "DOWN", "responseTimeMs": null, "checkedAt": "2026-10-08T07:30:03.824432700Z", "errorMessage": "Connection failed: ClosedChannelException" }
```

**Incidents for the failing service** — `GET /api/services/130/incidents`
```json
{
  "content": [
    {
      "id": 30,
      "serviceId": 130,
      "serviceName": "inventory-api",
      "startedAt": "2026-10-08T07:30:03.824433Z",
      "endedAt": null,
      "open": true,
      "durationSeconds": 0,
      "reason": "Connection failed: ClosedChannelException"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**Metrics** — `GET /api/services/129/metrics?hours=24`
```json
{
  "serviceId": 129,
  "serviceName": "payments-api",
  "from": "2026-10-07T07:30:03.956998600Z",
  "to": "2026-10-08T07:30:03.956998600Z",
  "totalChecks": 2,
  "successfulChecks": 2,
  "failedChecks": 0,
  "availabilityPercent": 100.0,
  "averageResponseTimeMs": 237
}
```

**Validation error** — `POST /api/services` with `{"name":"","url":"not-a-url"}` → `400 Bad Request`
```json
{
  "timestamp": "2026-10-08T07:30:03.986307900Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/services",
  "fieldErrors": { "url": "URL must start with http:// or https://", "name": "Name is required" }
}
```

**Not found** — `GET /api/services/999999` → `404 Not Found`
```json
{ "timestamp": "2026-10-08T07:30:04.014500200Z", "status": 404, "error": "Not Found", "message": "Service with id 999999 not found", "path": "/api/services/999999" }
```

**Run already in progress** — `POST /api/checks/run` during another run → `409 Conflict`
```json
{ "timestamp": "2026-10-08T07:14:53.605712600Z", "status": 409, "error": "Conflict", "message": "A check run is already in progress; try again when it finishes", "path": "/api/checks/run" }
```

## 9. Running Locally

**Requirements:** Java 21 (JDK) and PostgreSQL. Maven does not need to be installed — the Maven Wrapper downloads it.

1. Create the database:
   ```sql
   CREATE DATABASE pulsecheck;
   ```
2. Copy `.env.example` to `.env` and fill in your PostgreSQL credentials (`.env` is git-ignored).
3. Start the application:
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run
   # macOS / Linux
   ./mvnw spring-boot:run
   ```
4. Open `http://localhost:8080/api/ping` (or the `SERVER_PORT` from `.env`).

Tables are created automatically on startup. Run the tests with `.\mvnw.cmd verify`.

## 10. Configuration

Secrets and machine-specific values come from environment variables, loaded from a local `.env` file
(`spring.config.import=optional:file:.env[.properties]`). Real environment variables take precedence.

| `.env` variable | Example | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port (default 8080) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/pulsecheck` | JDBC URL |
| `DB_USERNAME` | `postgres` | database user |
| `DB_PASSWORD` | `change-me` | database password |

Application settings in `src/main/resources/application.properties`:

| Property | Default | Meaning |
|---|---|---|
| `pulsecheck.health-check.connect-timeout` | `3s` | max time to open a connection |
| `pulsecheck.health-check.request-timeout` | `5s` | max time to wait for the full response |
| `pulsecheck.health-check.thread-pool-size` | `4` | services checked at the same time |
| `pulsecheck.monitoring.enabled` | `true` | turn scheduled monitoring on/off |
| `pulsecheck.monitoring.interval` | `30s` | pause between the end of one run and the start of the next (min `1s`) |
| `pulsecheck.monitoring.initial-delay` | `10s` | wait after startup before the first run |

Invalid monitoring values (e.g. `interval=100ms`) stop the application at startup with a clear message.

## 11. Key Java Concepts Demonstrated

| Concept | Where |
|---|---|
| **OOP** — encapsulation, behaviour on the entity | `Incident.resolve()` / `isOpen()` guard state changes; entities expose no public no-arg constructor |
| **OOP** — inheritance and polymorphism | custom exceptions extend `RuntimeException`; `GlobalExceptionHandler` handles Spring's `ErrorResponse` types polymorphically |
| **Records & immutability** | 17 records: DTOs, `ApiError`, `CheckResult`, `AvailabilityStats`, `MonitoringProperties` |
| **Collections** | `EnumMap` for status counts, `LinkedHashMap` to keep result order, `groupingBy` into `Map<Long, List<HealthCheck>>` |
| **Generics** | `PageResponse<T>.from(Page<T>)`; `ParallelHealthChecker.<T> checkAll(List<T>, Function<T, String>)` |
| **Streams** | `HealthCheckAnalyzer` (`groupingBy`, `counting`, `takeWhile`, `LongStream`, `OptionalLong`), `MetricsService`, `AvailabilityStats` |
| **Lambdas & method references** | `Comparator.comparing(...).thenComparing(...)`, `MonitoredService::getUrl`, `orElseThrow(() -> ...)` |
| **Multithreading** | fixed thread pool `ExecutorService` bean with named threads; tasks submitted as `Callable` lambdas returning a `CheckResult`; `Future.get()` |
| **Concurrency** | submit-all-then-wait; `InterruptedException` handling (interrupt flag restored); `AtomicBoolean.compareAndSet` run guard; scheduler and request threads sharing one service |
| **Exception handling** | custom exceptions → `@RestControllerAdvice` → one `ApiError` format; timeouts/IO failures turned into `DOWN` results |
| **Transactions** | `@Transactional` per service result in `CheckResultRecorder` (separate bean to avoid the self-invocation trap); rollback proven by a test |
| **Spring Data JPA** | derived queries, `Pageable`, `Limit`, `@EntityGraph`, dirty checking, lazy relationships |
| **Scheduling & configuration** | `@Scheduled(fixedDelay)`, `@ConfigurationProperties` record with fail-fast validation, `@ConditionalOnProperty` |
| **Layered architecture** | controller → service → repository, DTOs at the API boundary, constructor injection everywhere |

## 12. Testing

**JUnit (42 tests)** — `.\mvnw.cmd verify`

| Test | What it proves |
|---|---|
| `MonitoredServiceRepositoryTest`, `HealthCheckRepositoryTest` | queries work against real PostgreSQL (`@DataJpaTest`, rolled back) |
| `HttpHealthCheckerTest` | UP / HTTP error / timeout / connection failure classification (local JDK `HttpServer`) |
| `HealthCheckAnalyzerTest`, `AvailabilityStatsTest` | stream calculations and percentage edge cases |
| `ParallelHealthCheckerTest` | checks run in parallel, results keep input order, pool size is respected |
| `CheckResultRecorderTest` | incident open / keep / resolve transitions |
| `CheckResultRecorderRollbackTest` | a failure while updating incidents also rolls back the saved check |
| `RunAllChecksGuardTest` | a second concurrent run is rejected; the guard is released after failures |
| `MonitoringSchedulerTest`, `MonitoringPropertiesTest` | scheduler survives errors; invalid settings are rejected |

**Postman** — `postman/PulseCheck.postman_collection.json` (45 requests, ~68 assertions): success
cases, validation errors, not-found cases, failing services, concurrent run-all, incidents and metrics.
It creates its own data and deletes it at the end.

1. Postman → **Import** → select the file, set the collection variable `baseUrl`.
2. Run the collection in order with the **Collection Runner** (needs internet for example.com and httpbin.org).

Or from the command line with Postman's CLI runner (requires Node.js):
```bash
npx newman run postman/PulseCheck.postman_collection.json --env-var baseUrl=http://localhost:8080
```

## 13. Known Limitations and Future Improvements

Known limitations (deliberate, to keep the project focused):
- **Single instance** — the run guard is in memory. Running several instances would need a database lock
  (e.g. ShedLock) or a dedicated worker.
- **Manual single-service checks** (`POST /services/{id}/checks`) are not covered by the run guard; two
  simultaneous checks of the same failing service could open two incidents. A partial unique index on
  `incidents(service_id) WHERE ended_at IS NULL` would enforce this in the database.
- **Metrics are computed in memory** from the checks in the window (capped at 7 days). Very large
  volumes would need SQL aggregation or pre-computed rollups.
- **Schema managed by Hibernate** (`ddl-auto=update`); production would use migrations (Flyway / Liquibase).

Possible improvements:
- Per-service check settings (expected status code, timeout, interval).
- Notifications when an incident opens or resolves (email / webhook).
- Retention job to delete old health checks.
- Authentication for the API (Spring Security).
- Response-time percentiles (p95 / p99) in metrics.
