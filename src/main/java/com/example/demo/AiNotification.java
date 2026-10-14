package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class AiNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scheduleId;
    private String scheduleTitle;
    private LocalDateTime scheduledFor;
    private LocalDateTime createdAt;
    private String message;

    public AiNotification() {
    }

    public AiNotification(Long scheduleId, String scheduleTitle, LocalDateTime scheduledFor, String message) {
        this.scheduleId = scheduleId;
        this.scheduleTitle = scheduleTitle;
        this.scheduledFor = scheduledFor;
        this.createdAt = LocalDateTime.now();
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getScheduleId() { return scheduleId; }
    public String getScheduleTitle() { return scheduleTitle; }
    public LocalDateTime getScheduledFor() { return scheduledFor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getMessage() { return message; }
}
