package dev.fairplay.arena.player;

import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StatDistributionService statDistributionService;

    @Test
    void meHasEmailWhileOtherPlayerOmitsEmailAndPatchUpdates() throws Exception {
        Player neo = data.player("neo_42");
        Player trinity = data.player("trinity");

        // /me includes email
        mvc.perform(get("/api/v1/players/me")
                        .header("Authorization", data.bearer(neo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("neo_42@example.com"))
                .andExpect(jsonPath("$.percentiles").doesNotExist());

        // /{id} omits email
        mvc.perform(get("/api/v1/players/" + trinity.getId())
                        .header("Authorization", data.bearer(neo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("trinity"))
                .andExpect(jsonPath("$.email").doesNotExist());

        // PATCH /me updates displayName and country
        mvc.perform(patch("/api/v1/players/me")
                        .header("Authorization", data.bearer(neo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "The One", "country": "IN" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("The One"))
                .andExpect(jsonPath("$.country").value("IN"))
                .andExpect(jsonPath("$.email").value("neo_42@example.com"));
    }

    @Test
    void bannedPlayerCannotPatchMeAndPercentilesAppearAfterFiveMatches() throws Exception {
        Player banned = data.player("banned_user", AccountStatus.BANNED, 1000);
        mvc.perform(patch("/api/v1/players/me")
                        .header("Authorization", data.bearer(banned))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Nope" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));

        Player p1 = data.player("p_one");
        Player p2 = data.player("p_two");
        for (int i = 0; i < 4; i++) {
            data.match(p1, p2);
        }
        statDistributionService.refresh();
        mvc.perform(get("/api/v1/players/" + p1.getId())
                        .header("Authorization", data.bearer(p1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentiles").doesNotExist());

        data.match(p1, p2);
        statDistributionService.refresh();
        mvc.perform(get("/api/v1/players/" + p1.getId())
                        .header("Authorization", data.bearer(p1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentiles.headshotRate").isNumber())
                .andExpect(jsonPath("$.percentiles.accuracy").isNumber())
                .andExpect(jsonPath("$.percentiles.reactionSpeed").isNumber());
    }

    @Test
    void moderatorCanSearchByPrefixAndStatus() throws Exception {
        Player mod = data.moderator();
        data.player("neo_1", AccountStatus.ACTIVE, 1200);
        data.player("neo_2", AccountStatus.FLAGGED, 1400);
        data.player("other", AccountStatus.ACTIVE, 1500);

        mvc.perform(get("/api/v1/players?q=NEO_&status=FLAGGED")
                        .header("Authorization", data.bearer(mod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("neo_2"));

        mvc.perform(get("/api/v1/players?q=neo_")
                        .header("Authorization", data.bearer(mod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].username").value("neo_2"))
                .andExpect(jsonPath("$.content[1].username").value("neo_1"));
    }
}
