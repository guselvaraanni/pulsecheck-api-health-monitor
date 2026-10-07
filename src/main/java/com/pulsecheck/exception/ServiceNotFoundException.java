package com.pulsecheck.exception;

public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(Long id) {
        super("Service with id " + id + " not found");
    }
}
