package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByScheduledDateOrderByCreatedAtAsc(LocalDate scheduledDate);
    List<Todo> findAllByGoalIdAndScheduledDateOrderByCreatedAtAsc(Long goalId, LocalDate scheduledDate);
    void deleteByGoalId(Long goalId);
}
