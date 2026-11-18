package dev.fairplay.arena.auth;

import dev.fairplay.arena.auth.dto.LoginRequest;
import dev.fairplay.arena.auth.dto.RefreshRequest;
import dev.fairplay.arena.auth.dto.RegisterRequest;
import dev.fairplay.arena.auth.dto.TokenResponse;
import dev.fairplay.arena.player.dto.PlayerDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login, and token refresh")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Public endpoints: clear the global bearer requirement so Swagger UI shows the lock only on secured routes.
    @SecurityRequirements
    @PostMapping("/register")
    @Operation(summary = "Register a new player account")
    public ResponseEntity<PlayerDetailResponse> register(@Valid @RequestBody RegisterRequest request) {
        PlayerDetailResponse created = authService.register(request);
        return ResponseEntity
                .created(URI.create("/api/v1/players/" + created.id()))
                .body(created);
    }

    @SecurityRequirements
    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain access and refresh tokens")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and obtain a new token pair")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
