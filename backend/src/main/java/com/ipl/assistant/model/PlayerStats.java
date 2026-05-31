package com.ipl.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name = "player_stats")
public class PlayerStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long matchId;
    private String playerName;
    private String teamName;
    private String role; // BATTER, BOWLER, ALL_ROUNDER

    // Batting stats
    private Integer runsScored = 0;
    private Integer ballsFaced = 0;
    private Integer fours = 0;
    private Integer sixes = 0;
    private Double strikeRate = 0.0;

    // Bowling stats
    private Double oversBowled = 0.0; 
    private Integer runsConceded = 0;
    private Integer wicketsTaken = 0;
    private Double economyRate = 0.0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getRunsScored() { return runsScored; }
    public void setRunsScored(Integer runsScored) { this.runsScored = runsScored; }
    public Integer getBallsFaced() { return ballsFaced; }
    public void setBallsFaced(Integer ballsFaced) { this.ballsFaced = ballsFaced; }
    public Integer getFours() { return fours; }
    public void setFours(Integer fours) { this.fours = fours; }
    public Integer getSixes() { return sixes; }
    public void setSixes(Integer sixes) { this.sixes = sixes; }
    public Double getStrikeRate() { return strikeRate; }
    public void setStrikeRate(Double strikeRate) { this.strikeRate = strikeRate; }
    public Double getOversBowled() { return oversBowled; }
    public void setOversBowled(Double oversBowled) { this.oversBowled = oversBowled; }
    public Integer getRunsConceded() { return runsConceded; }
    public void setRunsConceded(Integer runsConceded) { this.runsConceded = runsConceded; }
    public Integer getWicketsTaken() { return wicketsTaken; }
    public void setWicketsTaken(Integer wicketsTaken) { this.wicketsTaken = wicketsTaken; }
    public Double getEconomyRate() { return economyRate; }
    public void setEconomyRate(Double economyRate) { this.economyRate = economyRate; }
}
