package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class LineSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String accessToken;
    private String userId;
    private Long ownerUserId;

    public LineSettings() {}
    public LineSettings(String accessToken, String userId) { this.accessToken = accessToken; this.userId = userId; }
    public Long getId() { return id; }
    public String getAccessToken() { return accessToken; }
    public String getUserId() { return userId; }
    public void update(String accessToken, String userId) { this.accessToken = accessToken; this.userId = userId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
}
