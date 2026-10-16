package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Notification;
import com.college.clubmanagement.entity.NotificationType;
import com.college.clubmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    List<Notification> findByUser(User user);
    
    List<Notification> findByUserAndReadFalse(User user);
    
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    
    List<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(User user);
    
    List<Notification> findByType(NotificationType type);
    
    Long countByUserAndReadFalse(User user);
}
