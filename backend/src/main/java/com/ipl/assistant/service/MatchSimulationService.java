package com.ipl.assistant.service;

import com.ipl.assistant.model.*;
import com.ipl.assistant.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchSimulationService {

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

    private final Random random = new Random();

    /**
     * Periodically check for IN_PROGRESS matches and simulate the next ball.
     * Scheduled to run every 6 seconds.
     */
    @Scheduled(fixedRate = 6000)
    public void runSimulation() {
        List<MatchInfo> activeMatches = matchInfoRepository.findAll().stream()
                .filter(m -> "IN_PROGRESS".equalsIgnoreCase(m.getStatus()))
                .collect(Collectors.toList());

        for (MatchInfo match : activeMatches) {
            try {
                simulateNextBall(match.getId());
            } catch (Exception e) {
                System.err.println("Error simulating ball for match " + match.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Simulate a single ball for a match.
     */
    @Transactional
    public void simulateNextBall(Long matchId) {
        MatchInfo match = matchInfoRepository.findById(matchId).orElseThrow();
        if (!"IN_PROGRESS".equalsIgnoreCase(match.getStatus())) {
            return;
        }

        List<MatchScore> scores = matchScoreRepository.findByMatchId(matchId);
        MatchScore battingScore = scores.stream().filter(MatchScore::getIsBatting).findFirst().orElseThrow();
        MatchScore bowlingScore = scores.stream().filter(s -> !s.getIsBatting()).findFirst().orElseThrow();

        List<PlayerStats> allStats = playerStatsRepository.findByMatchId(matchId);
        List<PlayerStats> battingSquad = allStats.stream()
                .filter(s -> s.getTeamName().equals(battingScore.getTeam()))
                .sorted(Comparator.comparing(PlayerStats::getId))
                .collect(Collectors.toList());

        List<PlayerStats> bowlingSquad = allStats.stream()
                .filter(s -> s.getTeamName().equals(bowlingScore.getTeam()))
                .sorted(Comparator.comparing(PlayerStats::getId))
                .collect(Collectors.toList());

        List<MatchBall> allBalls = matchBallRepository.findByMatchIdOrderByInningsAscOverNumAscBallNumAsc(matchId);
        List<MatchBall> currentInningsBalls = allBalls.stream()
                .filter(b -> b.getInnings().equals(match.getCurrentInnings()))
                .collect(Collectors.toList());

        // 1. Determine active batters
        Set<String> dismissedBatters = currentInningsBalls.stream()
                .map(MatchBall::getDismissedBatter)
                .filter(Objects::nonNull)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());

        int wicketsFallen = dismissedBatters.size();
        if (wicketsFallen >= 10) {
            endInnings(match, battingScore, bowlingScore, allStats);
            return;
        }

        List<PlayerStats> activeBatters = new ArrayList<>();
        for (int i = 0; i < Math.min(wicketsFallen + 2, 11); i++) {
            PlayerStats p = battingSquad.get(i);
            if (!dismissedBatters.contains(p.getPlayerName())) {
                activeBatters.add(p);
            }
        }

        if (activeBatters.size() < 2) {
            endInnings(match, battingScore, bowlingScore, allStats);
            return;
        }

        PlayerStats striker;
        PlayerStats nonStriker;

        if (currentInningsBalls.isEmpty()) {
            // First ball of innings
            striker = activeBatters.get(0);
            nonStriker = activeBatters.get(1);
        } else {
            MatchBall lastBall = currentInningsBalls.get(currentInningsBalls.size() - 1);
            String lastStrikerName = lastBall.getBatter();

            // Find who was the striker and non-striker
            PlayerStats prevStriker = activeBatters.stream()
                    .filter(p -> p.getPlayerName().equals(lastStrikerName))
                    .findFirst()
                    .orElse(null);

            PlayerStats prevNonStriker = activeBatters.stream()
                    .filter(p -> !p.getPlayerName().equals(lastStrikerName))
                    .findFirst()
                    .orElse(null);

            if (lastBall.getWicketType() != null && !lastBall.getWicketType().isEmpty()) {
                // Wicket fell. Find the new batter
                PlayerStats newBatter = activeBatters.stream()
                        .filter(p -> !p.getPlayerName().equals(prevNonStriker != null ? prevNonStriker.getPlayerName() : ""))
                        .findFirst()
                        .orElse(activeBatters.get(0));

                striker = newBatter; // New batter takes strike
                nonStriker = prevNonStriker != null ? prevNonStriker : activeBatters.stream().filter(p -> !p.getPlayerName().equals(striker.getPlayerName())).findFirst().orElse(null);
            } else {
                // Normal ball - check runs and end of over
                boolean oddRuns = (lastBall.getRunsScored() == 1 || lastBall.getRunsScored() == 3);
                boolean endOfOver = (lastBall.getBallNum() == 6);

                boolean swap = oddRuns ^ endOfOver; // XOR: swap if odd runs OR end of over, but not both

                if (prevStriker != null && prevNonStriker != null) {
                    if (swap) {
                        striker = prevNonStriker;
                        nonStriker = prevStriker;
                    } else {
                        striker = prevStriker;
                        nonStriker = prevNonStriker;
                    }
                } else {
                    striker = activeBatters.get(0);
                    nonStriker = activeBatters.get(1);
                }
            }
        }

        // 2. Determine current bowler
        PlayerStats bowler;
        int currentOverNum = battingScore.getOvers();
        int currentBallNum = battingScore.getBalls() + 1; // 1-indexed for the ball being bowled

        if (currentInningsBalls.isEmpty()) {
            // First over, pick a bowler (e.g. key bowler, index 8, 9, 10)
            bowler = bowlingSquad.get(8 + random.nextInt(3));
        } else {
            MatchBall lastBall = currentInningsBalls.get(currentInningsBalls.size() - 1);
            if (lastBall.getBallNum() == 6) {
                // New over! Pick a different bowler
                List<PlayerStats> eligibleBowlers = bowlingSquad.stream()
                        .filter(p -> p.getRole().equals("BOWLER") || p.getRole().equals("ALL_ROUNDER"))
                        .filter(p -> !p.getPlayerName().equals(lastBall.getBowler()))
                        .collect(Collectors.toList());
                if (eligibleBowlers.isEmpty()) {
                    eligibleBowlers = bowlingSquad;
                }
                bowler = eligibleBowlers.get(random.nextInt(eligibleBowlers.size()));
            } else {
                // Same over, same bowler
                String bowlerName = lastBall.getBowler();
                bowler = bowlingSquad.stream()
                        .filter(p -> p.getPlayerName().equals(bowlerName))
                        .findFirst()
                        .orElse(bowlingSquad.get(8));
            }
        }

        // 3. Roll outcome
        // Probabilities based on role
        double wicketChance = striker.getRole().equals("BOWLER") ? 0.08 : 0.025;
        double extraChance = 0.03;
        double dotChance = striker.getRole().equals("BOWLER") ? 0.45 : 0.28;
        double singleChance = 0.40;
        double doubleChance = striker.getRole().equals("BOWLER") ? 0.04 : 0.12;
        double boundaryChance = striker.getRole().equals("BOWLER") ? 0.04 : 0.10;
        double sixChance = striker.getRole().equals("BOWLER") ? 0.01 : 0.045;

        double roll = random.nextDouble();
        int runsScored = 0;
        int extraRuns = 0;
        String wicketType = null;
        String dismissedBatter = null;

        if (roll < wicketChance) {
            // Wicket!
            double wRoll = random.nextDouble();
            if (wRoll < 0.60) {
                wicketType = "CAUGHT";
            } else if (wRoll < 0.80) {
                wicketType = "BOWLED";
            } else if (wRoll < 0.95) {
                wicketType = "LBW";
            } else {
                wicketType = "RUN_OUT";
            }
            dismissedBatter = striker.getPlayerName();
        } else if (roll < wicketChance + extraChance) {
            // Extra
            extraRuns = 1; // Wide or No Ball
        } else {
            // Runs
            double rRoll = random.nextDouble();
            double sum = dotChance + singleChance + doubleChance + boundaryChance + sixChance;
            double normRoll = rRoll * sum;

            if (normRoll < dotChance) {
                runsScored = 0;
            } else if (normRoll < dotChance + singleChance) {
                runsScored = 1;
            } else if (normRoll < dotChance + singleChance + doubleChance) {
                runsScored = 2;
            } else if (normRoll < dotChance + singleChance + doubleChance + boundaryChance) {
                runsScored = 4;
            } else {
                runsScored = 6;
            }
        }

        // 4. Update stats
        MatchBall ball = new MatchBall();
        ball.setMatchId(matchId);
        ball.setInnings(match.getCurrentInnings());
        ball.setOverNum(currentOverNum);
        ball.setBallNum(extraRuns > 0 ? battingScore.getBalls() : currentBallNum); // if extra, ball doesn't count
        ball.setBatter(striker.getPlayerName());
        ball.setBowler(bowler.getPlayerName());
        ball.setRunsScored(runsScored);
        ball.setExtraRuns(extraRuns);
        ball.setWicketType(wicketType);
        ball.setDismissedBatter(dismissedBatter);

        // Update score
        battingScore.setRuns(battingScore.getRuns() + runsScored + extraRuns);
        if (wicketType != null) {
            battingScore.setWickets(battingScore.getWickets() + 1);
        }

        if (extraRuns == 0) {
            // Legal ball
            battingScore.setBalls(currentBallNum);
            if (currentBallNum == 6) {
                battingScore.setOvers(currentOverNum + 1);
                battingScore.setBalls(0);
            }

            // Update Batter stats
            striker.setBallsFaced(striker.getBallsFaced() + 1);
            striker.setRunsScored(striker.getRunsScored() + runsScored);
            if (runsScored == 4) striker.setFours(striker.getFours() + 1);
            if (runsScored == 6) striker.setSixes(striker.getSixes() + 1);
            striker.setStrikeRate((striker.getRunsScored() * 100.0) / striker.getBallsFaced());

            // Update Bowler stats
            int bowlerBalls = getBallsFromOvers(bowler.getOversBowled()) + 1;
            bowler.setOversBowled((bowlerBalls / 6) + (bowlerBalls % 6) / 10.0);
            bowler.setRunsConceded(bowler.getRunsConceded() + runsScored);
            if (wicketType != null && !"RUN_OUT".equals(wicketType)) {
                bowler.setWicketsTaken(bowler.getWicketsTaken() + 1);
            }
            double bowlerOvers = (bowlerBalls / 6.0) + (bowlerBalls % 6) / 6.0;
            bowler.setEconomyRate(bowlerOvers > 0 ? (bowler.getRunsConceded() / bowlerOvers) : 0.0);
        } else {
            // Extra: bowler concedes run but doesn't get ball credited
            bowler.setRunsConceded(bowler.getRunsConceded() + extraRuns);
            double bowlerBalls = getBallsFromOvers(bowler.getOversBowled());
            double bowlerOvers = (bowlerBalls / 6.0);
            bowler.setEconomyRate(bowlerOvers > 0 ? (bowler.getRunsConceded() / bowlerOvers) : 0.0);
        }

        // Call Gemini Service for commentary
        String commentary = geminiService.generateCommentary(match, ball, Collections.emptyList(), allStats);
        ball.setCommentary(commentary);

        // Save
        matchBallRepository.save(ball);
        matchScoreRepository.save(battingScore);
        playerStatsRepository.save(striker);
        playerStatsRepository.save(bowler);

        // 5. Check Innings/Match Progression
        checkProgressions(match, battingScore, bowlingScore, allStats);
    }

    private int getBallsFromOvers(double overs) {
        int o = (int) overs;
        int b = (int) Math.round((overs - o) * 10);
        return o * 6 + b;
    }

    private void checkProgressions(MatchInfo match, MatchScore battingScore, MatchScore bowlingScore, List<PlayerStats> allStats) {
        boolean inningsEnded = false;

        // Innings 1 checks
        if (match.getCurrentInnings() == 1) {
            if (battingScore.getWickets() >= 10 || (battingScore.getOvers() == 20 && battingScore.getBalls() == 0)) {
                inningsEnded = true;
            }
        } else {
            // Innings 2 checks
            if (battingScore.getRuns() >= match.getTargetRuns()) {
                // Chasing team won!
                match.setStatus("COMPLETED");
                match.setWinner(battingScore.getTeam());
                match.setMargin((10 - battingScore.getWickets()) + " wickets");
                generatePostMatchSummary(match, bowlingScore, battingScore, allStats);
            } else if (battingScore.getWickets() >= 10 || (battingScore.getOvers() == 20 && battingScore.getBalls() == 0)) {
                // Defending team won or tie
                match.setStatus("COMPLETED");
                int diff = match.getTargetRuns() - 1 - battingScore.getRuns();
                if (diff == 0) {
                    match.setWinner("TIE");
                    match.setMargin("Scores level (Super Over required)");
                } else {
                    match.setWinner(bowlingScore.getTeam());
                    match.setMargin(diff + " runs");
                }
                generatePostMatchSummary(match, bowlingScore, battingScore, allStats);
            }
        }

        if (inningsEnded) {
            endInnings(match, battingScore, bowlingScore, allStats);
        } else {
            matchInfoRepository.save(match);
        }
    }

    private void endInnings(MatchInfo match, MatchScore battingScore, MatchScore bowlingScore, List<PlayerStats> allStats) {
        if (match.getCurrentInnings() == 1) {
            match.setCurrentInnings(2);
            match.setTargetRuns(battingScore.getRuns() + 1);
            
            // Switch batting team
            battingScore.setIsBatting(false);
            bowlingScore.setIsBatting(true);
            
            matchScoreRepository.save(battingScore);
            matchScoreRepository.save(bowlingScore);
            matchInfoRepository.save(match);
            System.out.println("Innings 1 complete! Target is " + match.getTargetRuns());
        }
    }

    private void generatePostMatchSummary(MatchInfo match, MatchScore score1, MatchScore score2, List<PlayerStats> stats) {
        String summary = geminiService.generateMatchSummary(match, score1, score2, stats);
        match.setAiSummary(summary);
        matchInfoRepository.save(match);
    }
}
