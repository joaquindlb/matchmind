package com.matchmind.controller;

import com.matchmind.client.FootballDataClient;
import com.matchmind.client.FootballModels.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Temporary endpoints to check that the API connection works.
@RestController
@RequestMapping("/api")
public class TestController {

    private final FootballDataClient client;

    public TestController(FootballDataClient client) {
        this.client = client;
    }

    @GetMapping("/competitions/{code}/teams")
    public List<Team> teams(@PathVariable String code) {
        return client.getTeams(code);
    }

    @GetMapping("/teams/{teamId}/recent")
    public List<Match> recent(@PathVariable int teamId) {
        return client.getFinishedMatches(teamId, 10);
    }
}
