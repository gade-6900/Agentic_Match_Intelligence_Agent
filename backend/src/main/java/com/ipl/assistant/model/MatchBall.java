package com.ipl.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name = "match_ball")
public class MatchBall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long matchId;
    private Integer innings;
    private Integer overNum;
    private Integer ballNum;
    private String batter;
    private String bowler;
    private Integer runsScored;
    private Integer extraRuns;
    private String wicketType; 
    private String dismissedBatter;
    
    @Column(columnDefinition = "TEXT")
    private String commentary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Integer getInnings() { return innings; }
    public void setInnings(Integer innings) { this.innings = innings; }
    public Integer getOverNum() { return overNum; }
    public void setOverNum(Integer overNum) { this.overNum = overNum; }
    public Integer getBallNum() { return ballNum; }
    public void setBallNum(Integer ballNum) { this.ballNum = ballNum; }
    public String getBatter() { return batter; }
    public void setBatter(String batter) { this.batter = batter; }
    public String getBowler() { return bowler; }
    public void setBowler(String bowler) { this.bowler = bowler; }
    public Integer getRunsScored() { return runsScored; }
    public void setRunsScored(Integer runsScored) { this.runsScored = runsScored; }
    public Integer getExtraRuns() { return extraRuns; }
    public void setExtraRuns(Integer extraRuns) { this.extraRuns = extraRuns; }
    public String getWicketType() { return wicketType; }
    public void setWicketType(String wicketType) { this.wicketType = wicketType; }
    public String getDismissedBatter() { return dismissedBatter; }
    public void setDismissedBatter(String dismissedBatter) { this.dismissedBatter = dismissedBatter; }
    public String getCommentary() { return commentary; }
    public void setCommentary(String commentary) { this.commentary = commentary; }
}
