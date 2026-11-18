package dev.fairplay.arena.leaderboard;

import dev.fairplay.arena.leaderboard.dto.LeaderboardPage;
import dev.fairplay.arena.leaderboard.dto.PlayerRankResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leaderboard")
@Validated
@Tag(name = "Leaderboard", description = "Global rankings and player rank lookups")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    // Public endpoints: clear the global bearer requirement so Swagger UI shows the lock only on secured routes.
    @SecurityRequirements
    @GetMapping
    @Operation(summary = "Get global leaderboard page")
    public LeaderboardPage getLeaderboard(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return leaderboardService.getLeaderboard(offset, limit);
    }

    @SecurityRequirements
    @GetMapping("/players/{id}")
    @Operation(summary = "Get a player's leaderboard rank")
    public PlayerRankResponse getPlayerRank(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "redis") String source) {
        return leaderboardService.getPlayerRank(id, source);
    }
}
