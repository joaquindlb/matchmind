package com.matchmind.prediction;

import com.matchmind.prediction.PredictionModels.Prediction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/predict")
    public Prediction predict(@RequestParam(defaultValue = "PL") String competition,
                              @RequestParam int home,
                              @RequestParam int away) {
        return predictionService.predict(competition, home, away);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}