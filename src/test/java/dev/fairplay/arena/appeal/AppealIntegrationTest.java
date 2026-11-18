package dev.fairplay.arena.appeal;

import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppealIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void enforcesAppealLifecycleOneAppealPerBanAndDecideOnce() throws Exception {
        Player mod = data.moderator();
        Player activePlayer = data.player("active_p");
        Player bannedPlayer = data.player("banned_p", AccountStatus.ACTIVE, 1350);

        // Active player cannot submit appeal
        mvc.perform(post("/api/v1/appeals")
                        .header("Authorization", data.bearer(activePlayer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statement": "I want to appeal a ban that does not exist yet." }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_NOT_ALLOWED"));

        // Ban bannedPlayer
        mvc.perform(post("/api/v1/moderation/players/" + bannedPlayer.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "HUMAN", "reason": "Confirmed cheat" }
                                """))
                .andExpect(status().isCreated());

        // Submit appeal -> 201 PENDING, player becomes APPEALED
        MvcResult appealResult = mvc.perform(post("/api/v1/appeals")
                        .header("Authorization", data.bearer(bannedPlayer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statement": "My account was compromised and I have secured it now." }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String appeal1Id = jsonMapper.readTree(appealResult.getResponse().getContentAsString()).get("id").asString();

        // List pending appeals
        mvc.perform(get("/api/v1/appeals?status=PENDING")
                        .header("Authorization", data.bearer(mod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(appeal1Id));

        // Uphold -> player remains BANNED, off leaderboard
        mvc.perform(post("/api/v1/appeals/" + appeal1Id + "/decision")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "UPHOLD", "note": "Account owner is responsible" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPHELD"));

        assertThat(redisTemplate.opsForZSet().score("leaderboard:global", bannedPlayer.getId().toString())).isNull();

        // Deciding twice -> 409 APPEAL_ALREADY_DECIDED
        mvc.perform(post("/api/v1/appeals/" + appeal1Id + "/decision")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "OVERTURN", "note": "Second decision" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_DECIDED"));

        // Second appeal on the same ban -> 409 APPEAL_ALREADY_EXISTS
        mvc.perform(post("/api/v1/appeals")
                        .header("Authorization", data.bearer(bannedPlayer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statement": "Please let me appeal this exact same ban a second time." }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPEAL_ALREADY_EXISTS"));

        // Another player banned and overturned -> REINSTATED and back on leaderboard
        Player overturnedPlayer = data.player("overturn_p", AccountStatus.ACTIVE, 1500);
        mvc.perform(post("/api/v1/moderation/players/" + overturnedPlayer.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "HUMAN", "reason": "False positive" }
                                """))
                .andExpect(status().isCreated());

        MvcResult appeal2 = mvc.perform(post("/api/v1/appeals")
                        .header("Authorization", data.bearer(overturnedPlayer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statement": "False positive during tournament match, please review." }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String appeal2Id = jsonMapper.readTree(appeal2.getResponse().getContentAsString()).get("id").asString();

        mvc.perform(post("/api/v1/appeals/" + appeal2Id + "/decision")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "OVERTURN", "note": "Confirmed false positive" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OVERTURNED"));

        mvc.perform(get("/api/v1/players/" + overturnedPlayer.getId())
                        .header("Authorization", data.bearer(mod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REINSTATED"));

        assertThat(redisTemplate.opsForZSet().score("leaderboard:global", overturnedPlayer.getId().toString()))
                .isEqualTo(1500.0);
    }
}
