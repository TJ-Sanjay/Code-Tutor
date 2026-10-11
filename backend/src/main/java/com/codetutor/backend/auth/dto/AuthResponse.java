package com.codetutor.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for authentication response — returned after login or register.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String avatarUrl;
    private Long storageUsedBytes;
    private Long storageLimitBytes;

    private AuthResponse() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AuthResponse obj = new AuthResponse();

        public Builder accessToken(String v) { obj.accessToken = v; return this; }
        public Builder refreshToken(String v) { obj.refreshToken = v; return this; }
        public Builder tokenType(String v) { obj.tokenType = v; return this; }
        public Builder expiresIn(Long v) { obj.expiresIn = v; return this; }
        public Builder userId(Long v) { obj.userId = v; return this; }
        public Builder username(String v) { obj.username = v; return this; }
        public Builder email(String v) { obj.email = v; return this; }
        public Builder fullName(String v) { obj.fullName = v; return this; }
        public Builder role(String v) { obj.role = v; return this; }
        public Builder avatarUrl(String v) { obj.avatarUrl = v; return this; }
        public Builder storageUsedBytes(Long v) { obj.storageUsedBytes = v; return this; }
        public Builder storageLimitBytes(Long v) { obj.storageLimitBytes = v; return this; }

        public AuthResponse build() { return obj; }
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public Long getExpiresIn() { return expiresIn; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public String getAvatarUrl() { return avatarUrl; }
    public Long getStorageUsedBytes() { return storageUsedBytes; }
    public Long getStorageLimitBytes() { return storageLimitBytes; }
}
