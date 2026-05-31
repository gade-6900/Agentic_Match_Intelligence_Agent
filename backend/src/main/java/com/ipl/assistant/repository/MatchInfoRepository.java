package com.ipl.assistant.repository;

import com.ipl.assistant.model.MatchInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchInfoRepository extends JpaRepository<MatchInfo, Long> {
}
