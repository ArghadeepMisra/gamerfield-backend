package dev.fairplay.arena.leaderboard;

import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaderboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LeaderboardRebuilder rebuilder;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void returnsTopPageAndReflectsBanAndOverturn() throws Exception {
        Player mod = data.moderator();
        Player top = data.player("top_player", AccountStatus.ACTIVE, 1800);
        Player mid = data.player("mid_player", AccountStatus.ACTIVE, 1400);
        data.player("low_player", AccountStatus.ACTIVE, 1100);

        mvc.perform(get("/api/v1/leaderboard?offset=0&limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].username").value("top_player"))
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.entries[1].username").value("mid_player"));

        // Ban mid_player -> removed from leaderboard
        mvc.perform(post("/api/v1/moderation/players/" + mid.getId() + "/actions")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "HUMAN", "reason": "Cheating confirmed" }
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mvc.perform(get("/api/v1/leaderboard/players/" + mid.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_RANKED"));

        // Appeal & Overturn -> restored to leaderboard
        MvcResult appealRes = mvc.perform(post("/api/v1/appeals")
                        .header("Authorization", data.bearer(mid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "statement": "I was falsely flagged during a custom lobby session." }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String appealId = jsonMapper.readTree(appealRes.getResponse().getContentAsString()).get("id").asString();

        mvc.perform(post("/api/v1/appeals/" + appealId + "/decision")
                        .header("Authorization", data.bearer(mod))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decision": "OVERTURN", "note": "Verified clean" }
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/leaderboard/players/" + mid.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.totalRanked").value(3));

        // Ensure top is still #1
        assertThat(leaderboardService.getPlayerRank(top.getId(), "redis").rank()).isEqualTo(1);
    }

    @Test
    void redisRankEqualsDbRankFor200PlayersWithForcedRatingTiesAndAfterRebuild() {
        List<Player> players = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            int tiedRating = 1000 + (i % 10) * 25;
            players.add(data.player("tie_" + i, AccountStatus.ACTIVE, tiedRating));
        }

        for (Player p : players) {
            var redisRank = leaderboardService.getPlayerRank(p.getId(), "redis");
            var dbRank = leaderboardService.getPlayerRank(p.getId(), "db");
            assertThat(redisRank.rank()).isEqualTo(dbRank.rank());
            assertThat(redisRank.totalRanked()).isEqualTo(200);
        }

        // Flush Redis, rebuild, and assert identical ranks
        redisTemplate.delete("leaderboard:global");
        rebuilder.rebuild();

        for (Player p : players) {
            var redisRank = leaderboardService.getPlayerRank(p.getId(), "redis");
            var dbRank = leaderboardService.getPlayerRank(p.getId(), "db");
            assertThat(redisRank.rank()).isEqualTo(dbRank.rank());
        }
    }
}
