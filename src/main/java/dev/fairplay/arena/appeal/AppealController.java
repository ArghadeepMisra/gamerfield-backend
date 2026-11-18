package dev.fairplay.arena.appeal;

import dev.fairplay.arena.appeal.dto.AppealResponse;
import dev.fairplay.arena.appeal.dto.DecideAppealRequest;
import dev.fairplay.arena.appeal.dto.SubmitAppealRequest;
import dev.fairplay.arena.common.web.CurrentUser;
import dev.fairplay.arena.common.web.PageResponse;
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
@RequestMapping("/api/v1/appeals")
@Validated
@Tag(name = "Appeals", description = "Ban appeals submission and moderator decisions")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLAYER')")
    @Operation(summary = "Submit an appeal for an active ban")
    public AppealResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmitAppealRequest request) {
        return appealService.submitAppeal(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "List appeals by status")
    public PageResponse<AppealResponse> list(
            @RequestParam(defaultValue = "PENDING") AppealStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return appealService.listAppeals(status, page, size);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('MODERATOR')")
    @Operation(summary = "Uphold or overturn a pending appeal")
    public AppealResponse decide(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DecideAppealRequest request) {
        return appealService.decideAppeal(id, request, CurrentUser.id(jwt));
    }
}
