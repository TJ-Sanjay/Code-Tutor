package com.codetutor.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * DTO for returning user profile information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String avatarUrl;
    private Long storageUsedBytes;
    private Long storageLimitBytes;
    private boolean enabled;
    private LocalDateTime createdAt;

    private UserProfileResponse() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final UserProfileResponse obj = new UserProfileResponse();

        public Builder id(Long v) { obj.id = v; return this; }
        public Builder username(String v) { obj.username = v; return this; }
        public Builder email(String v) { obj.email = v; return this; }
        public Builder fullName(String v) { obj.fullName = v; return this; }
        public Builder role(String v) { obj.role = v; return this; }
        public Builder avatarUrl(String v) { obj.avatarUrl = v; return this; }
        public Builder storageUsedBytes(Long v) { obj.storageUsedBytes = v; return this; }
        public Builder storageLimitBytes(Long v) { obj.storageLimitBytes = v; return this; }
        public Builder enabled(boolean v) { obj.enabled = v; return this; }
        public Builder createdAt(LocalDateTime v) { obj.createdAt = v; return this; }

        public UserProfileResponse build() { return obj; }
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public String getAvatarUrl() { return avatarUrl; }
    public Long getStorageUsedBytes() { return storageUsedBytes; }
    public Long getStorageLimitBytes() { return storageLimitBytes; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
