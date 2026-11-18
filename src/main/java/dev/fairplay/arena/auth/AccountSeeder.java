package dev.fairplay.arena.auth;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import dev.fairplay.arena.player.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class AccountSeeder implements ApplicationRunner {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final ArenaProperties props;
    private final Clock clock;

    public AccountSeeder(
            PlayerRepository playerRepository,
            PasswordEncoder passwordEncoder,
            ArenaProperties props,
            Clock clock) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ArenaProperties.AccountCredential mod = props.accounts().moderator();
        seedIfMissing(mod.username(), mod.password(), Role.MODERATOR);
        for (ArenaProperties.AccountCredential service : props.accounts().services()) {
            seedIfMissing(service.username(), service.password(), Role.SERVICE);
        }
    }

    private void seedIfMissing(String username, String rawPassword, Role role) {
        if (playerRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        Instant now = Instant.now(clock);
        Player account = new Player(
                UUID.randomUUID(),
                username,
                username + "@fairplay.local",
                passwordEncoder.encode(rawPassword),
                role,
                AccountStatus.ACTIVE,
                null,
                null,
                0,
                now);
        playerRepository.save(account);
    }
}
