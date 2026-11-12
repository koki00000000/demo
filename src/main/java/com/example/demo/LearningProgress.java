package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class LearningProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String completedWeeks = "";

    public LearningProgress() {}
    public LearningProgress(Long userId) { this.userId = userId; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCompletedWeeks() { return completedWeeks; }
    public void setCompletedWeeks(String completedWeeks) {
        this.completedWeeks = completedWeeks == null ? "" : completedWeeks;
    }
}
