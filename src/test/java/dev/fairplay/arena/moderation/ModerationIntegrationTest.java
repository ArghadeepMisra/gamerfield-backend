package dev.fairplay.arena.moderation;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.moderation.dto.ModerationActionRequest;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
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

class ModerationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ModerationService moderationService;

    @Test
    void appliesAllowedTransitionsRejectsInvalidAndEnforcesGuardrails() throws Exception {
        Player mod = data.moderator();
        Player judge = data.service("judge-agent");
        Player target = data.player("suspect_1");

        // Moderator sending JUDGE source -> 422 INVALID_SOURCE
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "FLAG", "source": "JUDGE", "reason": "Wrong source" }
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_SOURCE"));

        // Service BAN without confidence -> 422 CONFIDENCE_REQUIRED
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(judge))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "JUDGE", "reason": "Missing confidence" }
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CONFIDENCE_REQUIRED"));

        // Service BAN at 0.79 -> 422 CONFIDENCE_TOO_LOW
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(judge))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "JUDGE", "reason": "Too low", "confidence": 0.79 }
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CONFIDENCE_TOO_LOW"));

        // Service FLAG with requiresHumanReview = true -> 201
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(judge))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "FLAG",
                                  "source": "JUDGE",
                                  "reason": "Borderline stats",
                                  "evidence": ["hs 0.80"],
                                  "confidence": 0.75,
                                  "requiresHumanReview": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.toStatus").value("FLAGGED"));

        // Queue filter humanReviewOnly
        Player flaggedNoReview = data.player("suspect_2");
        mvc.perform(post("/api/v1/moderation/players/" + flaggedNoReview.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "FLAG", "source": "HUMAN", "reason": "Manual check", "requiresHumanReview": false }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/moderation/queue?humanReviewOnly=false")
                        .header("Authorization", data.bearer(mod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/v1/moderation/queue?humanReviewOnly=true")
                        .header("Authorization", data.bearer(mod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("suspect_1"));

        // Moderator CLEAR -> ACTIVE
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "CLEAR", "source": "HUMAN", "reason": "Reviewed clean" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toStatus").value("ACTIVE"));

        // Invalid CLEAR from ACTIVE -> 409
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "CLEAR", "source": "HUMAN", "reason": "Invalid" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        // Service BAN at 0.80 -> 201
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(judge))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "JUDGE", "reason": "Aimbot", "confidence": 0.80 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toStatus").value("BANNED"));

        // NOTE -> BANNED unchanged
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "NOTE", "source": "HUMAN", "reason": "Added log note" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toStatus").value("BANNED"));

        // History newest first (4 entries: NOTE, BAN, CLEAR, FLAG)
        mvc.perform(get("/api/v1/moderation/players/" + target.getId() + "/history")
                        .header("Authorization", data.bearer(judge)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].action").value("NOTE"))
                .andExpect(jsonPath("$.content[1].action").value("BAN"))
                .andExpect(jsonPath("$.content[2].action").value("CLEAR"))
                .andExpect(jsonPath("$.content[3].action").value("FLAG"));
    }

    @Test
    void parallelBansGiveExactlyOneSuccessAndOneConflict() throws Exception {
        Player mod = data.moderator();
        Player judge = data.service("judge-agent");
        Player target = data.player("race_player");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Throwable> f1 = pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    moderationService.applyAction(
                            target.getId(),
                            new ModerationActionRequest(
                                    ModerationActionType.BAN,
                                    ActionSource.HUMAN,
                                    "Manual ban",
                                    null,
                                    null,
                                    false),
                            mod.getId());
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            Future<Throwable> f2 = pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    moderationService.applyAction(
                            target.getId(),
                            new ModerationActionRequest(
                                    ModerationActionType.BAN,
                                    ActionSource.JUDGE,
                                    "Judge ban",
                                    List.of("evidence"),
                                    new BigDecimal("0.95"),
                                    false),
                            judge.getId());
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            ready.await();
            start.countDown();

            Throwable t1 = f1.get();
            Throwable t2 = f2.get();

            List<Throwable> failures = List.of(t1 == null ? new NoError() : t1, t2 == null ? new NoError() : t2)
                    .stream()
                    .filter(t -> !(t instanceof NoError))
                    .toList();

            assertThat(failures).hasSize(1);
            Throwable conflict = failures.getFirst();
            assertThat(conflict).satisfiesAnyOf(
                    ex -> assertThat(ex).isInstanceOf(ObjectOptimisticLockingFailureException.class),
                    ex -> assertThat(ex).isInstanceOf(ApiException.class));
        }
    }

    private static final class NoError extends Throwable {}
}
