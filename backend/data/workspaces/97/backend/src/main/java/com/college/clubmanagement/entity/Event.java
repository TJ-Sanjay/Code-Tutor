package com.college.clubmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal entryFee = BigDecimal.ZERO;

    private Integer maxParticipants;

    @Column(nullable = false)
    private LocalDateTime registrationDeadline;

    @Column(length = 500)
    private String posterUrl;

    @Column(columnDefinition = "TEXT")
    private String rules;

    @Column(length = 100)
    private String contactEmail;

    @Column(length = 15)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.PENDING;

    @JsonIgnore
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventRegistration> registrations = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public int getRegistrationCount() {
        return registrations != null ? registrations.size() : 0;
    }

    public boolean isFull() {
        return maxParticipants != null && getRegistrationCount() >= maxParticipants;
    }

    public boolean isRegistrationOpen() {
        return status == EventStatus.APPROVED && LocalDateTime.now().isBefore(registrationDeadline) && !isFull();
    }
}
