package com.example.demo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiNotificationRepository extends JpaRepository<AiNotification, Long> {
    boolean existsByScheduleIdAndScheduledFor(Long scheduleId, java.time.LocalDateTime scheduledFor);
    List<AiNotification> findAllByOrderByCreatedAtDesc();
    void deleteByScheduleId(Long scheduleId);
}
