package com.pulsecheck.exception;

public class CheckRunInProgressException extends RuntimeException {

    public CheckRunInProgressException() {
        super("A check run is already in progress; try again when it finishes");
    }
}
