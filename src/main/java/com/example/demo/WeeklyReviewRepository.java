package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, Long> {
    Optional<WeeklyReview> findByUserIdAndReviewDate(Long userId, LocalDate reviewDate);
    List<WeeklyReview> findAllByUserIdOrderByReviewDateDesc(Long userId);
}
