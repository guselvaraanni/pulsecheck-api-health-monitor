package com.pulsecheck.repository;

import com.pulsecheck.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByServiceIdAndEndedAtIsNull(Long serviceId);

    // @EntityGraph loads each incident's service in the same query (a JOIN),
    // so building responses with the service name does not trigger one extra query per incident.
    @EntityGraph(attributePaths = "service")
    Page<Incident> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "service")
    Page<Incident> findByEndedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = "service")
    Page<Incident> findByServiceId(Long serviceId, Pageable pageable);
}
