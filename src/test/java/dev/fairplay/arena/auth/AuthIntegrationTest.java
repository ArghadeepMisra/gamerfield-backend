package dev.fairplay.arena.auth;

import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void registersPlayerAndJoinsLeaderboard() throws Exception {
        String body = """
                {
                  "username": "Neo_42",
                  "email": "Neo@Example.com",
                  "password": "password123",
                  "displayName": "Neo",
                  "country": "IN"
                }
                """;
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.username").value("Neo_42"))
                .andExpect(jsonPath("$.email").value("Neo@Example.com"))
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rating").value(1000))
                .andReturn();

        JsonNode json = jsonMapper.readTree(result.getResponse().getContentAsString());
        String playerId = json.get("id").asString();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/players/" + playerId);
        assertThat(redisTemplate.opsForZSet().score("leaderboard:global", playerId)).isEqualTo(1000.0);
    }

    @Test
    void rejectsDuplicateUsernameAndEmailCaseInsensitively() throws Exception {
        data.player("neo_42");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "NEO_42",
                                  "email": "other@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "other_user",
                                  "email": "NEO_42@EXAMPLE.COM",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsBadEmailWithValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "neo_42",
                                  "email": "not-an-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    @Test
    void loginRotatesAndDetectsRefreshReuse() throws Exception {
        Player player = data.player("neo_42");

        // Wrong password -> 401
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "neo_42", "password": "wrong-password" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // Login 1 (case-insensitive username)
        MvcResult login1 = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "NEO_42", "password": "password123" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        // Login 2 (second session)
        MvcResult login2 = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "neo_42", "password": "password123" }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String refresh1 = jsonMapper.readTree(login1.getResponse().getContentAsString()).get("refreshToken").asString();
        String refresh2 = jsonMapper.readTree(login2.getResponse().getContentAsString()).get("refreshToken").asString();

        // Using a refresh token as a Bearer token fails with 401
        mvc.perform(get("/api/v1/players/" + player.getId())
                        .header("Authorization", "Bearer " + refresh1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // Rotate refresh1
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        String rotatedRefresh = jsonMapper.readTree(rotated.getResponse().getContentAsString())
                .get("refreshToken").asString();
        assertThat(rotatedRefresh).isNotEqualTo(refresh1);

        // Replay old refresh1 -> 401 REFRESH_TOKEN_REUSED and revokes ALL tokens for this player
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        // Second session's refresh2 and rotatedRefresh are now also revoked
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void expiredRefreshTokenIsInvalidAndDoesNotTriggerMassRevocation() throws Exception {
        data.player("neo_42");

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "neo_42", "password": "password123" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String refresh1 = jsonMapper.readTree(login.getResponse().getContentAsString())
                .get("refreshToken").asString();

        // Rotate once so refresh1 is revoked, and keep the new (still valid) token.
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh2 = jsonMapper.readTree(rotated.getResponse().getContentAsString())
                .get("refreshToken").asString();

        // Make the revoked token also expired.
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE revoked_at IS NOT NULL",
                java.sql.Timestamp.from(java.time.Instant.parse("2020-01-01T00:00:00Z")));

        // Expired -> plain INVALID_REFRESH_TOKEN, not REFRESH_TOKEN_REUSED.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // ...and the player's other session was not revoked.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isOk());
    }
}
