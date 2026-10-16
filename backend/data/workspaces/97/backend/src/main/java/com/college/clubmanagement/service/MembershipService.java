package com.college.clubmanagement.service;

import com.college.clubmanagement.dto.MembershipRequestDTO;
import com.college.clubmanagement.entity.*;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.exception.ResourceNotFoundException;
import com.college.clubmanagement.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.List;

@Service
public class MembershipService {

        @Autowired
        private MembershipRequestRepository membershipRequestRepository;

        @Autowired
        private ClubRepository clubRepository;

        @Autowired
        private ClubMemberRepository clubMemberRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private NotificationService notificationService;

        @Transactional(readOnly = true)
        public List<MembershipRequestDTO> getClubRequests(Long clubId) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                return membershipRequestRepository.findByClub(club).stream()
                                .map(this::convertToDTO)
                                .collect(java.util.stream.Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<MembershipRequestDTO> getPendingRequests(Long clubId) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                return membershipRequestRepository.findByClubAndStatus(club, RequestStatus.PENDING).stream()
                                .map(this::convertToDTO)
                                .collect(java.util.stream.Collectors.toList());
        }

        @Transactional(readOnly = true)
        public long getPendingRequestsCountForClubHead(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                List<Club> managedClubs = clubRepository.findAll().stream()
                                .filter(club -> club.getClubHeads().stream()
                                                .anyMatch(head -> head.getId().equals(user.getId())))
                                .collect(java.util.stream.Collectors.toList());

                return managedClubs.stream()
                                .mapToLong(club -> membershipRequestRepository
                                                .findByClubAndStatus(club, RequestStatus.PENDING).size())
                                .sum();
        }

        @Transactional(readOnly = true)
        public List<MembershipRequestDTO> getRequestsByUser(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                return membershipRequestRepository.findByStudent(user).stream()
                                .map(this::convertToDTO)
                                .collect(java.util.stream.Collectors.toList());
        }

        private MembershipRequestDTO convertToDTO(MembershipRequest request) {
                MembershipRequestDTO dto = new MembershipRequestDTO();
                dto.setId(request.getId());
                dto.setClubId(request.getClub().getId());
                dto.setClubName(request.getClub().getName());
                dto.setStudentId(request.getStudent().getId());
                dto.setStudentName(request.getStudent().getFullName() != null ? request.getStudent().getFullName()
                                : request.getStudent().getUsername());
                dto.setStudentEmail(request.getStudent().getEmail());
                dto.setStudentDepartment(request.getStudent().getDepartment());
                dto.setStudentYear(request.getStudent().getYear());
                dto.setStudentPhone(request.getStudent().getPhone());
                dto.setAreaOfInterest(request.getAreaOfInterest());
                dto.setSkills(request.getSkills());
                dto.setReason(request.getReason());
                dto.setPreferredRole(request.getPreferredRole());
                dto.setStatus(request.getStatus());
                dto.setRequestedAt(request.getRequestedAt());
                dto.setProcessedAt(request.getProcessedAt());
                dto.setAdminNotes(request.getAdminNotes());
                return dto;
        }

        @Transactional
        public void approveRequest(Long requestId, String approverUsername) {
                if (requestId == null)
                        throw new BadRequestException("Request ID cannot be null");
                MembershipRequest request = membershipRequestRepository.findById(requestId)
                                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

                User approver = userRepository.findByUsername(approverUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                request.setStatus(RequestStatus.APPROVED);
                request.setProcessedAt(LocalDateTime.now());
                request.setProcessedBy(approver);
                membershipRequestRepository.save(request);

                // Add user as club member
                ClubMember member = new ClubMember();
                member.setClub(request.getClub());
                member.setUser(request.getStudent());
                member.setRole(MemberRole.MEMBER);
                clubMemberRepository.save(member);

                notificationService.createNotification(
                                request.getStudent(),
                                "Membership Approved",
                                "Your request to join " + request.getClub().getName() +
                                                " has been approved!",
                                NotificationType.MEMBERSHIP_APPROVED,
                                "/clubs/" + request.getClub().getId());
        }

        @Transactional
        public void rejectRequest(Long requestId, String approverUsername, String reason) {
                if (requestId == null)
                        throw new BadRequestException("Request ID cannot be null");
                MembershipRequest request = membershipRequestRepository.findById(requestId)
                                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

                User approver = userRepository.findByUsername(approverUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                request.setStatus(RequestStatus.REJECTED);
                request.setProcessedAt(LocalDateTime.now());
                request.setProcessedBy(approver);
                request.setAdminNotes(reason);
                membershipRequestRepository.save(request);

                notificationService.createNotification(
                                request.getStudent(),
                                "Membership Request Rejected",
                                "Your request to join " + request.getClub().getName() + " was not approved.",
                                NotificationType.MEMBERSHIP_REJECTED,
                                "/clubs/" + request.getClub().getId());
        }
}
