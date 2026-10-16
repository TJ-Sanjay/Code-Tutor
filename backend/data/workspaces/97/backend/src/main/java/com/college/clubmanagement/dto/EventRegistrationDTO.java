package com.college.clubmanagement.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EventRegistrationDTO {
    private Long id;
    private Long eventId;
    private String eventName;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userDepartment;
    private Integer userYear;
    private String userPhone;
    private Boolean paymentRequired;
    private Boolean paymentCompleted;
    private Boolean attended;
    private LocalDateTime registeredAt;
}
