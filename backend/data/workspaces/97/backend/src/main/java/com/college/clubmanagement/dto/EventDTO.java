package com.college.clubmanagement.dto;

import com.college.clubmanagement.entity.EventStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventDTO {
    private Long id;

    @NotBlank(message = "Event name is required")
    private String name;

    private String description;

    @NotNull(message = "Club ID is required")
    private Long clubId;

    private String clubName;

    @NotNull(message = "Date and time is required")
    private LocalDateTime dateTime;

    @NotBlank(message = "Venue is required")
    private String venue;

    private BigDecimal entryFee;
    private Integer maxParticipants;

    @NotNull(message = "Registration deadline is required")
    private LocalDateTime registrationDeadline;

    private String posterUrl;
    private String rules;
    private String contactEmail;
    private String contactPhone;
    private EventStatus status;
    private Integer registrationCount;
    @JsonProperty("isFull")
    private Boolean isFull;
    @JsonProperty("isRegistrationOpen")
    private Boolean isRegistrationOpen;
    private LocalDateTime createdAt;
    @JsonProperty("isRegistered")
    private Boolean isRegistered;
    @JsonProperty("isPaymentCompleted")
    private Boolean isPaymentCompleted;
}
