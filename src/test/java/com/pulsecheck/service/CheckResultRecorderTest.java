package com.pulsecheck.service;

import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.Incident;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.repository.IncidentRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Incident state transitions, against the real database. Each test is rolled back afterwards.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CheckResultRecorder.class, IncidentService.class})
class CheckResultRecorderTest {

    @Autowired
    private CheckResultRecorder recorder;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    private MonitoredService service;
    private final Instant t0 = Instant.parse("2026-01-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = serviceRepository.save(new MonitoredService("Recorder Test API", "https://example.com", null));
    }

    private void record(HealthStatus status, int secondsAfterStart, String error) {
        recorder.record(service, new CheckResult(status, status == HealthStatus.UP ? 100L : null,
                t0.plusSeconds(secondsAfterStart), error));
    }

    private List<Incident> incidents() {
        return incidentRepository.findByServiceId(service.getId(), PageRequest.of(0, 50)).getContent();
    }

    @Test
    void firstFailureOpensIncident() {
        record(HealthStatus.DOWN, 0, "HTTP 500");

        List<Incident> incidents = incidents();
        assertEquals(1, incidents.size());
        assertTrue(incidents.get(0).isOpen());
        assertEquals(t0, incidents.get(0).getStartedAt());
        assertEquals("HTTP 500", incidents.get(0).getReason());
    }

    @Test
    void repeatedFailuresDoNotCreateDuplicateIncidents() {
        record(HealthStatus.DOWN, 0, "HTTP 500");
        record(HealthStatus.DOWN, 30, "Timed out");
        record(HealthStatus.DOWN, 60, "HTTP 500");

        List<Incident> incidents = incidents();
        assertEquals(1, incidents.size());
        assertEquals("HTTP 500", incidents.get(0).getReason(), "reason comes from the first failure");
    }

    @Test
    void recoveryResolvesOpenIncidentAtTimeOfCheck() {
        record(HealthStatus.DOWN, 0, "HTTP 500");
        record(HealthStatus.UP, 90, null);

        Incident incident = incidents().get(0);
        assertFalse(incident.isOpen());
        assertEquals(t0.plusSeconds(90), incident.getEndedAt());
    }

    @Test
    void successWithoutOpenIncidentChangesNothing() {
        record(HealthStatus.UP, 0, null);
        record(HealthStatus.UP, 30, null);

        assertTrue(incidents().isEmpty());
    }

    @Test
    void newFailureAfterRecoveryOpensSecondIncident() {
        record(HealthStatus.DOWN, 0, "HTTP 500");
        record(HealthStatus.UP, 30, null);
        record(HealthStatus.DOWN, 60, "Connection failed");

        List<Incident> incidents = incidents();
        assertEquals(2, incidents.size());
        Incident newest = incidents.stream().filter(Incident::isOpen).findFirst().orElseThrow();
        assertEquals("Connection failed", newest.getReason());
        assertNull(newest.getEndedAt());
    }
}
