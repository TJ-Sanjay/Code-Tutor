package com.codetutor.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for user login request.
 * Accepts either username or email for flexibility.
 */
public class LoginRequest {

    @NotBlank(message = "Email or username is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;

    public LoginRequest() {}

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
