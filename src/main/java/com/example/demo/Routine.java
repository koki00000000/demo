package com.example.demo;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Routine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private boolean completed;
    private LocalDate completedDate;
    private Long userId;

    public Routine() {}
    public Routine(String title) { this.title = title; }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public boolean isCompleted() { return completed && LocalDate.now().equals(completedDate); }
    public void setCompleted(boolean completed) {
        this.completed = completed;
        this.completedDate = completed ? LocalDate.now() : null;
    }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
