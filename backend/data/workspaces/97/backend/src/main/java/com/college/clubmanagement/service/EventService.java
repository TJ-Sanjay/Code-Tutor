package com.college.clubmanagement.service;

import com.college.clubmanagement.dto.EventDTO;
import com.college.clubmanagement.dto.EventRegistrationDTO;
import com.college.clubmanagement.entity.*;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.exception.ResourceNotFoundException;
import com.college.clubmanagement.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

        @Autowired
        private EventRepository eventRepository;

        @Autowired
        private ClubRepository clubRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private EventRegistrationRepository eventRegistrationRepository;

        @Autowired
        private NotificationService notificationService;

        @Transactional(readOnly = true)
        public List<EventDTO> getAllEvents() {
                return getAllEvents(null);
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getAllEvents(String username) {
                return eventRepository.findAll().stream()
                                .map(event -> convertToDTO(event, username))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getUpcomingEvents() {
                return getUpcomingEvents(null);
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getUpcomingEvents(String username) {
                return eventRepository.findUpcomingEvents(LocalDateTime.now().minusHours(24)).stream()
                                .map(event -> convertToDTO(event, username))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getPendingEvents() {
                return eventRepository.findByStatus(EventStatus.PENDING).stream()
                                .map(this::convertToDTO)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getEventsByClub(Long clubId) {
                return getEventsByClub(clubId, null);
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getEventsByClub(Long clubId, String username) {
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));
                return eventRepository.findByClub(club).stream()
                                .map(event -> convertToDTO(event, username))
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public EventDTO getEventById(Long id) {
                return getEventById(id, null);
        }

        @Transactional(readOnly = true)
        public EventDTO getEventById(Long id, String username) {
                if (id == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

                EventDTO dto = convertToDTO(event);

                if (username != null) {
                        User user = userRepository.findByUsername(username)
                                        .orElse(null);
                        if (user != null) {
                                var registration = eventRegistrationRepository.findByEventAndUser(event, user);
                                dto.setIsRegistered(registration.isPresent());
                                if (registration.isPresent()) {
                                        dto.setIsPaymentCompleted(registration.get().getPaymentCompleted());
                                }
                        }
                }

                return dto;
        }

        @Transactional
        public EventDTO createEvent(EventDTO eventDTO) {
                Long clubId = eventDTO.getClubId();
                if (clubId == null)
                        throw new BadRequestException("Club ID cannot be null");
                Club club = clubRepository.findById(clubId)
                                .orElseThrow(() -> new ResourceNotFoundException("Club not found"));

                // Validate dates
                LocalDateTime now = LocalDateTime.now();
                if (eventDTO.getDateTime().isBefore(now)) {
                        throw new BadRequestException("Event date must be in the future");
                }
                if (eventDTO.getRegistrationDeadline().isAfter(eventDTO.getDateTime()) ||
                                eventDTO.getRegistrationDeadline().isEqual(eventDTO.getDateTime())) {
                        throw new BadRequestException("Registration deadline must be before event date");
                }
                if (eventDTO.getRegistrationDeadline().isBefore(now)) {
                        throw new BadRequestException("Registration deadline must be in the future");
                }
                if (eventDTO.getMaxParticipants() != null && eventDTO.getMaxParticipants() < 1) {
                        throw new BadRequestException("Maximum participants must be at least 1");
                }

                Event event = new Event();
                event.setName(eventDTO.getName());
                event.setDescription(eventDTO.getDescription());
                event.setClub(club);
                event.setDateTime(eventDTO.getDateTime());
                event.setVenue(eventDTO.getVenue());
                event.setEntryFee(eventDTO.getEntryFee() != null ? eventDTO.getEntryFee() : BigDecimal.ZERO);
                event.setMaxParticipants(eventDTO.getMaxParticipants());
                event.setRegistrationDeadline(eventDTO.getRegistrationDeadline());
                event.setPosterUrl(eventDTO.getPosterUrl());
                event.setRules(eventDTO.getRules());
                event.setContactEmail(eventDTO.getContactEmail());
                event.setContactPhone(eventDTO.getContactPhone());
                event.setStatus(EventStatus.PENDING);

                Event savedEvent = eventRepository.save(event);
                return convertToDTO(savedEvent);
        }

        @Transactional
        public EventDTO updateEvent(Long id, EventDTO eventDTO) {
                if (id == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));

                event.setName(eventDTO.getName());
                event.setDescription(eventDTO.getDescription());
                event.setDateTime(eventDTO.getDateTime());
                event.setVenue(eventDTO.getVenue());
                event.setEntryFee(eventDTO.getEntryFee());
                event.setMaxParticipants(eventDTO.getMaxParticipants());
                event.setRegistrationDeadline(eventDTO.getRegistrationDeadline());
                event.setPosterUrl(eventDTO.getPosterUrl());
                event.setRules(eventDTO.getRules());
                event.setContactEmail(eventDTO.getContactEmail());
                event.setContactPhone(eventDTO.getContactPhone());

                Event updatedEvent = eventRepository.save(event);
                return convertToDTO(updatedEvent);
        }

        @Transactional
        public void approveEvent(Long id) {
                if (id == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
                event.setStatus(EventStatus.APPROVED);
                eventRepository.save(event);

                // Notify club heads
                event.getClub().getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Event Approved",
                                        "Your event '" + event.getName() + "' has been approved and is now live.",
                                        NotificationType.EVENT_REMINDER,
                                        "/events/" + event.getId());
                });
        }

        @Transactional
        public void rejectEvent(Long id, String reason) {
                if (id == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
                event.setStatus(EventStatus.REJECTED);
                eventRepository.save(event);

                // Notify club heads
                event.getClub().getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Event Rejected",
                                        "Your event '" + event.getName() + "' was not approved. Reason: " + reason,
                                        NotificationType.EVENT_REMINDER,
                                        "/clubhead/dashboard");
                });
        }

        @Transactional
        public void deactivateEvent(Long id) {
                if (id == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
                event.setStatus(EventStatus.INACTIVE);
                eventRepository.save(event);

                // Notify club heads
                event.getClub().getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Event Deactivated",
                                        "Your event '" + event.getName()
                                                        + "' has been deactivated by the administrator.",
                                        NotificationType.EVENT_REMINDER,
                                        "/clubhead/dashboard");
                });
        }

        @Transactional
        public void activateEvent(Long id) {
                if (id == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
                event.setStatus(EventStatus.APPROVED);
                eventRepository.save(event);

                // Notify club heads
                event.getClub().getClubHeads().forEach(head -> {
                        notificationService.createNotification(
                                        head,
                                        "Event Activated",
                                        "Your event '" + event.getName() + "' has been reactivated and is now live.",
                                        NotificationType.EVENT_REMINDER,
                                        "/events/" + event.getId());
                });
        }

        @Transactional
        public EventRegistration registerForEvent(Long eventId, String username) {
                if (eventId == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(eventId)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Check if user is a member of the club organizing the event
                Club club = event.getClub();
                boolean isMember = club.getMembers().stream()
                                .anyMatch(member -> member.getUser().getId().equals(user.getId()));

                if (!isMember) {
                        throw new BadRequestException(
                                        "You must be a member of " + club.getName() + " to register for this event");
                }

                if (!event.isRegistrationOpen()) {
                        throw new BadRequestException("Registration is closed for this event");
                }

                if (eventRegistrationRepository.existsByEventAndUser(event, user)) {
                        throw new BadRequestException("You are already registered for this event");
                }

                EventRegistration registration = new EventRegistration();
                registration.setEvent(event);
                registration.setUser(user);
                registration.setPaymentRequired(event.getEntryFee().compareTo(BigDecimal.ZERO) > 0);
                registration.setPaymentCompleted(event.getEntryFee().compareTo(BigDecimal.ZERO) == 0);

                EventRegistration saved = eventRegistrationRepository.save(registration);

                // Send notification
                notificationService.createNotification(
                                user,
                                "Event Registration Successful",
                                "You are successfully registered for " + event.getName(),
                                NotificationType.EVENT_REMINDER, // Using reminder as a generic type or I could check
                                                                 // for more types
                                "/events/" + event.getId());

                return saved;
        }

        private EventDTO convertToDTO(Event event) {
                return convertToDTO(event, null);
        }

        private EventDTO convertToDTO(Event event, String username) {
                EventDTO dto = new EventDTO();
                dto.setId(event.getId());
                dto.setName(event.getName());
                dto.setDescription(event.getDescription());
                dto.setClubId(event.getClub().getId());
                dto.setClubName(event.getClub().getName());
                dto.setDateTime(event.getDateTime());
                dto.setVenue(event.getVenue());
                dto.setEntryFee(event.getEntryFee());
                dto.setMaxParticipants(event.getMaxParticipants());
                dto.setRegistrationDeadline(event.getRegistrationDeadline());
                dto.setPosterUrl(event.getPosterUrl());
                dto.setRules(event.getRules());
                dto.setContactEmail(event.getContactEmail());
                dto.setContactPhone(event.getContactPhone());
                dto.setStatus(event.getStatus());
                dto.setRegistrationCount(event.getRegistrationCount());
                dto.setIsFull(event.isFull());
                dto.setIsRegistrationOpen(event.isRegistrationOpen());
                dto.setCreatedAt(event.getCreatedAt());

                // Populate isRegistered and isPaymentCompleted if username is provided
                if (username != null) {
                        User user = userRepository.findByUsername(username).orElse(null);
                        if (user != null) {
                                eventRegistrationRepository.findByEventAndUser(event, user)
                                                .ifPresentOrElse(registration -> {
                                                        dto.setIsRegistered(true);
                                                        dto.setIsPaymentCompleted(registration.getPaymentCompleted());
                                                }, () -> {
                                                        dto.setIsRegistered(false);
                                                        dto.setIsPaymentCompleted(false);
                                                });
                        }
                }

                return dto;
        }

        @Transactional(readOnly = true)
        public boolean isClubHeadOfEvent(Long eventId, String username) {
                if (eventId == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(eventId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Event not found with id: " + eventId));

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Admin has full permission
                if (user.getRoles().contains(Role.ADMIN)) {
                        return true;
                }

                return event.getClub().getClubHeads().stream()
                                .anyMatch(head -> head.getId().equals(user.getId()));
        }

        @Transactional(readOnly = true)
        public List<EventDTO> getEventsForClubHead(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Get all clubs where this user is a club head
                List<Club> managedClubs = clubRepository.findAll().stream()
                                .filter(club -> club.getClubHeads().stream()
                                                .anyMatch(head -> head.getId().equals(user.getId())))
                                .collect(Collectors.toList());

                // Get all events from those clubs
                return managedClubs.stream()
                                .flatMap(club -> eventRepository.findByClub(club).stream())
                                .map(this::convertToDTO)
                                .collect(Collectors.toList());
        }

        @Transactional
        public EventDTO updateEventWithAuth(Long id, EventDTO eventDTO, String username) {
                // Verify the user is a club head of this event's club
                if (!isClubHeadOfEvent(id, username)) {
                        throw new BadRequestException("You are not authorized to update this event");
                }

                return updateEvent(id, eventDTO);
        }

        @Transactional
        public void cancelRegistration(Long eventId, String username) {
                if (eventId == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(eventId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Event not found with id: " + eventId));

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                EventRegistration registration = eventRegistrationRepository.findByEventAndUser(event, user)
                                .orElseThrow(() -> new BadRequestException("You are not registered for this event"));

                // Check if payment was already completed - may need refund
                if (registration.getPaymentCompleted() && registration.getPaymentRequired()) {
                        // In production, handle refund logic here
                }

                eventRegistrationRepository.delete(registration);
        }

        @Transactional(readOnly = true)
        public List<EventRegistrationDTO> getEventRegistrations(Long eventId, String username) {
                if (eventId == null)
                        throw new BadRequestException("Event ID cannot be null");
                Event event = eventRepository.findById(eventId)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

                // Verify user is the club head
                if (!isClubHeadOfEvent(eventId, username)) {
                        throw new BadRequestException("Only event creators can view registrations");
                }

                return eventRegistrationRepository.findByEvent(event).stream()
                                .map(this::convertToRegistrationDTO)
                                .collect(Collectors.toList());
        }

        @Transactional
        public void removeRegistrationById(Long registrationId, String username) {
                if (registrationId == null)
                        throw new BadRequestException("Registration ID cannot be null");
                EventRegistration registration = eventRegistrationRepository.findById(registrationId)
                                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

                // Verify user is the club head
                if (!isClubHeadOfEvent(registration.getEvent().getId(), username)) {
                        throw new BadRequestException("Only event creators can remove registrations");
                }

                // Handle refund if payment was completed
                if (registration.getPaymentCompleted() && registration.getPaymentRequired()) {
                        // In production, handle refund logic here
                }

                eventRegistrationRepository.delete(registration);
        }

        private EventRegistrationDTO convertToRegistrationDTO(EventRegistration reg) {
                EventRegistrationDTO dto = new EventRegistrationDTO();
                dto.setId(reg.getId());
                dto.setEventId(reg.getEvent().getId());
                dto.setEventName(reg.getEvent().getName());
                dto.setUserId(reg.getUser().getId());
                dto.setUserName(reg.getUser().getFirstName() + " " + reg.getUser().getLastName());
                dto.setUserEmail(reg.getUser().getEmail());
                dto.setUserDepartment(reg.getUser().getDepartment());
                dto.setUserYear(reg.getUser().getYear());
                dto.setUserPhone(reg.getUser().getPhone());
                dto.setPaymentRequired(reg.getPaymentRequired());
                dto.setPaymentCompleted(reg.getPaymentCompleted());
                dto.setAttended(reg.getAttended());
                dto.setRegisteredAt(reg.getRegisteredAt());
                return dto;
        }
}
