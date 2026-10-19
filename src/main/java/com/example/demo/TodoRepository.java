package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByScheduledDateOrderByCreatedAtAsc(LocalDate scheduledDate);
    List<Todo> findAllByGoalIdAndScheduledDateOrderByCreatedAtAsc(Long goalId, LocalDate scheduledDate);
    void deleteByGoalId(Long goalId);
    List<Todo> findAllByUserIdAndScheduledDateOrderByCreatedAtAsc(Long userId, LocalDate scheduledDate);
    List<Todo> findAllByUserIdAndGoalIdAndScheduledDateOrderByCreatedAtAsc(Long userId, Long goalId, LocalDate scheduledDate);
    Optional<Todo> findByIdAndUserId(Long id, Long userId);
}
