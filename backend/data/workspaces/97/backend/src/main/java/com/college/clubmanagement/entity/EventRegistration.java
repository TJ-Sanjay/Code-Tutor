package com.college.clubmanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_registrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(mappedBy = "registration", cascade = CascadeType.ALL)
    private Payment payment;

    @Column(nullable = false)
    private Boolean paymentRequired;

    @Column(nullable = false)
    private Boolean paymentCompleted = false;

    @Column(length = 500)
    private String qrCodeUrl;

    @Column(nullable = false)
    private Boolean attended = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime registeredAt;
}
