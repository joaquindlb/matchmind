
package com.matchmind.client;

import com.matchmind.client.FootballModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Talks to the football-data.org API. Every other class uses this one,
// so all the API details live in a single place.
@Component
public class FootballDataClient {

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

    // Every finished match of the current season in a competition
    public List<Match> getFinishedCompetitionMatches(String competitionCode) {
        CachedMatches cached = cache.get(competitionCode);
        if (cached != null && cached.fetchedAt().isAfter(Instant.now().minus(CACHE_TTL))) {
            return cached.matches();
        }
        MatchesResponse response = rest.get()
                .uri("/competitions/{code}/matches?status=FINISHED", competitionCode)
                .retrieve()
                .body(MatchesResponse.class);
        List<Match> matches = response == null ? List.of() : response.matches();
        cache.put(competitionCode, new CachedMatches(matches, Instant.now()));
        return matches;
    }
}

