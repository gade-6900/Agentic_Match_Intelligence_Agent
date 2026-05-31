package com.ipl.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name = "match_score")
public class MatchScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long matchId;
    private String team;
    private Integer runs;
    private Integer wickets;
    private Integer overs;
    private Integer balls;
    private Boolean isBatting;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public Integer getRuns() { return runs; }
    public void setRuns(Integer runs) { this.runs = runs; }
    public Integer getWickets() { return wickets; }
    public void setWickets(Integer wickets) { this.wickets = wickets; }
    public Integer getOvers() { return overs; }
    public void setOvers(Integer overs) { this.overs = overs; }
    public Integer getBalls() { return balls; }
    public void setBalls(Integer balls) { this.balls = balls; }
    public Boolean getIsBatting() { return isBatting; }
    public void setIsBatting(Boolean batting) { isBatting = batting; }
}
