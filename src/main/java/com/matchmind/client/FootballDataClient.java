package com.matchmind.client;

import com.matchmind.client.FootballModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Talks to the football-data.org API. Every other class uses this one,
// so all the API details live in a single place.
@Component
public class FootballDataClient {

    // The free tier allows 10 requests per minute, so match lists are cached for 10 minutes
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final RestClient rest;
    private final Map<String, CachedMatches> cache = new ConcurrentHashMap<>();

    private record CachedMatches(List<Match> matches, Instant fetchedAt) {}

    public FootballDataClient(RestClient.Builder builder,
                              @Value("${football.api.base-url}") String baseUrl,
                              @Value("${football.api.token}") String token) {
        this.rest = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", token)
                .build();
    }

    // All teams in a competition, e.g. "PL" (Premier League), "PD" (La Liga), "BSA" (Brazil)
    public List<Team> getTeams(String competitionCode) {
        TeamsResponse response = rest.get()
                .uri("/competitions/{code}/teams", competitionCode)
                .retrieve()
                .body(TeamsResponse.class);
        return response == null ? List.of() : response.teams();
    }

    // A team's most recent finished matches
    public List<Match> getFinishedMatches(int teamId, int limit) {
        MatchesResponse response = rest.get()
                .uri("/teams/{id}/matches?status=FINISHED&limit={limit}", teamId, limit)
                .retrieve()
                .body(MatchesResponse.class);
        return response == null ? List.of() : response.matches();
    }

    // Every finished match of the current season in a competition (one request, cached)
    public List<Match> getFinishedCompetitionMatches(String competitionCode) {
        return cached("finished:" + competitionCode, () -> {
            MatchesResponse response = rest.get()
                    .uri("/competitions/{code}/matches?status=FINISHED", competitionCode)
                    .retrieve()
                    .body(MatchesResponse.class);
            return response == null ? List.of() : response.matches();
        });
    }

    // Matches of a competition from today until daysAhead days from now (one request, cached)
    public List<Match> getUpcomingMatches(String competitionCode, int daysAhead) {
        return cached("upcoming:" + competitionCode, () -> {
            LocalDate from = LocalDate.now(ZoneOffset.UTC);
            LocalDate to = from.plusDays(daysAhead);
            MatchesResponse response = rest.get()
                    .uri("/competitions/{code}/matches?dateFrom={from}&dateTo={to}", competitionCode, from, to)
                    .retrieve()
                    .body(MatchesResponse.class);
            return response == null ? List.of() : response.matches();
        });
    }

    // Returns the cached list if it is fresh; otherwise calls the API and stores the result
    private List<Match> cached(String key, Supplier<List<Match>> loader) {
        CachedMatches entry = cache.get(key);
        if (entry != null && entry.fetchedAt().isAfter(Instant.now().minus(CACHE_TTL))) {
            return entry.matches();
        }
        List<Match> matches = loader.get();
        cache.put(key, new CachedMatches(matches, Instant.now()));
        return matches;
    }
}