package com.pulsecheck.repository;

import com.pulsecheck.entity.MonitoredService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs against the real PostgreSQL database from .env. Each test is rolled back afterwards.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MonitoredServiceRepositoryTest {

    @Autowired
    private MonitoredServiceRepository repository;

    @Test
    void savesServiceAndGeneratesIdAndCreatedAt() {
        MonitoredService saved = repository.saveAndFlush(
                new MonitoredService("Payment API", "https://example.com/health", "Handles payments"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertTrue(saved.isActive());

        MonitoredService found = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Payment API", found.getName());
    }

    @Test
    void existsByNameIgnoresCase() {
        repository.saveAndFlush(new MonitoredService("Auth API", "https://example.com/auth", null));

        assertTrue(repository.existsByNameIgnoreCase("auth api"));
        assertFalse(repository.existsByNameIgnoreCase("Unknown API"));
    }
}
