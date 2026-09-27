package com.matchmind.backtest;

import com.matchmind.client.FootballDataClient;
import com.matchmind.client.FootballModels.Match;
import com.matchmind.prediction.PoissonModel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Backtesting: how good would MatchMind have been this season?
 *
 * For every finished match, the model is trained ONLY on matches that were
 * played before it, so it never "sees the answer". The predicted outcome
 * (the most likely of home win, draw or away win) is then compared with the
 * real result.
 *
 * As a reference, it also measures a naive strategy: always pick the home team.
 * A useful model should beat it.
 */
@Service
public class BacktestService {

    // The first matches of the season are skipped: the model needs some history first
    private static final int MIN_HISTORY = 20;
    private static final double CONFIDENT = 0.55;
    private static final int RECENT_TO_SHOW = 10;

    public record BacktestMatch(String date, String homeTeam, String awayTeam, String score,
                                String predicted, double predictedPercent, String actual, boolean correct) {}

    public record BacktestResult(String competition,
                                 int matchesTested,
                                 int correct,
                                 double accuracyPercent,
                                 double homeTeamBaselinePercent,
                                 int confidentTested,
                                 double confidentAccuracyPercent,
                                 double avgProbabilityOfActualPercent,
                                 List<BacktestMatch> recent) {}

    private record Timed(Match match, Instant kickoff) {}

    private enum Outcome { HOME, DRAW, AWAY }

    private final FootballDataClient client;

    public BacktestService(FootballDataClient client) {
        this.client = client;
    }

    public BacktestResult run(String competition) {
        List<Timed> season = client.getFinishedCompetitionMatches(competition).stream()
                .filter(PoissonModel::hasFinalScore)
                .map(m -> new Timed(m, Instant.parse(m.utcDate())))
                .sorted(Comparator.comparing(Timed::kickoff))
                .toList();

        int tested = 0, correct = 0, homeWins = 0, confidentTested = 0, confidentCorrect = 0;
        double sumProbActual = 0;
        List<BacktestMatch> details = new ArrayList<>();

        for (Timed t : season) {
            // Only matches that kicked off before this one: no peeking at the future
            List<Match> history = season.stream()
                    .filter(h -> h.kickoff().isBefore(t.kickoff()))
                    .map(Timed::match)
                    .toList();
            if (history.size() < MIN_HISTORY) continue;

            PoissonModel model;
            try {
                model = PoissonModel.fit(history);
            } catch (IllegalArgumentException e) {
                continue;
            }

            Match m = t.match();
            PoissonModel.Forecast f = model.forecast(m.homeTeam().id(), m.awayTeam().id());

            Outcome predicted = Outcome.HOME;
            double predictedProb = f.homeWin();
            if (f.draw() > predictedProb) { predicted = Outcome.DRAW; predictedProb = f.draw(); }
            if (f.awayWin() > predictedProb) { predicted = Outcome.AWAY; predictedProb = f.awayWin(); }

            int hg = m.score().fullTime().home();
            int ag = m.score().fullTime().away();
            Outcome actual = hg > ag ? Outcome.HOME : hg == ag ? Outcome.DRAW : Outcome.AWAY;

            boolean hit = predicted == actual;
            tested++;
            if (hit) correct++;
            if (actual == Outcome.HOME) homeWins++;
            if (predictedProb >= CONFIDENT) {
                confidentTested++;
                if (hit) confidentCorrect++;
            }
            sumProbActual += switch (actual) {
                case HOME -> f.homeWin();
                case DRAW -> f.draw();
                case AWAY -> f.awayWin();
            };

            String home = m.homeTeam().shortName();
            String away = m.awayTeam().shortName();
            details.add(new BacktestMatch(
                    m.utcDate().substring(0, 10), home, away, hg + "-" + ag,
                    label(predicted, home, away), pct(predictedProb),
                    label(actual, home, away), hit));
        }

        if (tested == 0) {
            throw new IllegalArgumentException(
                    "Not enough finished matches yet to test the model in " + competition);
        }

        List<BacktestMatch> recent = details.reversed().stream().limit(RECENT_TO_SHOW).toList();

        return new BacktestResult(
                competition,
                tested,
                correct,
                pct((double) correct / tested),
                pct((double) homeWins / tested),
                confidentTested,
                confidentTested == 0 ? 0 : pct((double) confidentCorrect / confidentTested),
                pct(sumProbActual / tested),
                recent);
    }

    private static String label(Outcome o, String home, String away) {
        return switch (o) {
            case HOME -> home;
            case DRAW -> "Draw";
            case AWAY -> away;
        };
    }

    private static double pct(double fraction) {
        return Math.round(fraction * 1000) / 10.0;
    }
}