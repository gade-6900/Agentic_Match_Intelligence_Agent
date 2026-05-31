package com.ipl.assistant.repository;

import com.ipl.assistant.model.MatchBall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchBallRepository extends JpaRepository<MatchBall, Long> {
    List<MatchBall> findByMatchId(Long matchId);
    List<MatchBall> findByMatchIdOrderByInningsDescOverNumDescBallNumDesc(Long matchId);
    List<MatchBall> findByMatchIdOrderByInningsAscOverNumAscBallNumAsc(Long matchId);
}
