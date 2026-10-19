package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private LocalDateTime dateTime;
    private LocalDateTime endDateTime;
    private int notifyMinutesBefore = 30;
    private String priority = "medium";
    private String category = "その他";

    public Schedule() {
    }

    public Schedule(String title, LocalDateTime dateTime, int notifyMinutesBefore) {
        this(title, dateTime, dateTime.plusHours(1), notifyMinutesBefore, "medium");
    }

    public Schedule(String title, LocalDateTime dateTime, int notifyMinutesBefore, String priority) {
        this(title, dateTime, dateTime.plusHours(1), notifyMinutesBefore, priority);
    }

    public Schedule(String title, LocalDateTime dateTime, LocalDateTime endDateTime,
            int notifyMinutesBefore, String priority) {
        this(title, dateTime, endDateTime, notifyMinutesBefore, priority, "その他");
    }

    public Schedule(String title, LocalDateTime dateTime, LocalDateTime endDateTime,
            int notifyMinutesBefore, String priority, String category) {
        this.title = title;
        this.dateTime = dateTime;
        this.endDateTime = endDateTime == null ? dateTime.plusHours(1) : endDateTime;
        this.notifyMinutesBefore = notifyMinutesBefore;
        this.priority = priority == null ? "medium" : priority;
        this.category = category == null || category.isBlank() ? "その他" : category;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getDateTime() { return dateTime; }
    public LocalDateTime getEndDateTime() { return endDateTime; }
    public int getNotifyMinutesBefore() { return notifyMinutesBefore; }
    public String getPriority() { return priority; }
    public String getCategory() { return category; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public void setEndDateTime(LocalDateTime endDateTime) { this.endDateTime = endDateTime; }
    public void update(String title, LocalDateTime dateTime, LocalDateTime endDateTime,
            int notifyMinutesBefore, String priority, String category) {
        this.title = title;
        this.dateTime = dateTime;
        this.endDateTime = endDateTime;
        this.notifyMinutesBefore = notifyMinutesBefore;
        this.priority = priority == null ? "medium" : priority;
        this.category = category == null || category.isBlank() ? "その他" : category;
    }
}
