package com.matchmind.client;

import java.util.List;

// Java records that mirror the parts of the football-data.org JSON we need.
public final class FootballModels {

    private FootballModels() {}

    public record TeamsResponse(List<Team> teams) {}
    public record Team(int id, String name, String shortName, String tla, String crest) {}

    public record MatchesResponse(List<Match> matches) {}
    public record Match(long id, String utcDate, String status,
                        TeamRef homeTeam, TeamRef awayTeam, Score score) {}
    public record TeamRef(int id, String name, String shortName) {}
    public record Score(String winner, Goals fullTime) {}
    public record Goals(Integer home, Integer away) {}
}