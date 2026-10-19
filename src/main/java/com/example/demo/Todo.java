package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long goalId;
    private String title;
    private LocalDate scheduledDate;
    private boolean completed;
    private LocalDateTime createdAt;
    private Long userId;

    public Todo() {
    }

    public Todo(Long goalId, String title, LocalDate scheduledDate) {
        this.goalId = goalId;
        this.title = title;
        this.scheduledDate = scheduledDate;
        this.completed = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getGoalId() { return goalId; }
    public String getTitle() { return title; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public boolean isCompleted() { return completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
