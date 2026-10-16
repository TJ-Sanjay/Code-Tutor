package com.college.clubmanagement.controller;

import com.college.clubmanagement.dto.EventDTO;
import com.college.clubmanagement.dto.EventRegistrationDTO;
import com.college.clubmanagement.dto.MessageResponse;
import com.college.clubmanagement.service.EventService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EventController {

    @Autowired
    private EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventDTO>> getAllEvents(
            @RequestParam(required = false) Long clubId,
            @RequestParam(required = false) Boolean upcoming,
            Authentication authentication) {
        String username = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            username = authentication.getName();
        }

        if (clubId != null) {
            return ResponseEntity.ok(eventService.getEventsByClub(clubId, username));
        } else if (upcoming != null && upcoming) {
            return ResponseEntity.ok(eventService.getUpcomingEvents(username));
        } else {
            return ResponseEntity.ok(eventService.getAllEvents(username));
        }
    }

    @GetMapping("/my-events")
    public ResponseEntity<List<EventDTO>> getMyEvents(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(eventService.getEventsForClubHead(username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id, Authentication authentication) {
        String username = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            username = authentication.getName();
        }
        return ResponseEntity.ok(eventService.getEventById(id, username));
    }

    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@Valid @RequestBody EventDTO eventDTO) {
        EventDTO created = eventService.createEvent(eventDTO);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(@PathVariable Long id,
            @Valid @RequestBody EventDTO eventDTO,
            Authentication authentication) {
        String username = authentication.getName();
        EventDTO updated = eventService.updateEventWithAuth(id, eventDTO, username);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/register")
    public ResponseEntity<?> registerForEvent(@PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        var registration = eventService.registerForEvent(id, username);

        // Return registration ID for payment flow
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Successfully registered for the event");
        response.put("registrationId", registration.getId());
        response.put("paymentRequired", registration.getPaymentRequired());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/registration")
    public ResponseEntity<MessageResponse> cancelRegistration(@PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        eventService.cancelRegistration(id, username);
        return ResponseEntity.ok(new MessageResponse("Registration cancelled successfully"));
    }

    @GetMapping("/{id}/registrations")
    public ResponseEntity<List<EventRegistrationDTO>> getEventRegistrations(
            @PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(eventService.getEventRegistrations(id, username));
    }

    @DeleteMapping("/registrations/{registrationId}")
    public ResponseEntity<MessageResponse> removeRegistration(
            @PathVariable Long registrationId,
            Authentication authentication) {
        String username = authentication.getName();
        eventService.removeRegistrationById(registrationId, username);
        return ResponseEntity.ok(new MessageResponse("Registration removed successfully"));
    }
}
