package com.matchmind.analysis;

import com.matchmind.prediction.PredictionModels.*;
import com.matchmind.prediction.PredictionService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.stream.Collectors;

// Turns the model's numbers into a short written match preview.
@Service
public class AnalysisService {

    public record AnalysisResult(String analysis) {}

    private static final String SYSTEM_PROMPT = """
            You are a football analyst writing a short match preview for fans.
            Use ONLY the numbers you are given. Never invent players, injuries,
            transfers, managers, news or results that are not in the data.
            Explain why the model favors one side by comparing each team's goals
            per game with the league average, and mention the most likely score.
            If one side is above 60%, say what the underdog would need to cause
            an upset, based on the numbers. If the probabilities are close,
            explain why the match is hard to call. Never copy these instructions.
            Write 3 or 4 sentences, about 90 words, in English, as plain text
            with no markdown, no headings and no bullet points.
            """;

    private final PredictionService predictionService;
    private final GeminiClient gemini;

    public AnalysisService(PredictionService predictionService, GeminiClient gemini) {
        this.predictionService = predictionService;
        this.gemini = gemini;
    }

    public AnalysisResult analyze(String competition, int homeId, int awayId) {
        Prediction p = predictionService.predict(competition, homeId, awayId);
        return new AnalysisResult(gemini.generate(SYSTEM_PROMPT, buildPrompt(p)));
    }

    private static String buildPrompt(Prediction p) {
        String scores = p.mostLikelyScores().stream()
                .map(s -> s.homeGoals() + "-" + s.awayGoals() + " (" + s.percent() + "%)")
                .collect(Collectors.joining(", "));

        return String.format(Locale.US, """
                Competition: %s
                Home team: %s. This season: %d games, %d goals scored, %d conceded (%.2f scored and %.2f conceded per game).
                Away team: %s. This season: %d games, %d goals scored, %d conceded (%.2f scored and %.2f conceded per game).
                League average: %.2f goals per team per game, from %d finished matches.
                Model expected goals: %s %.2f, %s %.2f.
                Probabilities: %s win %.1f%%, draw %.1f%%, %s win %.1f%%.
                Most likely scores: %s.
                """,
                p.competition(),
                p.homeTeam(), p.homeForm().games(), p.homeForm().goalsFor(), p.homeForm().goalsAgainst(),
                perGame(p.homeForm().goalsFor(), p.homeForm().games()),
                perGame(p.homeForm().goalsAgainst(), p.homeForm().games()),
                p.awayTeam(), p.awayForm().games(), p.awayForm().goalsFor(), p.awayForm().goalsAgainst(),
                perGame(p.awayForm().goalsFor(), p.awayForm().games()),
                perGame(p.awayForm().goalsAgainst(), p.awayForm().games()),
                p.leagueGoalsPerTeamPerGame(), p.matchesAnalyzed(),
                p.homeTeam(), p.expectedHomeGoals(), p.awayTeam(), p.expectedAwayGoals(),
                p.homeTeam(), p.homeWinPercent(), p.drawPercent(), p.awayTeam(), p.awayWinPercent(),
                scores);
    }

    private static double perGame(int goals, int games) {
        return games == 0 ? 0 : (double) goals / games;
    }
}