package com.college.clubmanagement.service;

import com.college.clubmanagement.dto.NotificationDTO;
import com.college.clubmanagement.entity.Notification;
import com.college.clubmanagement.entity.NotificationType;
import com.college.clubmanagement.entity.User;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.exception.ResourceNotFoundException;
import com.college.clubmanagement.repository.NotificationRepository;
import com.college.clubmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Notification createNotification(User user, String title, String message,
            NotificationType type, String link) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        notification.setRead(false);
        notification.setEmailSent(false);

        return notificationRepository.save(notification);
    }

    public List<NotificationDTO> getUserNotifications(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        System.out.println("Fetching notifications for user: " + username + ". Found: " + notifications.size());
        return notifications.stream()
                .map(this::convertToDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<NotificationDTO> getUnreadNotifications(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user).stream()
                .map(this::convertToDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    private NotificationDTO convertToDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType());
        dto.setLink(notification.getLink());
        dto.setRead(notification.getRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }

    public Long getUnreadCount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long notificationId, String username) {
        if (notificationId == null)
            throw new BadRequestException("Notification ID cannot be null");
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Notification not found");
        }

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Notification> unreadNotifications = notificationRepository.findByUserAndReadFalse(user);
        LocalDateTime now = LocalDateTime.now();

        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(now);
        });

        notificationRepository.saveAll(unreadNotifications);
    }
}
