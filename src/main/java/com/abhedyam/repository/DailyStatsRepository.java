package com.abhedyam.repository;

import com.abhedyam.model.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, UUID> {
    Optional<DailyStats> findByOwnerIdAndStatDate(UUID ownerId, LocalDate statDate);
    List<DailyStats> findByOwnerIdAndStatDateBetween(UUID ownerId, LocalDate startDate, LocalDate endDate);
}

