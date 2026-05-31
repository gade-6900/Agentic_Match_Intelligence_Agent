package com.ipl.assistant.service;

import com.ipl.assistant.model.*;
import com.ipl.assistant.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MatchService {

    @Autowired
    private MatchInfoRepository matchInfoRepository;

    @Autowired
    private MatchScoreRepository matchScoreRepository;

    @Autowired
    private MatchBallRepository matchBallRepository;

    @Autowired
    private PlayerStatsRepository playerStatsRepository;

    @Autowired
    private GeminiService geminiService;

    // Predefined squads
    private static final Map<String, List<SquadPlayer>> SQUADS = new HashMap<>();

    static {
        // Mumbai Indians
        SQUADS.put("Mumbai Indians", Arrays.asList(
                new SquadPlayer("Rohit Sharma", "BATTER"),
                new SquadPlayer("Ishan Kishan", "BATTER"),
                new SquadPlayer("Suryakumar Yadav", "BATTER"),
                new SquadPlayer("Tilak Varma", "BATTER"),
                new SquadPlayer("Hardik Pandya", "ALL_ROUNDER"),
                new SquadPlayer("Tim David", "BATTER"),
                new SquadPlayer("Romario Shepherd", "ALL_ROUNDER"),
                new SquadPlayer("Gerald Coetzee", "BOWLER"),
                new SquadPlayer("Jasprit Bumrah", "BOWLER"),
                new SquadPlayer("Piyush Chawla", "BOWLER"),
                new SquadPlayer("Akash Madhwal", "BOWLER")
        ));

        // Chennai Super Kings
        SQUADS.put("Chennai Super Kings", Arrays.asList(
                new SquadPlayer("Ruturaj Gaikwad", "BATTER"),
                new SquadPlayer("Rachin Ravindra", "ALL_ROUNDER"),
                new SquadPlayer("Ajinkya Rahane", "BATTER"),
                new SquadPlayer("Daryl Mitchell", "ALL_ROUNDER"),
                new SquadPlayer("Shivam Dube", "BATTER"),
                new SquadPlayer("Ravindra Jadeja", "ALL_ROUNDER"),
                new SquadPlayer("MS Dhoni", "BATTER"),
                new SquadPlayer("Mitchell Santner", "ALL_ROUNDER"),
                new SquadPlayer("Shardul Thakur", "BOWLER"),
                new SquadPlayer("Tushar Deshpande", "BOWLER"),
                new SquadPlayer("Matheesha Pathirana", "BOWLER")
        ));

        // Royal Challengers Bangalore
        SQUADS.put("Royal Challengers Bangalore", Arrays.asList(
                new SquadPlayer("Virat Kohli", "BATTER"),
                new SquadPlayer("Faf du Plessis", "BATTER"),
                new SquadPlayer("Will Jacks", "ALL_ROUNDER"),
                new SquadPlayer("Rajat Patidar", "BATTER"),
                new SquadPlayer("Glenn Maxwell", "ALL_ROUNDER"),
                new SquadPlayer("Cameron Green", "ALL_ROUNDER"),
                new SquadPlayer("Dinesh Karthik", "BATTER"),
                new SquadPlayer("Mahipal Lomror", "BATTER"),
                new SquadPlayer("Karn Sharma", "BOWLER"),
                new SquadPlayer("Mohammed Siraj", "BOWLER"),
                new SquadPlayer("Yash Dayal", "BOWLER")
        ));

        // Kolkata Knight Riders
        SQUADS.put("Kolkata Knight Riders", Arrays.asList(
                new SquadPlayer("Sunil Narine", "ALL_ROUNDER"),
                new SquadPlayer("Phil Salt", "BATTER"),
                new SquadPlayer("Angkrish Raghuvanshi", "BATTER"),
                new SquadPlayer("Shreyas Iyer", "BATTER"),
                new SquadPlayer("Venkatesh Iyer", "ALL_ROUNDER"),
                new SquadPlayer("Rinku Singh", "BATTER"),
                new SquadPlayer("Andre Russell", "ALL_ROUNDER"),
                new SquadPlayer("Ramandeep Singh", "BATTER"),
                new SquadPlayer("Mitchell Starc", "BOWLER"),
                new SquadPlayer("Varun Chakaravarthy", "BOWLER"),
                new SquadPlayer("Harshit Rana", "BOWLER")
        ));
    }

    public List<MatchInfo> getAllMatches() {
        return matchInfoRepository.findAll();
    }

    public Optional<MatchInfo> getMatchInfo(Long id) {
        return matchInfoRepository.findById(id);
    }

    public List<MatchScore> getMatchScores(Long matchId) {
        return matchScoreRepository.findByMatchId(matchId);
    }

    public List<PlayerStats> getPlayerStats(Long matchId) {
        return playerStatsRepository.findByMatchId(matchId);
    }

    public List<MatchBall> getMatchCommentary(Long matchId) {
        return matchBallRepository.findByMatchIdOrderByInningsDescOverNumDescBallNumDesc(matchId);
    }

    @Transactional
    public MatchInfo createMatch(String team1, String team2) {
        // 1. Create MatchInfo
        MatchInfo matchInfo = new MatchInfo();
        matchInfo.setTeam1(team1);
        matchInfo.setTeam2(team2);
        
        // Randomly decide toss
        String tossWinner = Math.random() < 0.5 ? team1 : team2;
        String tossDecision = Math.random() < 0.5 ? "BAT" : "BOWL";
        matchInfo.setTossWinner(tossWinner);
        matchInfo.setTossDecision(tossDecision);
        matchInfo.setStatus("IN_PROGRESS");
        matchInfo.setCurrentInnings(1);
        
        matchInfo = matchInfoRepository.save(matchInfo);

        // 2. Setup batting order
        boolean team1BatsFirst = (tossWinner.equals(team1) && tossDecision.equals("BAT"))
                || (tossWinner.equals(team2) && tossDecision.equals("BOWL"));

        // 3. Create MatchScore entries
        MatchScore score1 = new MatchScore();
        score1.setMatchId(matchInfo.getId());
        score1.setTeam(team1);
        score1.setRuns(0);
        score1.setWickets(0);
        score1.setOvers(0);
        score1.setBalls(0);
        score1.setIsBatting(team1BatsFirst);
        matchScoreRepository.save(score1);

        MatchScore score2 = new MatchScore();
        score2.setMatchId(matchInfo.getId());
        score2.setTeam(team2);
        score2.setRuns(0);
        score2.setWickets(0);
        score2.setOvers(0);
        score2.setBalls(0);
        score2.setIsBatting(!team1BatsFirst);
        matchScoreRepository.save(score2);

        // 4. Prepopulate PlayerStats
        prepopulateSquad(matchInfo.getId(), team1);
        prepopulateSquad(matchInfo.getId(), team2);

        // 5. Generate AI Preview
        String preview = geminiService.generateMatchPreview(matchInfo);
        matchInfo.setAiPreview(preview);
        
        return matchInfoRepository.save(matchInfo);
    }

    private void prepopulateSquad(Long matchId, String teamName) {
        List<SquadPlayer> squad = SQUADS.getOrDefault(teamName, Collections.emptyList());
        for (SquadPlayer player : squad) {
            PlayerStats stats = new PlayerStats();
            stats.setMatchId(matchId);
            stats.setPlayerName(player.name);
            stats.setTeamName(teamName);
            stats.setRole(player.role);
            playerStatsRepository.save(stats);
        }
    }

    @Transactional
    public void resetMatch(Long matchId) {
        MatchInfo match = matchInfoRepository.findById(matchId).orElseThrow();
        
        // Delete balls
        List<MatchBall> balls = matchBallRepository.findByMatchId(matchId);
        matchBallRepository.deleteAll(balls);

        // Reset scores
        List<MatchScore> scores = matchScoreRepository.findByMatchId(matchId);
        boolean team1BatsFirst = (match.getTossWinner().equals(match.getTeam1()) && match.getTossDecision().equals("BAT"))
                || (match.getTossWinner().equals(match.getTeam2()) && match.getTossDecision().equals("BOWL"));

        for (MatchScore s : scores) {
            s.setRuns(0);
            s.setWickets(0);
            s.setOvers(0);
            s.setBalls(0);
            if (s.getTeam().equals(match.getTeam1())) {
                s.setIsBatting(team1BatsFirst);
            } else {
                s.setIsBatting(!team1BatsFirst);
            }
            matchScoreRepository.save(s);
        }

        // Reset player stats
        List<PlayerStats> statsList = playerStatsRepository.findByMatchId(matchId);
        for (PlayerStats stats : statsList) {
            stats.setRunsScored(0);
            stats.setBallsFaced(0);
            stats.setFours(0);
            stats.setSixes(0);
            stats.setStrikeRate(0.0);
            stats.setOversBowled(0.0);
            stats.setRunsConceded(0);
            stats.setWicketsTaken(0);
            stats.setEconomyRate(0.0);
            playerStatsRepository.save(stats);
        }

        // Reset match info
        match.setStatus("IN_PROGRESS");
        match.setWinner(null);
        match.setMargin(null);
        match.setTargetRuns(null);
        match.setCurrentInnings(1);
        match.setAiSummary(null);
        
        // Re-generate preview if needed, or keep same
        matchInfoRepository.save(match);
    }

    @Transactional
    public void resetAll() {
        matchBallRepository.deleteAll();
        playerStatsRepository.deleteAll();
        matchScoreRepository.deleteAll();
        matchInfoRepository.deleteAll();
    }

    private static class SquadPlayer {
        String name;
        String role;

        SquadPlayer(String name, String role) {
            this.name = name;
            this.role = role;
        }
    }
}
