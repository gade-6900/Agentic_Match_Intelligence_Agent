package com.ipl.assistant.repository;

import com.ipl.assistant.model.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerStatsRepository extends JpaRepository<PlayerStats, Long> {
    List<PlayerStats> findByMatchId(Long matchId);
    Optional<PlayerStats> findByMatchIdAndPlayerName(Long matchId, String playerName);
}
