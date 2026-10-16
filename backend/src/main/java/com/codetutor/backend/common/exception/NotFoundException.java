package com.codetutor.backend.common.exception;

/** Thrown when a resource does not exist — or belongs to another user (deliberately indistinguishable). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
