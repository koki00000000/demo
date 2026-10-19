package com.example.demo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineSettingsRepository extends JpaRepository<LineSettings, Long> {
    Optional<LineSettings> findFirstByOrderByIdAsc();
}
