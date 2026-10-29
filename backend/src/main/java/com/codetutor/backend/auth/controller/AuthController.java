package com.codetutor.backend.auth.controller;

import com.codetutor.backend.auth.dto.*;
import com.codetutor.backend.auth.service.AuthService;
import com.codetutor.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Auth REST controller.
 * Base path: /api/auth
 *
 * Public endpoints:
 *   POST /api/auth/register  — create a new account
 *   POST /api/auth/login     — get JWT tokens
 *
 * Protected endpoints (JWT required):
 *   GET  /api/auth/me        — get current user profile
 *   POST /api/auth/logout    — client-side logout (clear token)
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    public record GoogleRequest(@jakarta.validation.constraints.NotBlank String credential) {}

    /** Tells the sign-in page whether to show "Continue with Google" (and with which client id). */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> config() {
        return ResponseEntity.ok(ApiResponse.success("Auth config", java.util.Map.of("googleClientId", authService.googleClientId())));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> google(@Valid @RequestBody GoogleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.loginWithGoogle(request.credential())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserProfileResponse profile = authService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", profile));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT is stateless — client simply discards the token.
        // For enhanced security, implement a token blacklist (Redis) later.
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }
}
