package com.pulsecheck.service;

import com.pulsecheck.dto.ServiceRequest;
import com.pulsecheck.dto.ServiceResponse;
import com.pulsecheck.entity.MonitoredService;
import com.pulsecheck.repository.MonitoredServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MonitoredServiceService {

    private final MonitoredServiceRepository repository;

    public MonitoredServiceService(MonitoredServiceRepository repository) {
        this.repository = repository;
    }

    public ServiceResponse create(ServiceRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service with name '" + request.name() + "' already exists");
        }

        MonitoredService service = new MonitoredService(request.name(), request.url(), request.description());
        if (request.active() != null) {
            service.setActive(request.active());
        }
        return ServiceResponse.from(repository.save(service));
    }

    public List<ServiceResponse> findAll() {
        return repository.findAll().stream()
                .map(ServiceResponse::from)
                .toList();
    }

    public ServiceResponse findById(Long id) {
        return ServiceResponse.from(getExisting(id));
    }

    public ServiceResponse update(Long id, ServiceRequest request) {
        MonitoredService service = getExisting(id);

        if (repository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service with name '" + request.name() + "' already exists");
        }

        service.setName(request.name());
        service.setUrl(request.url());
        service.setDescription(request.description());
        if (request.active() != null) {
            service.setActive(request.active());
        }
        return ServiceResponse.from(repository.save(service));
    }

    public void delete(Long id) {
        repository.delete(getExisting(id));
    }

    private MonitoredService getExisting(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Service with id " + id + " not found"));
    }
}
