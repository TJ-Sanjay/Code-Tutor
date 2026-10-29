package com.codetutor.backend.auth.service;

import com.codetutor.backend.auth.dto.*;
import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.security.jwt.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth service — handles user registration and login.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final GoogleTokenVerifier googleVerifier;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            GoogleTokenVerifier googleVerifier
    ) {
        this.googleVerifier = googleVerifier;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Register a new user account.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.USER)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} ({})", user.getUsername(), user.getEmail());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Authenticate user and return JWT tokens.
     */
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email/username or password");
        }

        User user = userRepository.findByEmail(request.getIdentifier())
                .or(() -> userRepository.findByUsername(request.getIdentifier()))
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        log.info("User logged in: {} ({})", user.getUsername(), user.getEmail());
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * "Continue with Google": signs in the account that owns the verified Google e-mail, creating it on first use.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String credential) {
        GoogleTokenVerifier.Profile p = googleVerifier.verify(credential)
                .orElseThrow(() -> new BadCredentialsException("Google sign-in could not be verified. Please try again."));
        User user = userRepository.findByEmail(p.email()).orElse(null);
        if (user == null) {
            String base = p.email().substring(0, p.email().indexOf('@')).replaceAll("[^A-Za-z0-9._-]", "");
            if (base.length() < 3) base = base + "user";
            if (base.length() > 40) base = base.substring(0, 40);
            String username = base;
            for (int i = 2; userRepository.existsByUsername(username); i++) username = base + i;
            user = userRepository.save(User.builder()
                    .username(username)
                    .email(p.email())
                    .password(passwordEncoder.encode(java.util.UUID.randomUUID() + "-" + java.util.UUID.randomUUID())) // never used: Google signs them in
                    .fullName(p.name().isBlank() ? username : p.name())
                    .avatarUrl(p.picture().isBlank() ? null : p.picture())
                    .role(User.Role.USER)
                    .build());
            log.info("New user created through Google sign-in: {} ({})", user.getUsername(), user.getEmail());
        } else {
            log.info("User signed in with Google: {} ({})", user.getUsername(), user.getEmail());
        }
        if (!user.isEnabled()) throw new BadCredentialsException("This account is disabled.");
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return buildAuthResponse(user, jwtUtil.generateAccessToken(userDetails), jwtUtil.generateRefreshToken(userDetails));
    }

    /** The Google client id the front-end needs to draw the button (empty = Google sign-in is switched off). */
    public String googleClientId() { return googleVerifier.clientId(); }

    /**
     * Get current user profile by email.
     */
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .storageUsedBytes(user.getStorageUsedBytes())
                .storageLimitBytes(user.getStorageLimitBytes())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpiration())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .storageUsedBytes(user.getStorageUsedBytes())
                .storageLimitBytes(user.getStorageLimitBytes())
                .build();
    }
}
