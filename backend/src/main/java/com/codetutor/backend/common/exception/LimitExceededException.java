package com.codetutor.backend.common.exception;

/** Thrown when an uploaded / cloned repository exceeds the configured resource limits. */
public class LimitExceededException extends RuntimeException {
    public LimitExceededException(String message) { super(message); }
}
