package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Event;
import com.college.clubmanagement.entity.EventRegistration;
import com.college.clubmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {
    
    List<EventRegistration> findByEvent(Event event);
    
    List<EventRegistration> findByUser(User user);
    
    Optional<EventRegistration> findByEventAndUser(Event event, User user);
    
    Boolean existsByEventAndUser(Event event, User user);
    
    Long countByEvent(Event event);
    
    List<EventRegistration> findByEventAndPaymentCompletedTrue(Event event);
}
