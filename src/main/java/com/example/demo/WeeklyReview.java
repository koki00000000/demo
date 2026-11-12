package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

@Entity
public class WeeklyReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private LocalDate reviewDate;
    @Lob private String did;
    @Lob private String trouble;
    private String previousTryResult;
    @Lob private String reason;
    @Lob private String nextTry;
    private String whenToDo;
    private LocalDateTime updatedAt;

    public WeeklyReview() {}
    public WeeklyReview(Long userId, LocalDate reviewDate) {
        this.userId = userId;
        this.reviewDate = reviewDate;
    }
    public void update(String did, String trouble, String previousTryResult, String reason,
            String nextTry, String whenToDo) {
        this.did = did; this.trouble = trouble; this.previousTryResult = previousTryResult;
        this.reason = reason; this.nextTry = nextTry; this.whenToDo = whenToDo;
        this.updatedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getReviewDate() { return reviewDate; }
    public String getDid() { return did; }
    public String getTrouble() { return trouble; }
    public String getPreviousTryResult() { return previousTryResult; }
    public String getReason() { return reason; }
    public String getNextTry() { return nextTry; }
    public String getWhenToDo() { return whenToDo; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
