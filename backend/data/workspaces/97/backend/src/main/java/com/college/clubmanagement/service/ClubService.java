package com.college.clubmanagement.service;

import com.college.clubmanagement.dto.ClubDTO;
import com.college.clubmanagement.dto.ClubMemberDTO;
import com.college.clubmanagement.entity.*;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.exception.ResourceNotFoundException;
import com.college.clubmanagement.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClubService {

        @Autowired
        private ClubRepository clubRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ClubMemberRepository clubMemberRepository;

        @Autowired
        private MembershipRequestRepository membershipRequestRepository;

        @Autowired
        private NotificationService notificationService;

        @Transactional(readOnly = true)
        public List<ClubDTO> getAllClubs() {
                return clubRepository.findAll().stream()
                                .map(this::convertToDTO)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> getActiveClubs() {
                return getActiveClubs(null);
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> getActiveClubs(String username) {
                return clubRepository.findByStatus(ClubStatus.ACTIVE).stream()
                                .map(club -> convertToDTO(club, username))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> getClubsByCategory(ClubCategory category) {
                return getClubsByCategory(category, null);
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> getClubsByCategory(ClubCategory category, String username) {
                return clubRepository.findByCategoryAndStatus(category, ClubStatus.ACTIVE).stream()
                                .map(club -> convertToDTO(club, username))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> searchClubs(String keyword) {
                return searchClubs(keyword, null);
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> searchClubs(String keyword, String username) {
                return clubRepository.searchByKeyword(keyword).stream()
                                .map(club -> convertToDTO(club, username))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public ClubDTO getClubById(Long id) {
                return getClubById(id, null);
        }

        @Transactional(readOnly = true)
        public ClubDTO getClubById(Long id, String username) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + id));

                ClubDTO dto = convertToDTO(club);

                if (username != null) {
                        User user = userRepository.findByUsername(username)
                                        .orElse(null);
                        if (user != null) {
                                boolean isMember = club.getMembers().stream()
                                                .anyMatch(member -> member.getUser().getId().equals(user.getId()));
                                dto.setIsMember(isMember);
                        }
                }

                return dto;
        }

        @Transactional
        public ClubDTO createClub(ClubDTO clubDTO, String username) {
                if (clubRepository.existsByName(clubDTO.getName())) {
                        throw new BadRequestException("Club with this name already exists");
                }

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Club club = new Club();
                club.setName(clubDTO.getName());
                club.setDescription(clubDTO.getDescription());
                club.setCategory(clubDTO.getCategory());
                club.setLogoUrl(clubDTO.getLogoUrl());
                club.setBannerUrl(clubDTO.getBannerUrl());
                club.setAchievements(clubDTO.getAchievements());
                club.setContactEmail(clubDTO.getContactEmail());
                club.setContactPhone(clubDTO.getContactPhone());
                club.setStatus(ClubStatus.PENDING);

                club.getClubHeads().add(user);

                Club savedClub = clubRepository.save(club);
                return convertToDTO(savedClub);
        }

        @Transactional
        public void approveClub(Long id) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                club.setStatus(ClubStatus.ACTIVE);
                clubRepository.save(club);

                // Notify club heads
                club.getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Club Approved",
                                        "Your club '" + club.getName() + "' has been approved!",
                                        NotificationType.CLUB_APPROVAL,
                                        "/clubs/" + club.getId());
                });
        }

        @Transactional
        public void rejectClub(Long id) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                club.setStatus(ClubStatus.REJECTED);
                clubRepository.save(club);

                // Notify club heads
                club.getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Club Rejected",
                                        "Your club application for '" + club.getName() + "' was rejected.",
                                        NotificationType.CLUB_APPROVAL,
                                        "/student/dashboard");
                });
        }

        @Transactional
        public void deactivateClub(Long id) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                club.setStatus(ClubStatus.INACTIVE);
                clubRepository.save(club);

                // Notify club heads
                club.getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Club Deactivated",
                                        "Your club '" + club.getName() + "' has been deactivated by the administrator.",
                                        NotificationType.CLUB_APPROVAL,
                                        "/student/dashboard");
                });
        }

        @Transactional
        public void activateClub(Long id) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                club.setStatus(ClubStatus.ACTIVE);
                clubRepository.save(club);

                // Notify club heads
                club.getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Club Activated",
                                        "Your club '" + club.getName() + "' has been activated and is now live.",
                                        NotificationType.CLUB_APPROVAL,
                                        "/clubs/" + club.getId());
                });
        }

        @Transactional
        public ClubDTO updateClub(Long id, ClubDTO clubDTO) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + id));

                club.setName(clubDTO.getName());
                club.setDescription(clubDTO.getDescription());
                club.setCategory(clubDTO.getCategory());
                club.setLogoUrl(clubDTO.getLogoUrl());
                club.setBannerUrl(clubDTO.getBannerUrl());
                club.setAchievements(clubDTO.getAchievements());
                club.setContactEmail(clubDTO.getContactEmail());
                club.setContactPhone(clubDTO.getContactPhone());

                Club updatedClub = clubRepository.save(club);
                return convertToDTO(updatedClub);
        }

        @Transactional
        public void deleteClub(Long id) {
                if (id == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + id));
                if (club == null)
                        throw new ResourceNotFoundException("Club not found with id: " + id);
                clubRepository.delete(club);
        }

        @Transactional
        public void submitJoinRequest(Long clubId, String username, String areaOfInterest,
                        String skills, String reason, String preferredRole) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + clubId));

                User student = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Check if already a member
                if (clubMemberRepository.existsByClubAndUser(club, student)) {
                        throw new BadRequestException("You are already a member of this club");
                }

                // Check if pending request exists
                if (membershipRequestRepository.existsByClubAndStudentAndStatus(club, student, RequestStatus.PENDING)) {
                        throw new BadRequestException("You already have a pending request for this club");
                }

                MembershipRequest request = new MembershipRequest();
                request.setClub(club);
                request.setStudent(student);
                request.setAreaOfInterest(areaOfInterest);
                request.setSkills(skills);
                request.setReason(reason);
                request.setPreferredRole(preferredRole);
                request.setStatus(RequestStatus.PENDING);

                membershipRequestRepository.save(request);
        }

        public ClubDTO convertToDTO(Club club) {
                return convertToDTO(club, null);
        }

        public ClubDTO convertToDTO(Club club, String username) {
                ClubDTO dto = new ClubDTO();
                dto.setId(club.getId());
                dto.setName(club.getName());
                dto.setDescription(club.getDescription());
                dto.setCategory(club.getCategory());
                dto.setLogoUrl(club.getLogoUrl());
                dto.setBannerUrl(club.getBannerUrl());
                dto.setAchievements(club.getAchievements());
                dto.setContactEmail(club.getContactEmail());
                dto.setContactPhone(club.getContactPhone());
                dto.setStatus(club.getStatus());
                dto.setCreatedAt(club.getCreatedAt());
                dto.setMemberCount(club.getMembers() != null ? club.getMembers().size() : 0);
                dto.setCreatedBy(club.getClubHeads() != null && !club.getClubHeads().isEmpty()
                                ? club.getClubHeads().get(0)
                                : null);

                // Populate isMember if username is provided
                if (username != null) {
                        User user = userRepository.findByUsername(username).orElse(null);
                        if (user != null) {
                                boolean isMember = clubMemberRepository.findByClubAndUser(club, user).isPresent();
                                dto.setIsMember(isMember);
                        }
                }

                return dto;
        }

        private ClubMemberDTO convertToMemberDTO(ClubMember member) {
                ClubMemberDTO dto = new ClubMemberDTO();
                dto.setId(member.getId());
                dto.setUserId(member.getUser().getId());
                dto.setUsername(member.getUser().getUsername());
                dto.setFullName(member.getUser().getFullName());
                dto.setEmail(member.getUser().getEmail());
                dto.setPhone(member.getUser().getPhone());
                dto.setDepartment(member.getUser().getDepartment());
                dto.setYear(member.getUser().getYear());
                dto.setRole(member.getRole());
                dto.setJoinedAt(member.getJoinedAt());
                return dto;
        }

        @Transactional(readOnly = true)
        public boolean isClubHead(Long clubId, String username) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + clubId));

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Admin has full permission
                if (user.getRoles().contains(Role.ADMIN)) {
                        return true;
                }

                return club.getClubHeads().stream()
                                .anyMatch(head -> head.getId().equals(user.getId()));
        }

        @Transactional(readOnly = true)
        public List<ClubMemberDTO> getClubMembers(Long clubId, String username) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + clubId));

                // Verify the user is a club head
                if (!isClubHead(clubId, username)) {
                        throw new BadRequestException("You are not authorized to view members of this club");
                }

                return clubMemberRepository.findByClub(club).stream()
                                .map(this::convertToMemberDTO)
                                .collect(Collectors.toList());
        }

        @Transactional
        public void removeMember(Long clubId, Long memberId, String username) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                if (memberId == null)
                        throw new BadRequestException("Member ID cannot be null");

                // Verify the club exists
                if (!clubRepository.existsById(clubId)) {
                        throw new ResourceNotFoundException("Club not found with id: " + clubId);
                }

                // Verify the user is a club head
                if (!isClubHead(clubId, username)) {
                        throw new BadRequestException("You are not authorized to remove members from this club");
                }

                ClubMember member = clubMemberRepository.findById(memberId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Member not found with id: " + memberId));

                // Verify the member belongs to this club
                if (!member.getClub().getId().equals(clubId)) {
                        throw new BadRequestException("This member does not belong to this club");
                }

                clubMemberRepository.delete(member);
        }

        @Transactional(readOnly = true)
        public List<ClubDTO> getClubsForClubHead(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                return clubRepository.findAll().stream()
                                .filter(club -> club.getClubHeads().stream()
                                                .anyMatch(head -> head.getId().equals(user.getId())))
                                .map(this::convertToDTO)
                                .collect(Collectors.toList());
        }

        @Transactional
        public void leaveClub(Long clubId, String username) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + clubId));

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Check if user is a club head - club heads cannot leave
                boolean isClubHead = club.getClubHeads().stream()
                                .anyMatch(head -> head.getId().equals(user.getId()));
                if (isClubHead) {
                        throw new BadRequestException("Club heads cannot leave the club. Transfer ownership first.");
                }

                // Find member record
                ClubMember member = clubMemberRepository.findByClubAndUser(club, user)
                                .orElseThrow(() -> new BadRequestException("You are not a member of this club"));

                if (member == null)
                        throw new BadRequestException("You are not a member of this club");

                clubMemberRepository.delete(member);
        }
}
