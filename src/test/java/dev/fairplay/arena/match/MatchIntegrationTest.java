package dev.fairplay.arena.match;

import dev.fairplay.arena.match.dto.ParticipantRequest;
import dev.fairplay.arena.match.dto.RecordMatchRequest;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private MatchService matchService;

    @Test
    void recordsMatchUpdatesRatingsTotalsRedisAndIsIdempotent() throws Exception {
        Player svc = data.service("telemetry-sim");
        Player p1 = data.player("p1");
        Player p2 = data.player("p2");

        String body = """
                {
                  "externalId": "sim-2026-11-17-0001",
                  "gameMode": "FFA",
                  "mapName": "Rust Yard",
                  "startedAt": "2026-11-17T10:00:00Z",
                  "endedAt": "2026-11-17T10:12:30Z",
                  "participants": [
                    {
                      "playerId": "%s",
                      "placement": 1,
                      "kills": 14,
                      "deaths": 3,
                      "headshots": 6,
                      "shotsFired": 180,
                      "shotsHit": 61,
                      "avgReactionMs": 212
                    },
                    {
                      "playerId": "%s",
                      "placement": 2,
                      "kills": 5,
                      "deaths": 10,
                      "headshots": 1,
                      "shotsFired": 100,
                      "shotsHit": 20,
                      "avgReactionMs": 260
                    }
                  ]
                }
                """.formatted(p1.getId(), p2.getId());

        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(svc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value("sim-2026-11-17-0001"))
                .andExpect(jsonPath("$.participants[0].ratingBefore").value(1000))
                .andExpect(jsonPath("$.participants[0].ratingAfter").value(1030))
                .andExpect(jsonPath("$.participants[1].ratingAfter").value(970));

        // Replay with identical externalId -> 200 and nothing doubles
        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(svc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].ratingAfter").value(1030));

        Player updated1 = playerRepository.findById(p1.getId()).orElseThrow();
        assertThat(updated1.getRating()).isEqualTo(1030);
        assertThat(updated1.getMatchesPlayed()).isEqualTo(1);
        assertThat(updated1.getTotalKills()).isEqualTo(14);
        assertThat(redisTemplate.opsForZSet().score("leaderboard:global", p1.getId().toString())).isEqualTo(1030.0);
        assertThat(redisTemplate.opsForZSet().score("leaderboard:global", p2.getId().toString())).isEqualTo(970.0);

        mvc.perform(get("/api/v1/players/" + p1.getId() + "/matches")
                        .header("Authorization", data.bearer(p1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].participantCount").value(2))
                .andExpect(jsonPath("$.content[0].ratingAfter").value(1030));
    }

    @Test
    void validatesMatchRulesAndEligibility() throws Exception {
        Player svc = data.service("telemetry-sim");
        Player p1 = data.player("p1");
        Player p2 = data.player("p2");
        Player banned = data.player("banned_p", AccountStatus.BANNED, 1000);

        // Placements not 1..N
        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(svc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalId": "bad-place",
                                  "gameMode": "FFA",
                                  "mapName": "Rust Yard",
                                  "startedAt": "2026-11-17T10:00:00Z",
                                  "endedAt": "2026-11-17T10:12:30Z",
                                  "participants": [
                                    {"playerId":"%s","placement":1,"kills":1,"deaths":0,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200},
                                    {"playerId":"%s","placement":3,"kills":1,"deaths":1,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200}
                                  ]
                                }
                                """.formatted(p1.getId(), p2.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Duplicate player
        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(svc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalId": "dup-player",
                                  "gameMode": "FFA",
                                  "mapName": "Rust Yard",
                                  "startedAt": "2026-11-17T10:00:00Z",
                                  "endedAt": "2026-11-17T10:12:30Z",
                                  "participants": [
                                    {"playerId":"%s","placement":1,"kills":1,"deaths":0,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200},
                                    {"playerId":"%s","placement":2,"kills":1,"deaths":1,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200}
                                  ]
                                }
                                """.formatted(p1.getId(), p1.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Headshots > kills
        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(svc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalId": "bad-hs",
                                  "gameMode": "FFA",
                                  "mapName": "Rust Yard",
                                  "startedAt": "2026-11-17T10:00:00Z",
                                  "endedAt": "2026-11-17T10:12:30Z",
                                  "participants": [
                                    {"playerId":"%s","placement":1,"kills":2,"deaths":0,"headshots":3,"shotsFired":10,"shotsHit":5,"avgReactionMs":200},
                                    {"playerId":"%s","placement":2,"kills":1,"deaths":1,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200}
                                  ]
                                }
                                """.formatted(p1.getId(), p2.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Banned participant -> 422 PLAYER_NOT_ELIGIBLE
        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(svc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalId": "banned-part",
                                  "gameMode": "FFA",
                                  "mapName": "Rust Yard",
                                  "startedAt": "2026-11-17T10:00:00Z",
                                  "endedAt": "2026-11-17T10:12:30Z",
                                  "participants": [
                                    {"playerId":"%s","placement":1,"kills":1,"deaths":0,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200},
                                    {"playerId":"%s","placement":2,"kills":1,"deaths":1,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200}
                                  ]
                                }
                                """.formatted(p1.getId(), banned.getId())))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_ELIGIBLE"));
    }
    @Test
    void concurrentRequestsWithSameExternalIdCreateOneMatchAndReplayTheOther() throws Exception {
        Player svc = data.service("telemetry-sim");
        Player p1 = data.player("race1");
        Player p2 = data.player("race2");

        RecordMatchRequest request = new RecordMatchRequest(
                "race-external-id",
                GameMode.FFA,
                "Rust Yard",
                Instant.parse("2026-11-17T10:00:00Z"),
                Instant.parse("2026-11-17T10:12:30Z"),
                List.of(
                        new ParticipantRequest(p1.getId(), 1, 10, 2, 4, 100, 35, 220),
                        new ParticipantRequest(p2.getId(), 2, 5, 8, 1, 100, 20, 260)));

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MatchService.RecordResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return matchService.recordMatch(request, svc.getId());
                }));
            }
            ready.await();
            go.countDown();

            long created = 0;
            for (Future<MatchService.RecordResult> f : futures) {
                // Neither call may throw (previously the loser failed with a 500-mapped unique violation).
                if (f.get().created()) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Integer matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matches WHERE external_id = 'race-external-id'", Integer.class);
        assertThat(matches).isEqualTo(1);
        // Ratings/stats were applied exactly once.
        assertThat(playerRepository.findById(p1.getId()).orElseThrow().getMatchesPlayed()).isEqualTo(1);
        assertThat(playerRepository.findById(p2.getId()).orElseThrow().getMatchesPlayed()).isEqualTo(1);
    }
}
