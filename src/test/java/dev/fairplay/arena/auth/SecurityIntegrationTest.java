package dev.fairplay.arena.auth;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ArenaProperties props;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void rejectsMissingAndExpiredTokensAndEnforcesRoles() throws Exception {
        Player player = data.player("neo_42");
        Player target = data.player("trinity");

        // No token -> 401 problem JSON
        mvc.perform(get("/api/v1/players/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // PLAYER calling endpoint 13 -> 403 FORBIDDEN
        mvc.perform(post("/api/v1/moderation/players/" + target.getId() + "/actions")
                        .header("Authorization", data.bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "BAN", "source": "HUMAN", "reason": "Unauthorized" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // PLAYER calling endpoint 8 -> 403 FORBIDDEN
        mvc.perform(post("/api/v1/matches")
                        .header("Authorization", data.bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        // A valid body: Spring validates @Valid request bodies before @PreAuthorize runs,
                        // so an invalid body such as {} would return 400 instead of the role check's 403.
                        .content("""
                                {
                                  "externalId": "forbidden-match",
                                  "gameMode": "FFA",
                                  "mapName": "Rust Yard",
                                  "startedAt": "2026-11-17T10:00:00Z",
                                  "endedAt": "2026-11-17T10:12:30Z",
                                  "participants": [
                                    {"playerId":"%s","placement":1,"kills":1,"deaths":0,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200},
                                    {"playerId":"%s","placement":2,"kills":0,"deaths":1,"headshots":0,"shotsFired":10,"shotsHit":5,"avgReactionMs":200}
                                  ]
                                }
                                """.formatted(player.getId(), target.getId())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Public leaderboard without token -> 200
        mvc.perform(get("/api/v1/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        // Expired token minted with a past Clock -> 401
        Clock pastClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        TokenService pastTokenService = new TokenService(jwtEncoder, props, pastClock);
        String expired = pastTokenService.issueAccessToken(player).value();

        mvc.perform(get("/api/v1/players/me")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void openApiSpecDocumentsExactly18Operations() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = jsonMapper.readTree(result.getResponse().getContentAsString());
        JsonNode paths = root.get("paths");
        int operationCount = 0;
        Iterator<String> pathNames = paths.propertyNames().iterator();
        while (pathNames.hasNext()) {
            String path = pathNames.next();
            JsonNode methods = paths.get(path);
            operationCount += methods.size();
        }
        assertThat(operationCount).isEqualTo(18);
    }
}
