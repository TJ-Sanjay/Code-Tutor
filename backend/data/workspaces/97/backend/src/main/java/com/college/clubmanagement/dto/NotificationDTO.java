package com.college.clubmanagement.dto;

import com.college.clubmanagement.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {
    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private String link;

    @JsonProperty("read")
    private boolean isRead;

    private LocalDateTime createdAt;
}
