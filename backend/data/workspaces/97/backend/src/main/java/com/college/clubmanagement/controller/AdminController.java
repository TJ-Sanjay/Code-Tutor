package com.college.clubmanagement.controller;

import com.college.clubmanagement.dto.*;
import com.college.clubmanagement.entity.*;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.repository.*;
import com.college.clubmanagement.service.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private ClubService clubService;

    // Dashboard Analytics
    @GetMapping("/analytics")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        analytics.put("totalClubs", clubRepository.count());
        analytics.put("activeClubs", clubRepository.countByStatus(ClubStatus.ACTIVE));
        analytics.put("pendingClubs", clubRepository.countByStatus(ClubStatus.PENDING));
        analytics.put("totalUsers", userRepository.count());
        analytics.put("totalEvents", eventRepository.count());
        analytics.put("upcomingEvents",
                eventRepository.findUpcomingEvents(java.time.LocalDateTime.now().minusHours(24)).size());
        analytics.put("pendingEvents", eventRepository.findByStatus(EventStatus.PENDING).size());
        analytics.put("totalRevenue", paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));

        return ResponseEntity.ok(analytics);
    }

    // Club Management
    @GetMapping("/clubs/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ClubDTO>> getPendingClubs() {
        return ResponseEntity.ok(clubRepository.findByStatus(ClubStatus.PENDING).stream()
                .map(clubService::convertToDTO)
                .toList());
    }

    @PutMapping("/clubs/{id}/approve")
    public ResponseEntity<MessageResponse> approveClub(@PathVariable Long id) {
        clubService.approveClub(id);
        return ResponseEntity.ok(new MessageResponse("Club approved successfully"));
    }

    @PutMapping("/clubs/{id}/reject")
    public ResponseEntity<MessageResponse> rejectClub(@PathVariable Long id) {
        clubService.rejectClub(id);
        return ResponseEntity.ok(new MessageResponse("Club rejected"));
    }

    @PutMapping("/clubs/{id}/deactivate")
    public ResponseEntity<MessageResponse> deactivateClub(@PathVariable Long id) {
        clubService.deactivateClub(id);
        return ResponseEntity.ok(new MessageResponse("Club deactivated successfully"));
    }

    @PutMapping("/clubs/{id}/activate")
    public ResponseEntity<MessageResponse> activateClub(@PathVariable Long id) {
        clubService.activateClub(id);
        return ResponseEntity.ok(new MessageResponse("Club activated successfully"));
    }

    // User Management
    @GetMapping("/users")
    @Transactional(readOnly = true)
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        if (id == null)
            throw new BadRequestException("ID cannot be null");
        return ResponseEntity.ok(userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found")));
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<MessageResponse> updateUserRoles(
            @PathVariable Long id,
            @RequestBody Map<String, List<String>> request) {

        if (id == null)
            throw new BadRequestException("ID cannot be null");
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<String> roleStrings = request.get("roles");
        java.util.Set<Role> roles = user.getRoles();
        boolean wasAdmin = roles.contains(Role.ADMIN);

        roles.clear();
        roleStrings.forEach(roleStr -> {
            Role role = Role.valueOf(roleStr);
            if (role != Role.ADMIN) {
                roles.add(role);
            }
        });

        if (wasAdmin) {
            roles.add(Role.ADMIN);
        }

        // Always preserve ADMIN role if the user was already an admin
        // (This assumes the initial admin is created via DataInitializer and shouldn't
        // be demoted here either)
        // For now, just ensuring no NEW admins can be created.

        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User roles updated successfully"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable Long id) {
        if (id == null)
            throw new BadRequestException("ID cannot be null");
        userRepository.deleteById(id);
        return ResponseEntity.ok(new MessageResponse("User deleted successfully"));
    }

    // System Reports
    @GetMapping("/reports/clubs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getClubsReport() {
        List<Club> clubs = clubRepository.findAll();
        List<Map<String, Object>> report = clubs.stream()
                .map(club -> {
                    Map<String, Object> clubData = new HashMap<>();
                    clubData.put("id", club.getId());
                    clubData.put("name", club.getName());
                    clubData.put("category", club.getCategory());
                    clubData.put("status", club.getStatus());
                    clubData.put("memberCount", club.getMembers().size());
                    clubData.put("eventCount", club.getEvents().size());
                    return clubData;
                })
                .toList();

        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/events")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getEventsReport() {
        List<Event> events = eventRepository.findAll();
        List<Map<String, Object>> report = events.stream()
                .map(event -> {
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("id", event.getId());
                    eventData.put("name", event.getName());
                    eventData.put("clubName", event.getClub().getName());
                    eventData.put("dateTime", event.getDateTime());
                    eventData.put("status", event.getStatus());
                    eventData.put("registrationCount", event.getRegistrations().size());
                    eventData.put("revenue", event.getRegistrations().stream()
                            .filter(EventRegistration::getPaymentCompleted)
                            .count() * event.getEntryFee().doubleValue());
                    return eventData;
                })
                .toList();

        return ResponseEntity.ok(report);
    }

    // Event Approval Endpoints
    @GetMapping("/events/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<List<EventDTO>> getPendingEvents() {
        return ResponseEntity.ok(eventService.getPendingEvents());
    }

    @PutMapping("/events/{id}/approve")
    public ResponseEntity<MessageResponse> approveEvent(@PathVariable Long id) {
        eventService.approveEvent(id);
        return ResponseEntity.ok(new MessageResponse("Event approved successfully"));
    }

    @PutMapping("/events/{id}/reject")
    public ResponseEntity<MessageResponse> rejectEvent(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String reason = request.getOrDefault("reason", "Not specified");
        eventService.rejectEvent(id, reason);
        return ResponseEntity.ok(new MessageResponse("Event rejected"));
    }

    @PutMapping("/events/{id}/deactivate")
    public ResponseEntity<MessageResponse> deactivateEvent(@PathVariable Long id) {
        eventService.deactivateEvent(id);
        return ResponseEntity.ok(new MessageResponse("Event deactivated successfully"));
    }

    @PutMapping("/events/{id}/activate")
    public ResponseEntity<MessageResponse> activateEvent(@PathVariable Long id) {
        eventService.activateEvent(id);
        return ResponseEntity.ok(new MessageResponse("Event activated successfully"));
    }
}
