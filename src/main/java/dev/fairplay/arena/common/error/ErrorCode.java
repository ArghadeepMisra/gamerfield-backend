package dev.fairplay.arena.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.net.URI;

@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh token"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token reused"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "Account suspended"),
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "Player not found"),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Match not found"),
    APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Appeal not found"),
    PLAYER_NOT_RANKED(HttpStatus.NOT_FOUND, "Player not ranked"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Username taken"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email taken"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    APPEAL_NOT_ALLOWED(HttpStatus.CONFLICT, "Appeal not allowed"),
    APPEAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Appeal already exists"),
    APPEAL_ALREADY_DECIDED(HttpStatus.CONFLICT, "Appeal already decided"),
    TARGET_NOT_A_PLAYER(HttpStatus.valueOf(422), "Target not a player"),
    INVALID_SOURCE(HttpStatus.valueOf(422), "Invalid source"),
    CONFIDENCE_REQUIRED(HttpStatus.valueOf(422), "Confidence required"),
    CONFIDENCE_TOO_LOW(HttpStatus.valueOf(422), "Confidence too low"),
    PLAYER_NOT_ELIGIBLE(HttpStatus.valueOf(422), "Player not eligible"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public URI typeUri() {
        return URI.create("https://fairplay.dev/errors/" + name().toLowerCase().replace('_', '-'));
    }
}
