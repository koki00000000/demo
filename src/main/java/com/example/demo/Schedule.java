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
    private int notifyMinutesBefore = 30;
    private String priority = "medium";

    public Schedule() {
    }

    public Schedule(String title, LocalDateTime dateTime, int notifyMinutesBefore) {
        this(title, dateTime, notifyMinutesBefore, "medium");
    }

    public Schedule(String title, LocalDateTime dateTime, int notifyMinutesBefore, String priority) {
        this.title = title;
        this.dateTime = dateTime;
        this.notifyMinutesBefore = notifyMinutesBefore;
        this.priority = priority == null ? "medium" : priority;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getDateTime() { return dateTime; }
    public int getNotifyMinutesBefore() { return notifyMinutesBefore; }
    public String getPriority() { return priority; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
}
