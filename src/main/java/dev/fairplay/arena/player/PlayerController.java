package dev.fairplay.arena.player;

import dev.fairplay.arena.common.web.CurrentUser;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.dto.PlayerDetailResponse;
import dev.fairplay.arena.player.dto.PlayerSummaryResponse;
import dev.fairplay.arena.player.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
@Validated
@Tag(name = "Players", description = "Player profiles and search")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current player profile")
    public PlayerDetailResponse me(@AuthenticationPrincipal Jwt jwt) {
        return playerService.getMe(CurrentUser.id(jwt));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('PLAYER')")
    @Operation(summary = "Update current player profile")
    public PlayerDetailResponse updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return playerService.updateMe(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player profile by ID")
    public PlayerDetailResponse getById(@PathVariable UUID id) {
        return playerService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MODERATOR','SERVICE')")
    @Operation(summary = "Search players")
    public PageResponse<PlayerSummaryResponse> search(
            @RequestParam(name = "q", required = false) @Size(max = 20) String q,
            @RequestParam(name = "status", required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return playerService.search(q, status, page, size);
    }
}
