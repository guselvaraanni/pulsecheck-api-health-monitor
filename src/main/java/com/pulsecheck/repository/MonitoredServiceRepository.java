package com.pulsecheck.repository;

import com.pulsecheck.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<MonitoredService> findByActiveTrue();
}
