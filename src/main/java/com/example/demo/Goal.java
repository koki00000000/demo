package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Lob
    private String requirements;

    @Lob
    private String plan;
    private LocalDateTime createdAt;
    private boolean active;

    public Goal() {
    }

    public Goal(String title, String requirements, String plan) {
        this.title = title;
        this.requirements = requirements;
        this.plan = plan;
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getRequirements() { return requirements; }
    public String getPlan() { return plan; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }
}
