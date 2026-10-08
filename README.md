# PulseCheck — API & Service Health Monitor

> Status: **Stage 11 — Scheduled monitoring.**
> This document describes the planned design and will be expanded as stages are completed.

## Quick Start

Requirements: Java 21 and PostgreSQL. Maven does not need to be installed — the Maven Wrapper downloads it.

1. Create the database:
   ```sql
   CREATE DATABASE pulsecheck;
   ```
2. Copy `.env.example` to `.env` and set your PostgreSQL credentials. `.env` is git-ignored.
3. Run the application:
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run

   # macOS / Linux
   ./mvnw spring-boot:run
   ```
4. Open `http://localhost:8080/api/ping` (or the `SERVER_PORT` set in `.env`).

Tables are created automatically by Hibernate on startup. Run tests with `.\mvnw.cmd verify`
(tests use the same database and roll back their changes).

## 1. Overview

PulseCheck is a backend-only Spring Boot application that monitors the health of other backend services.

Users register services (name + URL). PulseCheck periodically or on demand sends HTTP requests to those
URLs, records whether each service is **UP** or **DOWN** and how long it took to respond, tracks
**incidents** (periods of failure), and exposes **availability metrics** — all through REST APIs.

## 2. Problem Statement

Modern systems depend on many services (payments, auth, inventory, notifications). When one of them
fails silently, users usually notice before engineers do. Teams need to answer:

- Is the service up **right now**?
- **When** did it fail, and for **how long**?
- How **reliable** is it over time (uptime %, average latency)?

PulseCheck answers these questions with a small, understandable monolith.

## 3. Tech Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4, Spring MVC |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| HTTP checks | `java.net.http.HttpClient` (built into the JDK) |
| Build | Maven (via Maven Wrapper) |
| API testing | Postman |
| Version control | Git |

Intentionally **not** used: Docker, Redis, Kafka, cloud services, microservices, frontend frameworks.

## 4. Domain Model

```
MonitoredService 1 ──── * HealthCheck
MonitoredService 1 ──── * Incident
```

### MonitoredService
A backend service being monitored. (Named `MonitoredService` rather than `Service` to avoid confusion
with Spring's `@Service` annotation and the service layer.)

| Field | Type | Notes |
|---|---|---|
| id | Long | primary key |
| name | String | unique, required |
| url | String | required, valid HTTP/HTTPS URL |
| description | String | optional |
| active | boolean | only active services are checked automatically |
| createdAt | Instant | set when first saved |

### HealthCheck
One attempt to check a service.

| Field | Type | Notes |
|---|---|---|
| id | Long | primary key |
| service | MonitoredService | foreign key (`@ManyToOne`) |
| status | enum `UP` / `DOWN` | |
| responseTimeMs | Long | measured round-trip time |
| checkedAt | Instant | |
| errorMessage | String | null when UP |

### Incident
A period during which a service was failing.

| Field | Type | Notes |
|---|---|---|
| id | Long | primary key |
| service | MonitoredService | foreign key (`@ManyToOne`) |
| startedAt | Instant | first failed check |
| endedAt | Instant | `null` while the incident is still open |
| reason | String | failure reason from the first failed check |

### Incident state transitions

| Previous state | New check | Action |
|---|---|---|
| UP (or no history) | DOWN | open a new incident |
| DOWN | DOWN | keep existing incident open (no duplicate) |
| DOWN | UP | close incident (`endedAt = now`) |
| UP | UP | nothing |

## 5. Architecture

Layered monolith:

```
        Client (Postman)            Scheduler (@Scheduled)
               │                            │
               ▼                            │
        ┌──────────────┐                    │
        │  Controller  │  HTTP, validation, status codes, DTOs
        └──────┬───────┘                    │
               ▼                            ▼
        ┌──────────────────────────────────────┐
        │              Service layer           │  business rules, transactions,
        │  service mgmt · health checks ·      │  concurrency, incidents, metrics
        │  incidents · metrics                 │
        └──────┬───────────────────────┬───────┘
               ▼                       ▼
        ┌──────────────┐        ┌──────────────┐
        │  Repository  │        │  HttpClient  │──▶ monitored service URLs
        │ (Spring Data)│        └──────────────┘
        └──────┬───────┘
               ▼
          PostgreSQL
```

### Package structure

```
com.pulsecheck
├── controller   REST endpoints — HTTP concerns only
├── service      business logic, transactions, health-check execution
├── repository   Spring Data JPA repositories
├── entity       JPA entities
├── dto          request/response objects
├── exception    custom exceptions + global exception handler
├── config       beans and configuration (thread pool, HTTP client, scheduling)
└── util         small shared helpers (only if genuinely needed)
```

### Why layered architecture?

- **Separation of responsibilities** — controllers handle HTTP, services hold logic, repositories handle persistence.
- **Reuse** — the REST endpoint and the scheduler call the same service-layer code.
- **Changeability** — replacing the HTTP client or a query does not affect controllers.
- **Right-sized** — a single team, single deployable; microservices would add cost without benefit.

## 6. Request Flows

### Registering a service
```
POST /api/services → Controller (@Valid DTO) → Service (duplicate check) → Repository.save → PostgreSQL
```

### Running a monitoring cycle (manual or scheduled)
1. Load active services from the database.
2. Submit one health-check task per service to a **fixed-size thread pool** (`ExecutorService`).
3. Each task sends an HTTP GET with a timeout and measures elapsed time.
4. 2xx/3xx → `UP`; error status, timeout, or connection failure → `DOWN` with an error message.
5. Save the `HealthCheck` result.
6. Update incidents based on the state transition table above.

The scheduler starts a cycle automatically (`fixedDelay`: the interval is counted from the end of the
previous run). Only one cycle runs at a time: a manual `POST /api/checks/run` during a scheduled run
returns `409 Conflict`, and a scheduled run that finds a manual run in progress is skipped.

Configured in `application.properties`:

| Property | Default | Meaning |
|---|---|---|
| `pulsecheck.monitoring.enabled` | `true` | turn automatic monitoring on/off |
| `pulsecheck.monitoring.interval` | `30s` | pause between the end of one run and the start of the next (min `1s`) |
| `pulsecheck.monitoring.initial-delay` | `10s` | wait after startup before the first run |

## 7. Planned API

| Category | Method & Path | Purpose |
|---|---|---|
| System | `GET /api/ping` | application liveness |
| Services | `POST /api/services` | register a service |
| | `GET /api/services` | list services |
| | `GET /api/services/{id}` | get one service |
| | `PUT /api/services/{id}` | update a service |
| | `DELETE /api/services/{id}` | delete a service |
| Health checks | `POST /api/services/{id}/checks` | check one service now |
| | `POST /api/checks/run` | check all active services concurrently (`409` if a run is already in progress) |
| | `GET /api/services/{id}/checks?page=&size=` | paginated check history (newest first) |
| | `GET /api/services/{id}/checks/recent?limit=` | most recent checks |
| | `GET /api/services/{id}/checks/summary?limit=` | status counts, failure streak, top errors over recent checks |
| | `GET /api/services/{id}/health` | current health |
| Incidents | `GET /api/incidents?open=&page=&size=` | incidents across services (`open=true` for ongoing only) |
| | `GET /api/services/{id}/incidents?page=&size=` | incidents for one service |
| Metrics | `GET /api/services/{id}/metrics?hours=` | availability, failures, average response time (1–168 h window) |
| | `GET /api/metrics?hours=` | metrics for all services, worst availability first |

Errors will use one consistent JSON structure (defined in Stage 4).

## 8. Role of the Database

PostgreSQL is the single source of truth for:

- registered services and their configuration,
- the full history of health checks (used for metrics),
- incidents, including currently open ones (`ended_at IS NULL`).

Metrics are **calculated** from stored health checks rather than stored separately, so they are always
consistent with the raw data.

## 9. Development Roadmap

| Stage | Milestone |
|---|---|
| 0 | Project definition & architecture |
| 1 | Spring Boot project setup |
| 2 | PostgreSQL & JPA setup (`MonitoredService`) |
| 3 | Service CRUD REST APIs |
| 4 | Validation & global exception handling |
| 5 | Health check domain & history |
| 6 | Actual HTTP health checking |
| 7 | Streams & lambda expressions |
| 8 | Concurrent health checks (multithreading) |
| 9 | Incident tracking |
| 10 | Availability & failure metrics |
| 11 | Scheduled monitoring |
| 12 | API cleanup & Postman collection |
| 13 | Final documentation |
