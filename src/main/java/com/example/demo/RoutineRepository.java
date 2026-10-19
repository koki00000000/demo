package com.example.demo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findAllByUserIdOrderByIdAsc(Long userId);
    Optional<Routine> findByIdAndUserId(Long id, Long userId);
}
