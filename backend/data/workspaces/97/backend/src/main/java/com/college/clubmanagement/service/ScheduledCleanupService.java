package com.college.clubmanagement.service;

import com.college.clubmanagement.entity.Club;
import com.college.clubmanagement.entity.ClubStatus;
import com.college.clubmanagement.entity.Event;
import com.college.clubmanagement.entity.EventStatus;
import com.college.clubmanagement.repository.ClubRepository;
import com.college.clubmanagement.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCleanupService.class);

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private EventRepository eventRepository;

    // Run every hour
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupRejectedItems() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        // Delete rejected clubs older than 24 hours
        List<Club> rejectedClubs = clubRepository.findByStatusAndUpdatedAtBefore(ClubStatus.REJECTED, cutoff);
        if (!rejectedClubs.isEmpty()) {
            logger.info("Deleting {} rejected clubs older than 24 hours", rejectedClubs.size());
            clubRepository.deleteAll(rejectedClubs);
        }

        // Delete rejected events older than 24 hours
        List<Event> rejectedEvents = eventRepository.findByStatusAndUpdatedAtBefore(EventStatus.REJECTED, cutoff);
        if (!rejectedEvents.isEmpty()) {
            logger.info("Deleting {} rejected events older than 24 hours", rejectedEvents.size());
            eventRepository.deleteAll(rejectedEvents);
        }
    }
}
