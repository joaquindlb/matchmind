package com.matchmind.prediction;

import java.util.List;

// What the prediction endpoint returns
public final class PredictionModels {

    private PredictionModels() {}

    public record ScoreProbability(int homeGoals, int awayGoals, double percent) {}

    // A team's season so far: used by the AI analysis to explain the numbers
    public record TeamForm(int games, int goalsFor, int goalsAgainst) {}

    public record Prediction(
            String competition,
            String homeTeam,
            String awayTeam,
            double expectedHomeGoals,
            double expectedAwayGoals,
            double homeWinPercent,
            double drawPercent,
            double awayWinPercent,
            List<ScoreProbability> mostLikelyScores,
            int matchesAnalyzed,
            TeamForm homeForm,
            TeamForm awayForm,
            double leagueGoalsPerTeamPerGame) {}
}