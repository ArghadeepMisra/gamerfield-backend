package dev.fairplay.arena.common.error;

import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorHandlingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void returnsProblemDetailWithCodeForRouteMalformedAndMissingResources() throws Exception {
        Player p = data.player("neo_42");

        // Unknown route -> 404 NOT_FOUND
        mvc.perform(get("/api/v1/nonexistent")
                        .header("Authorization", data.bearer(p)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));

        // Malformed JSON -> 400 MALFORMED_REQUEST
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400));

        // Unknown player -> 404 PLAYER_NOT_FOUND
        mvc.perform(get("/api/v1/players/" + UUID.randomUUID())
                        .header("Authorization", data.bearer(p)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));

        // Invalid leaderboard source -> 400 VALIDATION_FAILED
        mvc.perform(get("/api/v1/leaderboard/players/" + p.getId() + "?source=bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Method not allowed -> 405 METHOD_NOT_ALLOWED
        // (authenticated: only GET is public, so an anonymous DELETE is rejected with 401 before routing)
        mvc.perform(delete("/api/v1/leaderboard")
                        .header("Authorization", data.bearer(p)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
