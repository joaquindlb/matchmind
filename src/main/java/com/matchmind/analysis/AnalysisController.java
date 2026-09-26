package com.matchmind.analysis;

import com.matchmind.analysis.AnalysisService.AnalysisResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analysis")
    public AnalysisResult analysis(@RequestParam(defaultValue = "PL") String competition,
                                   @RequestParam int home,
                                   @RequestParam int away) {
        return analysisService.analyze(competition, home, away);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // If Gemini fails, the page still shows the prediction and explains what happened
    @ExceptionHandler({RestClientException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> aiUnavailable(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "The written analysis is unavailable right now: " + e.getMessage()));
    }
}