package com.ipl.assistant.controller;

import com.ipl.assistant.model.*;
import com.ipl.assistant.service.GeminiService;
import com.ipl.assistant.service.MatchService;
import com.ipl.assistant.service.MatchSimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchSimulationService matchSimulationService;

    @Autowired
    private GeminiService geminiService;

    @GetMapping
    public ResponseEntity<List<MatchInfo>> getAllMatches() {
        return ResponseEntity.ok(matchService.getAllMatches());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMatchDetails(@PathVariable Long id) {
        MatchInfo matchInfo = matchService.getMatchInfo(id).orElse(null);
        if (matchInfo == null) {
            return ResponseEntity.notFound().build();
        }

        List<MatchScore> scores = matchService.getMatchScores(id);
        Map<String, Object> response = new HashMap<>();
        response.put("matchInfo", matchInfo);
        response.put("scores", scores);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/commentary")
    public ResponseEntity<List<MatchBall>> getCommentary(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getMatchCommentary(id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<List<PlayerStats>> getPlayerStats(@PathVariable Long id) {
        return ResponseEntity.ok(matchService.getPlayerStats(id));
    }

    @PostMapping("/simulate")
    public ResponseEntity<MatchInfo> simulateMatch(@RequestBody Map<String, String> request) {
        String team1 = request.get("team1");
        String team2 = request.get("team2");
        if (team1 == null || team2 == null) {
            return ResponseEntity.badRequest().build();
        }
        MatchInfo match = matchService.createMatch(team1, team2);
        return ResponseEntity.ok(match);
    }

    @PostMapping("/{id}/next-ball")
    public ResponseEntity<Void> triggerNextBall(@PathVariable Long id) {
        matchSimulationService.simulateNextBall(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reset")
    public ResponseEntity<Void> resetMatch(@PathVariable Long id) {
        matchService.resetMatch(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-all")
    public ResponseEntity<Void> resetAll() {
        matchService.resetAll();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<Map<String, String>> chatAboutMatch(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        MatchInfo matchInfo = matchService.getMatchInfo(id).orElse(null);
        if (matchInfo == null) {
            return ResponseEntity.notFound().build();
        }

        List<MatchScore> scores = matchService.getMatchScores(id);
        List<PlayerStats> stats = matchService.getPlayerStats(id);
        List<MatchBall> commentary = matchService.getMatchCommentary(id);

        String answer = geminiService.answerUserQuery(matchInfo, scores, stats, commentary, userMessage);
        
        Map<String, String> response = new HashMap<>();
        response.put("reply", answer);
        return ResponseEntity.ok(response);
    }
}
