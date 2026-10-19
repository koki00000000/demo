package com.example.demo;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineSettingsRepository extends JpaRepository<LineSettings, Long> {
    Optional<LineSettings> findFirstByOrderByIdAsc();
    Optional<LineSettings> findByOwnerUserId(Long ownerUserId);
    List<LineSettings> findAllByOwnerUserIdIsNotNull();
}
