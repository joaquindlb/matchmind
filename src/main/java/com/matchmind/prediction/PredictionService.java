package com.matchmind.prediction;

import com.matchmind.client.FootballDataClient;
import com.matchmind.client.FootballModels.Match;
import com.matchmind.prediction.PredictionModels.*;
import org.springframework.stereotype.Service;

import java.util.List;

// Live predictions: trains the Poisson model on every finished match of the season.
// The math itself lives in PoissonModel.
@Service
public class PredictionService {

    private final FootballDataClient client;

    public PredictionService(FootballDataClient client) {
        this.client = client;
    }

    public Prediction predict(String competition, int homeId, int awayId) {
        if (homeId == awayId) {
            throw new IllegalArgumentException("Home and away team must be different");
        }

        List<Match> matches = client.getFinishedCompetitionMatches(competition).stream()
                .filter(PoissonModel::hasFinalScore)
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No finished matches found for competition " + competition);
        }

        PoissonModel model = PoissonModel.fit(matches);
        PoissonModel.Forecast f = model.forecast(homeId, awayId);

        List<ScoreProbability> top = f.mostLikelyScores().stream()
                .map(s -> new ScoreProbability(s.homeGoals(), s.awayGoals(), pct(s.percent())))
                .toList();

        return new Prediction(
                competition,
                model.name(homeId),
                model.name(awayId),
                round2(f.expectedHomeGoals()),
                round2(f.expectedAwayGoals()),
                pct(f.homeWin()),
                pct(f.draw()),
                pct(f.awayWin()),
                top,
                model.matchCount(),
                model.form(homeId),
                model.form(awayId),
                round2(model.goalsPerTeamPerGame()));
    }

    static double pct(double fraction) {
        return Math.round(fraction * 1000) / 10.0;
    }

    static double round2(double x) {
        return Math.round(x * 100) / 100.0;
    }
}