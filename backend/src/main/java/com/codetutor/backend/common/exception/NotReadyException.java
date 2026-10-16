package com.codetutor.backend.common.exception;

/** The project exists but its analysis has not finished (or failed). */
public class NotReadyException extends RuntimeException {
    public NotReadyException(String message) { super(message); }
}
