package com.college.clubmanagement.dto;

import com.college.clubmanagement.entity.RequestStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MembershipRequestDTO {
    private Long id;
    private Long clubId;
    private String clubName;
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private String studentDepartment;
    private Integer studentYear;
    private String studentPhone;
    private String areaOfInterest;
    private String skills;
    private String reason;
    private String preferredRole;
    private RequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private String adminNotes;
}
