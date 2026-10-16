package com.college.clubmanagement.dto;

import com.college.clubmanagement.entity.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClubMemberDTO {

    private Long id;
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String department;
    private Integer year;
    private MemberRole role;
    private LocalDateTime joinedAt;
}
