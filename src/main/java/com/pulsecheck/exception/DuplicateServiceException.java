package com.pulsecheck.exception;

public class DuplicateServiceException extends RuntimeException {

    public DuplicateServiceException(String name) {
        super("Service with name '" + name + "' already exists");
    }
}
