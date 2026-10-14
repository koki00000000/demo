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

    public Schedule() {
    }

    public Schedule(String title, LocalDateTime dateTime, int notifyMinutesBefore) {
        this.title = title;
        this.dateTime = dateTime;
        this.notifyMinutesBefore = notifyMinutesBefore;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getDateTime() { return dateTime; }
    public int getNotifyMinutesBefore() { return notifyMinutesBefore; }
}
