package com.pulsecheck.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "health_checks",
        indexes = @Index(name = "idx_health_checks_service_checked_at", columnList = "service_id, checked_at"))
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side of the relationship: this column holds the foreign key.
    // ON DELETE CASCADE lets PostgreSQL remove a service's checks in one statement when the service is deleted.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MonitoredService service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HealthStatus status;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected HealthCheck() {
    }

    public HealthCheck(MonitoredService service, HealthStatus status, Long responseTimeMs,
                       Instant checkedAt, String errorMessage) {
        this.service = service;
        this.status = status;
        this.responseTimeMs = responseTimeMs;
        this.checkedAt = checkedAt;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public MonitoredService getService() {
        return service;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
