package com.matchmind.prediction;

import com.matchmind.client.FootballDataClient;
import com.matchmind.client.FootballModels.Match;
import com.matchmind.prediction.PredictionModels.*;
import org.springframework.stereotype.Service;

import java.util.*;

/*
 * Poisson model for football scores.
 *
 * 1. From every finished match of the season we compute the league averages
 *    (goals by home teams, goals by away teams).
 * 2. Each team gets an attack strength (how much it scores vs. the average team)
 *    and a defense strength (how much it concedes vs. the average team).
 *    Early in the season teams have few matches, so each strength is pulled
 *    toward the league average as if the team had played PRIOR_GAMES average games.
 * 3. Expected goals:
 *      home = leagueHomeAvg * homeAttack * awayDefense
 *      away = leagueAwayAvg * awayAttack * homeDefense
 * 4. Goals follow a Poisson distribution, so P(score i-j) = P(home=i) * P(away=j).
 *    Adding up the grid gives win / draw / loss probabilities.
 */
@Service
public class PredictionService {

    private static final int MAX_GOALS = 7;
    private static final double PRIOR_GAMES = 3.0;

    private final FootballDataClient client;

    public PredictionService(FootballDataClient client) {
        this.client = client;
    }

    public Prediction predict(String competition, int homeId, int awayId) {
        if (homeId == awayId) {
            throw new IllegalArgumentException("Home and away team must be different");
        }

        List<Match> matches = client.getFinishedCompetitionMatches(competition).stream()
                .filter(PredictionService::hasFinalScore)
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No finished matches found for competition " + competition);
        }

        // Step 1: league totals and per-team stats
        Map<Integer, TeamStats> stats = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        double totalHomeGoals = 0;
        double totalAwayGoals = 0;

        for (Match m : matches) {
            int hg = m.score().fullTime().home();
            int ag = m.score().fullTime().away();
            totalHomeGoals += hg;
            totalAwayGoals += ag;
            stats.computeIfAbsent(m.homeTeam().id(), id -> new TeamStats()).add(hg, ag);
            stats.computeIfAbsent(m.awayTeam().id(), id -> new TeamStats()).add(ag, hg);
            names.put(m.homeTeam().id(), m.homeTeam().shortName());
            names.put(m.awayTeam().id(), m.awayTeam().shortName());
        }

        int n = matches.size();
        double leagueHomeAvg = totalHomeGoals / n;
        double leagueAwayAvg = totalAwayGoals / n;
        double goalsPerTeamPerGame = (totalHomeGoals + totalAwayGoals) / (2.0 * n);
        if (goalsPerTeamPerGame == 0) {
            throw new IllegalStateException("No goals recorded yet in " + competition);
        }

        // Step 2: strengths
        TeamStats home = stats.get(homeId);
        TeamStats away = stats.get(awayId);
        double homeAttack = strength(home, true, goalsPerTeamPerGame);
        double homeDefense = strength(home, false, goalsPerTeamPerGame);
        double awayAttack = strength(away, true, goalsPerTeamPerGame);
        double awayDefense = strength(away, false, goalsPerTeamPerGame);

        // Step 3: expected goals
        double lambdaHome = leagueHomeAvg * homeAttack * awayDefense;
        double lambdaAway = leagueAwayAvg * awayAttack * homeDefense;

        // Step 4: score grid
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

        // The grid stops at MAX_GOALS, so normalize to make the three outcomes add up to 100%
        double total = homeWin + draw + awayWin;
        List<ScoreProbability> top = scores.stream()
                .sorted(Comparator.comparingDouble(ScoreProbability::percent).reversed())
                .limit(5)
                .map(s -> new ScoreProbability(s.homeGoals(), s.awayGoals(), pct(s.percent() / total)))
                .toList();

        return new Prediction(
                competition,
                names.getOrDefault(homeId, "Team " + homeId),
                names.getOrDefault(awayId, "Team " + awayId),
                round2(lambdaHome),
                round2(lambdaAway),
                pct(homeWin / total),
                pct(draw / total),
                pct(awayWin / total),
                top,
                n,
                form(home),
                form(away),
                round2(goalsPerTeamPerGame));
    }

    private static TeamForm form(TeamStats s) {
        return s == null ? new TeamForm(0, 0, 0) : new TeamForm(s.games, s.goalsFor, s.goalsAgainst);
    }

    private static boolean hasFinalScore(Match m) {
        return m.score() != null && m.score().fullTime() != null
                && m.score().fullTime().home() != null && m.score().fullTime().away() != null;
    }

    // Goals per game relative to the league average, pulled toward 1.0 when data is scarce
    private static double strength(TeamStats s, boolean attack, double leagueAvg) {
        double games = s == null ? 0 : s.games;
        double goals = s == null ? 0 : (attack ? s.goalsFor : s.goalsAgainst);
        double smoothedPerGame = (goals + PRIOR_GAMES * leagueAvg) / (games + PRIOR_GAMES);
        return smoothedPerGame / leagueAvg;
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

    private static double pct(double fraction) {
        return Math.round(fraction * 1000) / 10.0;
    }

    private static double round2(double x) {
        return Math.round(x * 100) / 100.0;
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