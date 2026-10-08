package com.pulsecheck.service;

import com.pulsecheck.dto.IncidentResponse;
import com.pulsecheck.dto.PageResponse;
import com.pulsecheck.entity.HealthCheck;
import com.pulsecheck.entity.HealthStatus;
import com.pulsecheck.entity.Incident;
import com.pulsecheck.exception.ServiceNotFoundException;
import com.pulsecheck.repository.IncidentRepository;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    public IncidentService(IncidentRepository incidentRepository,
                           MonitoredServiceRepository monitoredServiceRepository) {
        this.incidentRepository = incidentRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
    }

    /**
     * Applies the incident state machine for one new check:
     * <ul>
     *   <li>DOWN and no open incident: open a new incident</li>
     *   <li>UP and an open incident: resolve it at the time of this check</li>
     *   <li>otherwise: nothing changes (no duplicate incidents for repeated failures)</li>
     * </ul>
     * Joins the caller's transaction, so the check and the incident change are committed together.
     */
    @Transactional
    public void applyCheck(HealthCheck check) {
        Long serviceId = check.getService().getId();
        Optional<Incident> openIncident = incidentRepository.findFirstByServiceIdAndEndedAtIsNull(serviceId);

        if (check.getStatus() == HealthStatus.DOWN && openIncident.isEmpty()) {
            Incident incident = incidentRepository.save(
                    new Incident(check.getService(), check.getCheckedAt(), check.getErrorMessage()));
            log.warn("Incident {} opened for service {}: {}", incident.getId(), serviceId, check.getErrorMessage());
        } else if (check.getStatus() == HealthStatus.UP && openIncident.isPresent()) {
            Incident incident = openIncident.get();
            // No save() needed: the incident is managed inside this transaction, so Hibernate
            // detects the change (dirty checking) and issues an UPDATE on commit.
            incident.resolve(check.getCheckedAt());
            log.info("Incident {} resolved for service {}", incident.getId(), serviceId);
        }
    }

    public PageResponse<IncidentResponse> getIncidents(boolean openOnly, int page, int size) {
        Pageable pageable = newestFirst(page, size);
        return PageResponse.from(
                (openOnly ? incidentRepository.findByEndedAtIsNull(pageable) : incidentRepository.findAllBy(pageable))
                        .map(IncidentResponse::from));
    }

    public PageResponse<IncidentResponse> getIncidentsForService(Long serviceId, int page, int size) {
        if (!monitoredServiceRepository.existsById(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }
        return PageResponse.from(
                incidentRepository.findByServiceId(serviceId, newestFirst(page, size))
                        .map(IncidentResponse::from));
    }

    private static Pageable newestFirst(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
    }
}
