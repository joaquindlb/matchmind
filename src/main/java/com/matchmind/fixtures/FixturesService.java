package com.matchmind.fixtures;

import com.matchmind.client.FootballDataClient;
import com.matchmind.client.FootballModels.Match;
import com.matchmind.prediction.PoissonModel;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Upcoming matches of a league, each with a quick forecast.
// The Poisson model is trained once on the season and reused for every fixture.
@Service
public class FixturesService {

    private static final int DAYS_AHEAD = 7;
    private static final int MAX_FIXTURES = 12;
    private static final Set<String> NOT_STARTED = Set.of("SCHEDULED", "TIMED");

    public record FixtureForecast(long matchId, String kickoff,
                                  int homeId, String homeTeam,
                                  int awayId, String awayTeam,
                                  Double homeWinPercent, Double drawPercent, Double awayWinPercent) {}

    private final FootballDataClient client;

    public FixturesService(FootballDataClient client) {
        this.client = client;
    }

    public List<FixtureForecast> upcoming(String competition) {
        List<Match> fixtures = client.getUpcomingMatches(competition, DAYS_AHEAD).stream()
                .filter(m -> NOT_STARTED.contains(m.status()))
                .filter(m -> m.homeTeam() != null && m.awayTeam() != null)
                .sorted(Comparator.comparing(Match::utcDate))
                .limit(MAX_FIXTURES)
                .toList();
        if (fixtures.isEmpty()) {
            return List.of();
        }

        // If the season has no finished matches yet, fixtures are still listed without a forecast
        PoissonModel model = null;
        try {
            model = PoissonModel.fit(client.getFinishedCompetitionMatches(competition));
        } catch (IllegalArgumentException e) {

        }

        final PoissonModel trained = model;
        return fixtures.stream().map(m -> {
            int homeId = m.homeTeam().id();
            int awayId = m.awayTeam().id();
            Double home = null, draw = null, away = null;
            if (trained != null) {
                PoissonModel.Forecast f = trained.forecast(homeId, awayId);
                home = pct(f.homeWin());
                draw = pct(f.draw());
                away = pct(f.awayWin());
            }
            return new FixtureForecast(m.id(), m.utcDate(),
                    homeId, m.homeTeam().shortName(),
                    awayId, m.awayTeam().shortName(),
                    home, draw, away);
        }).toList();
    }

    private static double pct(double fraction) {
        return Math.round(fraction * 1000) / 10.0;
    }
}