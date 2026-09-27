package com.matchmind.fixtures;

import com.matchmind.fixtures.FixturesService.FixtureForecast;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FixturesController {

    private final FixturesService fixturesService;

    public FixturesController(FixturesService fixturesService) {
        this.fixturesService = fixturesService;
    }

    @GetMapping("/competitions/{code}/upcoming")
    public List<FixtureForecast> upcoming(@PathVariable String code) {
        return fixturesService.upcoming(code);
    }
}