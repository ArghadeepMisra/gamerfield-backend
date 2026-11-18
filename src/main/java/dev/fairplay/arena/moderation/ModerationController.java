package dev.fairplay.arena.moderation;

import dev.fairplay.arena.common.web.CurrentUser;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.moderation.dto.ModerationActionRequest;
import dev.fairplay.arena.moderation.dto.ModerationActionResponse;
import dev.fairplay.arena.moderation.dto.QueueItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/moderation")
@Validated
@Tag(name = "Moderation", description = "Moderation actions, queue, and audit history")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/players/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MODERATOR','SERVICE')")
    @Operation(summary = "Apply a moderation action to a player")
    public ModerationActionResponse applyAction(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ModerationActionRequest request) {
        return moderationService.applyAction(id, request, CurrentUser.id(jwt));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "List flagged players awaiting moderation review")
    public PageResponse<QueueItemResponse> getQueue(
            @RequestParam(defaultValue = "false") boolean humanReviewOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return moderationService.getQueue(humanReviewOnly, page, size);
    }

    @GetMapping("/players/{id}/history")
    @PreAuthorize("hasAnyRole('MODERATOR','SERVICE')")
    @Operation(summary = "Get moderation audit history for a player")
    public PageResponse<ModerationActionResponse> getHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return moderationService.getHistory(id, page, size);
    }
}
