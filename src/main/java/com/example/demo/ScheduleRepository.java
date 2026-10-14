package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
// JpaRepositoryを継承することで、save() や findAll() が自動で使えるようになります
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}