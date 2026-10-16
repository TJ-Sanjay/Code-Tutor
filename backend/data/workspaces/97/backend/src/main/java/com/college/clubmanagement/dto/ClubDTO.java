package com.college.clubmanagement.dto;

import com.college.clubmanagement.entity.ClubCategory;
import com.college.clubmanagement.entity.ClubStatus;
import com.college.clubmanagement.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClubDTO {
    private Long id;

    @NotBlank(message = "Club name is required")
    private String name;

    private String description;

    @NotNull(message = "Category is required")
    private ClubCategory category;

    private String logoUrl;
    private String bannerUrl;
    private ClubStatus status;
    private String achievements;
    private String contactEmail;
    private String contactPhone;
    private Integer memberCount;
    private java.time.LocalDateTime createdAt;

    @JsonProperty("isMember")
    private Boolean isMember;

    private User createdBy;
}
