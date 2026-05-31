package com.ipl.assistant.service;

import com.ipl.assistant.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Call the Gemini API. If the API key is missing or the call fails, returns null.
     */
    private String callGemini(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("${GEMINI_API_KEY}")) {
            System.out.println("Gemini API key is not configured. Falling back to local AI engine.");
            return null;
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Construct body: {"contents": [{"parts": [{"text": prompt}]}]}
            Map<String, Object> part = Map.of("text", prompt);
            Map<String, Object> content = Map.of("parts", List.of(part));
            Map<String, Object> requestBody = Map.of("contents", List.of(content));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode textNode = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");
                if (!textNode.isMissingNode()) {
                    return textNode.asText().trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage() + ". Falling back to local AI engine.");
        }
        return null;
    }

    /**
     * Generate commentary for a single ball.
     */
    public String generateCommentary(MatchInfo matchInfo, MatchBall matchBall, List<MatchBall> lastFewBalls, List<PlayerStats> playerStats) {
        String eventDescription = getEventDescription(matchBall);

        // Build prompt for Gemini
        String prompt = String.format(
                "You are an energetic, expert Indian Premier League (IPL) cricket commentator (like Harsha Bhogle or Ravi Shastri). " +
                "Generate a short (2-3 sentences), dramatic, situational ball-by-ball commentary for the following event in a match between %s and %s.\n\n" +
                "Context:\n" +
                "- Innings: %d, Over: %d.%d\n" +
                "- Batter: %s\n" +
                "- Bowler: %s\n" +
                "- Event: %s\n" +
                "Provide only the commentary text. Keep it exciting and suited for live television broadcast.",
                matchInfo.getTeam1(), matchInfo.getTeam2(),
                matchBall.getInnings(), matchBall.getOverNum(), matchBall.getBallNum(),
                matchBall.getBatter(), matchBall.getBowler(), eventDescription
        );

        String response = callGemini(prompt);
        if (response != null) {
            return response;
        }

        // Fallback: Local rule-based commentary
        return generateLocalCommentary(matchBall);
    }

    /**
     * Generate pre-match preview.
     */
    public String generateMatchPreview(MatchInfo matchInfo) {
        String prompt = String.format(
                "Write a professional, exciting pre-match preview (about 150-200 words) for an IPL match between %s and %s. " +
                "Include a brief pitch report, 2 key player matchups to watch out for, and a predicted winner. " +
                "Format the response using markdown with nice headers.",
                matchInfo.getTeam1(), matchInfo.getTeam2()
        );

        String response = callGemini(prompt);
        if (response != null) {
            return response;
        }

        // Fallback
        return String.format(
                "### Match Preview: %s vs %s\n\n" +
                "Welcome to the high-octane clash between **%s** and **%s**! " +
                "Both teams look solid on paper, loaded with explosive match-winners.\n\n" +
                "#### Pitch Report\n" +
                "The pitch is expected to be a absolute batting paradise with a bit of assistance for the spinners in the middle overs. Win the toss and bat first seems to be the trend.\n\n" +
                "#### Key Player Matchups\n" +
                "1. **Opening Battle:** The explosive openers vs the swing bowlers in the Powerplay.\n" +
                "2. **Death Overs Mastery:** The finishing batters against the Yorker specialists in the final overs.\n\n" +
                "#### Prediction\n" +
                "It's a tough one to call, but **%s** has a slightly more balanced bowling attack which might give them the edge tonight!",
                matchInfo.getTeam1(), matchInfo.getTeam2(), matchInfo.getTeam1(), matchInfo.getTeam2(), matchInfo.getTeam1()
        );
    }

    /**
     * Generate post-match summary.
     */
    public String generateMatchSummary(MatchInfo matchInfo, MatchScore score1, MatchScore score2, List<PlayerStats> stats) {
        String statsSummary = stats.stream()
                .filter(s -> s.getRunsScored() > 10 || s.getWicketsTaken() > 0)
                .map(s -> String.format("- %s (%s): %d runs (%db, %dx4, %dx6), Bowling: %.1f-0-%d-%d",
                        s.getPlayerName(), s.getTeamName(), s.getRunsScored(), s.getBallsFaced(),
                        s.getFours(), s.getSixes(), s.getOversBowled(), s.getRunsConceded(), s.getWicketsTaken()))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "Write a comprehensive post-match report (about 150-250 words) for the IPL match between %s and %s.\n\n" +
                "Match Details:\n" +
                "- Winner: %s by %s\n" +
                "- %s Score: %d/%d in %d.%d overs\n" +
                "- %s Score: %d/%d in %d.%d overs\n" +
                "Player Performances:\n%s\n\n" +
                "Summarize the key turning points, top batting and bowling performances, and why the winner succeeded. Format in markdown.",
                matchInfo.getTeam1(), matchInfo.getTeam2(),
                matchInfo.getWinner(), matchInfo.getMargin(),
                score1.getTeam(), score1.getRuns(), score1.getWickets(), score1.getOvers(), score1.getBalls(),
                score2.getTeam(), score2.getRuns(), score2.getWickets(), score2.getOvers(), score2.getBalls(),
                statsSummary
        );

        String response = callGemini(prompt);
        if (response != null) {
            return response;
        }

        // Fallback
        return String.format(
                "### Match Summary: %s wins by %s!\n\n" +
                "What an absolute thriller of a cricket match! **%s** emerged victorious over **%s** in a clash that kept fans on the edge of their seats.\n\n" +
                "#### Key Performances\n" +
                "- **Top Batting:** The top-order set a brilliant foundation with aggressive strokeplay.\n" +
                "- **Top Bowling:** Key spells in the middle overs restricted the opposition and broke crucial partnerships.\n\n" +
                "#### Turning Point\n" +
                "The match turned during the death overs, where tight bowling and excellent fielding built pressure, leading to crucial wickets that sealed the victory for **%s**.",
                matchInfo.getWinner(), matchInfo.getMargin(), matchInfo.getWinner(),
                matchInfo.getWinner().equals(matchInfo.getTeam1()) ? matchInfo.getTeam2() : matchInfo.getTeam1(),
                matchInfo.getWinner()
        );
    }

    /**
     * Chat assistant to answer user questions about the live match.
     */
    public String answerUserQuery(MatchInfo matchInfo, List<MatchScore> scores, List<PlayerStats> stats, List<MatchBall> commentary, String query) {
        String scoreSummary = scores.stream()
                .map(s -> String.format("%s: %d/%d (%d.%d overs)", s.getTeam(), s.getRuns(), s.getWickets(), s.getOvers(), s.getBalls()))
                .collect(Collectors.joining(", "));

        String topBatters = stats.stream()
                .filter(s -> s.getRunsScored() > 0)
                .sorted(Comparator.comparing(PlayerStats::getRunsScored).reversed())
                .limit(3)
                .map(s -> String.format("%s (%d runs off %db)", s.getPlayerName(), s.getRunsScored(), s.getBallsFaced()))
                .collect(Collectors.joining(", "));

        String topBowlers = stats.stream()
                .filter(s -> s.getWicketsTaken() > 0)
                .sorted(Comparator.comparing(PlayerStats::getWicketsTaken).reversed())
                .limit(3)
                .map(s -> String.format("%s (%d wkts, econ %.1f)", s.getPlayerName(), s.getWicketsTaken(), s.getEconomyRate()))
                .collect(Collectors.joining(", "));

        String recentCommentary = commentary.stream()
                .limit(5)
                .map(b -> String.format("Over %d.%d: %s to %s -> %s (%s)", b.getOverNum(), b.getBallNum(), b.getBowler(), b.getBatter(), getEventDescription(b), b.getCommentary()))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "You are an intelligent, friendly IPL Live Match AI Assistant. Answer the user's question about the current live cricket match.\n\n" +
                "Current Match Context:\n" +
                "- Teams: %s vs %s (Status: %s)\n" +
                "- Current Scores: %s\n" +
                "- Target: %d runs (if 2nd innings)\n" +
                "- Top Batters: %s\n" +
                "- Top Bowlers: %s\n" +
                "- Recent Commentary:\n%s\n\n" +
                "User Question: \"%s\"\n\n" +
                "Provide a helpful, concise answer based on this context. Feel free to use markdown and add insights (win probability, player form, etc.).",
                matchInfo.getTeam1(), matchInfo.getTeam2(), matchInfo.getStatus(),
                scoreSummary, matchInfo.getTargetRuns() != null ? matchInfo.getTargetRuns() : 0,
                topBatters, topBowlers, recentCommentary, query
        );

        String response = callGemini(prompt);
        if (response != null) {
            return response;
        }

        // Fallback: Local rule-based search
        return generateLocalChatAnswer(query, matchInfo, scores, stats, commentary);
    }

    private String getEventDescription(MatchBall ball) {
        if (ball.getWicketType() != null && !ball.getWicketType().isEmpty()) {
            return "WICKET! (" + ball.getWicketType() + " - " + ball.getDismissedBatter() + " dismissed)";
        }
        int runs = ball.getRunsScored();
        int extras = ball.getExtraRuns();
        if (extras > 0) {
            return runs + " runs + " + extras + " extras";
        }
        if (runs == 4) return "FOUR runs! Boundary!";
        if (runs == 6) return "SIX runs! Maximum!";
        if (runs == 0) return "Dot ball";
        return runs + " run" + (runs > 1 ? "s" : "");
    }

    private String generateLocalCommentary(MatchBall ball) {
        String batter = ball.getBatter();
        String bowler = ball.getBowler();
        int runs = ball.getRunsScored();
        String wicket = ball.getWicketType();

        String[] templates;
        if (wicket != null && !wicket.isEmpty()) {
            templates = new String[]{
                    "OUT! What a massive moment in this match! %s has clean bowled %s! The stumps are in disarray and the bowling team is ecstatic!",
                    "GONE! %s tries to clear the boundary but finds the fielder! A simple catch taken, and %s has to walk back. Big wicket for %s!",
                    "WICKET! An appeal for LBW and the finger goes up! %s strikes! %s is trapped plumb in front of the stumps.",
                    "RUN OUT! Sensational work in the field! A direct hit and %s is short of the crease. Brilliant teamwork!"
            };
            int idx = Math.abs(ball.hashCode()) % templates.length;
            if (wicket.equalsIgnoreCase("BOWLED")) return String.format(templates[0], bowler, batter);
            if (wicket.equalsIgnoreCase("CAUGHT")) return String.format(templates[1], batter, bowler, bowler);
            if (wicket.equalsIgnoreCase("LBW")) return String.format(templates[2], bowler, batter);
            return String.format(templates[3], batter);
        }

        if (ball.getExtraRuns() > 0) {
            return String.format("Wide ball! %s fires it down the leg side. The keeper collects, but it's an extra run to the batting team.", bowler);
        }

        if (runs == 6) {
            templates = new String[]{
                    "BOOM! That is massive! %s launches %s over deep mid-wicket for a colossal six! Incredible power!",
                    "SIX! Struck beautifully! %s takes a step forward and lofts it cleanly over long-off. The ball is lost in the stands!",
                    "MAXIMUM! A sensational shot from %s, picking up the length early and dispatching it over square leg."
            };
            return String.format(templates[Math.abs(ball.hashCode()) % templates.length], batter, bowler);
        }

        if (runs == 4) {
            templates = new String[]{
                    "FOUR! Lovely timing! %s finds the gap in the covers and the ball races away to the boundary.",
                    "FOUR MORE! %s uses the pace of %s and guides it delicately past third man. Outstanding batting!",
                    "CRACK! Short delivery from %s and %s pulls it authoritatively through mid-wicket for a four."
            };
            return String.format(templates[Math.abs(ball.hashCode()) % templates.length], batter, bowler);
        }

        if (runs == 0) {
            templates = new String[]{
                    "No run. Good line and length from %s, defending it solidly.",
                    "Dot ball. %s beats the bat as %s attempts a big drive outside off-stump.",
                    "A well-directed delivery by %s, played back to the bowler. No run."
            };
            return String.format(templates[Math.abs(ball.hashCode()) % templates.length], bowler, batter);
        }

        templates = new String[]{
                "Direct hit/single. Driven to long-on for a quick single by %s.",
                "%s pushes it into the gap at cover and runs hard for two. Excellent running between the wickets!",
                "Flicked off the pads to deep square leg by %s. Just a single."
        };
        return String.format(templates[Math.abs(ball.hashCode()) % templates.length], batter);
    }

    private String generateLocalChatAnswer(String query, MatchInfo matchInfo, List<MatchScore> scores, List<PlayerStats> stats, List<MatchBall> commentary) {
        String q = query.toLowerCase();

        // 1. Who is winning / win prediction
        if (q.contains("win") || q.contains("predict") || q.contains("chance")) {
            if (matchInfo.getStatus().equalsIgnoreCase("COMPLETED")) {
                return "The match has already concluded! **" + matchInfo.getWinner() + "** won the match by " + matchInfo.getMargin() + ".";
            }
            if (matchInfo.getStatus().equalsIgnoreCase("UPCOMING")) {
                return "The match hasn't started yet. Based on recent form, it's expected to be a tight contest, but **" + matchInfo.getTeam1() + "** might have a slight 55% edge.";
            }
            // In progress
            if (scores.size() >= 2) {
                MatchScore activeScore = scores.stream().filter(MatchScore::getIsBatting).findFirst().orElse(scores.get(0));
                MatchScore otherScore = scores.stream().filter(s -> !s.getIsBatting()).findFirst().orElse(scores.get(0));
                if (matchInfo.getCurrentInnings() == 2) {
                    double target = matchInfo.getTargetRuns();
                    double runsNeeded = target - activeScore.getRuns();
                    int ballsRemaining = 120 - (activeScore.getOvers() * 6 + activeScore.getBalls());
                    if (ballsRemaining > 0) {
                        double reqRate = (runsNeeded * 6.0) / ballsRemaining;
                        double curRate = (activeScore.getRuns() * 6.0) / (activeScore.getOvers() * 6 + activeScore.getBalls());
                        String prediction = reqRate < 8.5 ? "The chasing team (" + activeScore.getTeam() + ") is in a very strong position." : "The required run rate is " + String.format("%.2f", reqRate) + ", making it a tough chase for " + activeScore.getTeam() + ".";
                        return String.format("### Live Win Probability & Chase Analysis\n\n" +
                                        "**%s** needs **%d** runs off **%d** balls. \n" +
                                        "- **Current Run Rate:** %.2f\n" +
                                        "- **Required Run Rate:** %.2f\n\n" +
                                        "%s Win probability: **%s** 55%% vs **%s** 45%%.",
                                activeScore.getTeam(), (int)runsNeeded, ballsRemaining, curRate, reqRate,
                                prediction, activeScore.getTeam(), otherScore.getTeam());
                    }
                }
            }
            return "Based on the current match progression, **" + matchInfo.getTeam1() + "** is leading the charge with a 60% win probability due to solid batting partnerships.";
        }

        // 2. Query about scores / scoreboard
        if (q.contains("score") || q.contains("runs") || q.contains("wicket")) {
            StringBuilder sb = new StringBuilder("### Current Scorecard Summary\n\n");
            for (MatchScore s : scores) {
                sb.append(String.format("- **%s:** %d/%d (%d.%d overs)%s\n",
                        s.getTeam(), s.getRuns(), s.getWickets(), s.getOvers(), s.getBalls(),
                        s.getIsBatting() ? " *batting*" : ""));
            }
            if (matchInfo.getTargetRuns() != null && matchInfo.getTargetRuns() > 0) {
                sb.append("\n**Target:** ").append(matchInfo.getTargetRuns()).append(" runs");
            }
            return sb.toString();
        }

        // 3. Query about specific players
        for (PlayerStats s : stats) {
            if (q.contains(s.getPlayerName().toLowerCase())) {
                return String.format("### Player Stats: %s (%s)\n\n" +
                                "- **Role:** %s\n" +
                                "- **Batting:** %d runs off %d balls (%d fours, %d sixes, SR: %.1f)\n" +
                                "- **Bowling:** %.1f overs, %d runs conceded, %d wickets (Econ: %.1f)",
                        s.getPlayerName(), s.getTeamName(), s.getRole(),
                        s.getRunsScored(), s.getBallsFaced(), s.getFours(), s.getSixes(), s.getStrikeRate(),
                        s.getOversBowled(), s.getRunsConceded(), s.getWicketsTaken(), s.getEconomyRate());
            }
        }

        // 4. Query about best batter / top batter
        if (q.contains("batter") || q.contains("batsman") || q.contains("batting")) {
            PlayerStats top = stats.stream()
                    .filter(s -> s.getRunsScored() > 0)
                    .max(Comparator.comparing(PlayerStats::getRunsScored))
                    .orElse(null);
            if (top != null) {
                return String.format("The top performer with the bat is **%s** of **%s**, who has scored **%d** runs off **%d** balls with %d fours and %d sixes (Strike Rate: %.1f).",
                        top.getPlayerName(), top.getTeamName(), top.getRunsScored(), top.getBallsFaced(), top.getFours(), top.getSixes(), top.getStrikeRate());
            }
            return "No runs have been scored in the match yet.";
        }

        // 5. Query about bowler / top bowler
        if (q.contains("bowler") || q.contains("bowling")) {
            PlayerStats top = stats.stream()
                    .filter(s -> s.getWicketsTaken() > 0)
                    .max(Comparator.comparing(PlayerStats::getWicketsTaken))
                    .orElse(null);
            if (top != null) {
                return String.format("The standout bowler is **%s** of **%s**, with figures of **%d wickets** from **%.1f overs**, conceding **%d runs** (Economy Rate: %.1f).",
                        top.getPlayerName(), top.getTeamName(), top.getWicketsTaken(), top.getOversBowled(), top.getRunsConceded(), top.getEconomyRate());
            }
            return "No wickets have been taken in the match yet.";
        }

        // Default response
        return "Hi there! I can help you analyze the live IPL match. Ask me things like:\n" +
                "- *\"Who is winning?\"*\n" +
                "- *\"Show me the live score\"*\n" +
                "- *\"How is Virat Kohli playing?\"*\n" +
                "- *\"Who is the top bowler?\"*";
    }
}
