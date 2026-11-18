package dev.fairplay.arena.match;

import dev.fairplay.arena.common.web.CurrentUser;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.match.dto.MatchResponse;
import dev.fairplay.arena.match.dto.PlayerMatchResponse;
import dev.fairplay.arena.match.dto.RecordMatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Matches", description = "Match recording and history")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/matches")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Record a completed match")
    public ResponseEntity<MatchResponse> recordMatch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecordMatchRequest request) {
        MatchService.RecordResult result = matchService.recordMatch(request, CurrentUser.id(jwt));
        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/v1/matches/" + result.match().id()))
                    .body(result.match());
        }
        return ResponseEntity.ok(result.match());
    }

    @GetMapping("/matches/{id}")
    @Operation(summary = "Get match details by ID")
    public MatchResponse getMatch(@PathVariable UUID id) {
        return matchService.getMatch(id);
    }

    @GetMapping("/players/{id}/matches")
    @Operation(summary = "Get match history for a player")
    public PageResponse<PlayerMatchResponse> getPlayerMatches(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return matchService.getPlayerMatches(id, page, size);
    }
}
