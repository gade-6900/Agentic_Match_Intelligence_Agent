package com.ipl.assistant.repository;

import com.ipl.assistant.model.MatchScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchScoreRepository extends JpaRepository<MatchScore, Long> {
    List<MatchScore> findByMatchId(Long matchId);
    Optional<MatchScore> findByMatchIdAndTeam(Long matchId, String team);
}
