package com.matchmind.prediction;

import com.matchmind.client.FootballModels.Match;
import com.matchmind.prediction.PredictionModels.ScoreProbability;
import com.matchmind.prediction.PredictionModels.TeamForm;

import java.util.*;

/*
 * The Poisson model on its own, independent of where the matches come from.
 *
 * fit(matches) learns league averages and each team's attack/defense strength
 * from a list of finished matches. forecast(home, away) then returns the
 * probabilities for one match.
 *
 * Keeping it separate lets the same model be used for live predictions
 * (trained on the whole season) and for backtesting (trained only on the
 * matches played before the one being predicted).
 */
public final class PoissonModel {

    private static final int MAX_GOALS = 7;
    private static final double PRIOR_GAMES = 3.0;

    public record Forecast(double expectedHomeGoals, double expectedAwayGoals,
                           double homeWin, double draw, double awayWin,
                           List<ScoreProbability> mostLikelyScores) {}

    private final Map<Integer, TeamStats> stats;
    private final Map<Integer, String> names;
    private final double leagueHomeAvg;
    private final double leagueAwayAvg;
    private final double goalsPerTeamPerGame;
    private final int matchCount;

    private PoissonModel(Map<Integer, TeamStats> stats, Map<Integer, String> names,
                         double leagueHomeAvg, double leagueAwayAvg,
                         double goalsPerTeamPerGame, int matchCount) {
        this.stats = stats;
        this.names = names;
        this.leagueHomeAvg = leagueHomeAvg;
        this.leagueAwayAvg = leagueAwayAvg;
        this.goalsPerTeamPerGame = goalsPerTeamPerGame;
        this.matchCount = matchCount;
    }

    public static boolean hasFinalScore(Match m) {
        return m.score() != null && m.score().fullTime() != null
                && m.score().fullTime().home() != null && m.score().fullTime().away() != null;
    }

    // Learns the league averages and team strengths from finished matches
    public static PoissonModel fit(List<Match> matches) {
        Map<Integer, TeamStats> stats = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        double totalHome = 0;
        double totalAway = 0;
        int n = 0;

        for (Match m : matches) {
            if (!hasFinalScore(m)) continue;
            int hg = m.score().fullTime().home();
            int ag = m.score().fullTime().away();
            totalHome += hg;
            totalAway += ag;
            n++;
            stats.computeIfAbsent(m.homeTeam().id(), id -> new TeamStats()).add(hg, ag);
            stats.computeIfAbsent(m.awayTeam().id(), id -> new TeamStats()).add(ag, hg);
            names.put(m.homeTeam().id(), m.homeTeam().shortName());
            names.put(m.awayTeam().id(), m.awayTeam().shortName());
        }

        if (n == 0) {
            throw new IllegalArgumentException("No finished matches to learn from");
        }
        double perTeam = (totalHome + totalAway) / (2.0 * n);
        if (perTeam == 0) {
            throw new IllegalArgumentException("No goals recorded yet");
        }
        return new PoissonModel(stats, names, totalHome / n, totalAway / n, perTeam, n);
    }

    public Forecast forecast(int homeId, int awayId) {
        TeamStats home = stats.get(homeId);
        TeamStats away = stats.get(awayId);

        double lambdaHome = leagueHomeAvg * strength(home, true) * strength(away, false);
        double lambdaAway = leagueAwayAvg * strength(away, true) * strength(home, false);

        double[] pHome = poisson(lambdaHome);
        double[] pAway = poisson(lambdaAway);
        double homeWin = 0, draw = 0, awayWin = 0;
        List<ScoreProbability> scores = new ArrayList<>();

        for (int i = 0; i <= MAX_GOALS; i++) {
            for (int j = 0; j <= MAX_GOALS; j++) {
                double p = pHome[i] * pAway[j];
                if (i > j) homeWin += p;
                else if (i == j) draw += p;
                else awayWin += p;
                scores.add(new ScoreProbability(i, j, p));
            }
        }

        // The grid stops at MAX_GOALS, so normalize to make the outcomes add up to 1
        double total = homeWin + draw + awayWin;
        List<ScoreProbability> top = scores.stream()
                .sorted(Comparator.comparingDouble(ScoreProbability::percent).reversed())
                .limit(5)
                .map(s -> new ScoreProbability(s.homeGoals(), s.awayGoals(), s.percent() / total))
                .toList();

        return new Forecast(lambdaHome, lambdaAway, homeWin / total, draw / total, awayWin / total, top);
    }

    public TeamForm form(int teamId) {
        TeamStats s = stats.get(teamId);
        return s == null ? new TeamForm(0, 0, 0) : new TeamForm(s.games, s.goalsFor, s.goalsAgainst);
    }

    public String name(int teamId) {
        return names.getOrDefault(teamId, "Team " + teamId);
    }

    public int matchCount() {
        return matchCount;
    }

    public double goalsPerTeamPerGame() {
        return goalsPerTeamPerGame;
    }

    // Goals per game relative to the league average, pulled toward 1.0 when data is scarce
    private double strength(TeamStats s, boolean attack) {
        double games = s == null ? 0 : s.games;
        double goals = s == null ? 0 : (attack ? s.goalsFor : s.goalsAgainst);
        double smoothedPerGame = (goals + PRIOR_GAMES * goalsPerTeamPerGame) / (games + PRIOR_GAMES);
        return smoothedPerGame / goalsPerTeamPerGame;
    }

    // P(X = k) for k = 0..MAX_GOALS, computed iteratively: P(k) = P(k-1) * lambda / k
    private static double[] poisson(double lambda) {
        double[] p = new double[MAX_GOALS + 1];
        p[0] = Math.exp(-lambda);
        for (int k = 1; k <= MAX_GOALS; k++) {
            p[k] = p[k - 1] * lambda / k;
        }
        return p;
    }

    private static class TeamStats {
        int games;
        int goalsFor;
        int goalsAgainst;

        void add(int scored, int conceded) {
            games++;
            goalsFor += scored;
            goalsAgainst += conceded;
        }
    }
}