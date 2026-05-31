package com.ipl.assistant.model;

import jakarta.persistence.*;

@Entity
@Table(name = "match_info")
public class MatchInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String team1;
    private String team2;
    private String tossWinner;
    private String tossDecision;
    private String status; // UPCOMING, IN_PROGRESS, COMPLETED
    private String winner;
    private String margin;
    private Integer targetRuns;
    private Integer currentInnings; // 1 or 2

    @Column(columnDefinition = "TEXT")
    private String aiPreview;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTeam1() { return team1; }
    public void setTeam1(String team1) { this.team1 = team1; }
    public String getTeam2() { return team2; }
    public void setTeam2(String team2) { this.team2 = team2; }
    public String getTossWinner() { return tossWinner; }
    public void setTossWinner(String tossWinner) { this.tossWinner = tossWinner; }
    public String getTossDecision() { return tossDecision; }
    public void setTossDecision(String tossDecision) { this.tossDecision = tossDecision; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public String getMargin() { return margin; }
    public void setMargin(String margin) { this.margin = margin; }
    public Integer getTargetRuns() { return targetRuns; }
    public void setTargetRuns(Integer targetRuns) { this.targetRuns = targetRuns; }
    public Integer getCurrentInnings() { return currentInnings; }
    public void setCurrentInnings(Integer currentInnings) { this.currentInnings = currentInnings; }
    public String getAiPreview() { return aiPreview; }
    public void setAiPreview(String aiPreview) { this.aiPreview = aiPreview; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
}
