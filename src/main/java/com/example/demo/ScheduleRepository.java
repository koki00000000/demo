package com.example.demo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// JpaRepositoryを継承することで、save() や findAll() が自動で使えるようになります
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findAllByDateTimeBetweenOrderByDateTimeAsc(LocalDateTime start, LocalDateTime end);
    List<Schedule> findAllByUserIdOrderByDateTimeAsc(Long userId);
    List<Schedule> findAllByUserIdAndDateTimeBetweenOrderByDateTimeAsc(Long userId, LocalDateTime start, LocalDateTime end);
    Optional<Schedule> findByIdAndUserId(Long id, Long userId);
}
