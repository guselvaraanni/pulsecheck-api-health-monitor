package com.pulsecheck.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A period during which a service was failing. Open while {@code endedAt} is null.
 */
@Entity
@Table(name = "incidents",
        indexes = @Index(name = "idx_incidents_service_ended_at", columnList = "service_id, ended_at"))
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MonitoredService service;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(length = 1000)
    private String reason;

    protected Incident() {
    }

    public Incident(MonitoredService service, Instant startedAt, String reason) {
        this.service = service;
        this.startedAt = startedAt;
        this.reason = reason;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public void resolve(Instant endedAt) {
        if (!isOpen()) {
            throw new IllegalStateException("Incident " + id + " is already resolved");
        }
        this.endedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public MonitoredService getService() {
        return service;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getReason() {
        return reason;
    }
}
