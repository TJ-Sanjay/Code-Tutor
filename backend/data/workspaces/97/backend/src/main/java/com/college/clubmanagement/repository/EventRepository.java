package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Club;
import com.college.clubmanagement.entity.Event;
import com.college.clubmanagement.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByClub(Club club);

    List<Event> findByStatus(EventStatus status);

    @Query("SELECT e FROM Event e WHERE e.status = 'APPROVED' AND e.dateTime > :limit ORDER BY e.dateTime ASC")
    List<Event> findUpcomingEvents(@Param("limit") LocalDateTime limit);

    @Query("SELECT e FROM Event e WHERE e.dateTime BETWEEN :start AND :end ORDER BY e.dateTime ASC")
    List<Event> findEventsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e FROM Event e WHERE e.club = :club AND e.status = 'APPROVED' AND e.dateTime > :now ORDER BY e.dateTime ASC")
    List<Event> findUpcomingEventsByClub(@Param("club") Club club, @Param("now") LocalDateTime now);

    List<Event> findByStatusAndUpdatedAtBefore(EventStatus status, LocalDateTime cutoff);
}
