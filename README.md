# MatchMind

**Football match forecasts from a statistical model, tested against real results and explained by AI.**

Built solo at **ShellHacks 2026** (FIU, Miami).

## What it does

- Shows **this week's fixtures** for nine leagues, each with a quick forecast.
- Predicts any match: **home win, draw and away win probabilities**, expected goals and the most likely scores.
- Writes a short **match preview with Gemini** that explains the numbers.
- **Measures its own accuracy** by replaying the season.

## How it works

MatchMind downloads this season's results from the [football-data.org](https://www.football-data.org) API and rates each team's attack and defense against the league average. From those ratings it calculates the expected goals for each side and uses a **Poisson distribution** to find the probability of every possible score.

The AI doesn't make the prediction. The Java model does the math, and Gemini only explains the result, with instructions never to invent players, injuries or news.

## Accuracy

<!-- Replace X and Y with the numbers from the "Test the model" button -->

Each past match is predicted using only the games played before it, so the model never sees the answer.

|           Serie A         |
| Strategy | Correct result |
|----------|----------------|
| MatchMind| **56.7%** |
| Always pick the home team | 46.7% |
| Random guess | ~33% |

## Built with

Java 25 · Spring Boot 4 · Gemini API · football-data.org API · HTML, CSS and JavaScript

## Run it locally

1. Get a free token from [football-data.org](https://www.football-data.org)
   and a Gemini API key from [Google AI Studio](https://aistudio.google.com).
2. Set them as environment variables:

   FOOTBALL_API_TOKEN=your_token
   GEMINI_API_KEY=your_key

3. Run "./mvnw spring-boot:run" or ("mvnw.cmd spring-boot:run" on Windows) and open http://localhost:8080.

## What's next

- Deploy it online.
- Add previous seasons and home/away form to improve accuracy.

---
Developed by Joaquin De La Barrera · [GitHub](https://github.com/joaquindlb)



