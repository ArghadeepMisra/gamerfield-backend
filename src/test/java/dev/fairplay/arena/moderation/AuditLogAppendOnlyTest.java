package dev.fairplay.arena.moderation;

import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogAppendOnlyTest extends AbstractIntegrationTest {

    @Autowired
    private ModerationActionRepository moderationActionRepository;

    @Test
    void databaseRejectsUpdateAndDeleteOnModerationActions() {
        Player mod = data.moderator();
        Player target = data.player("audit_target");

        UUID actionId = UUID.randomUUID();
        moderationActionRepository.saveAndFlush(new ModerationAction(
                actionId,
                target,
                ModerationActionType.NOTE,
                AccountStatus.ACTIVE,
                AccountStatus.ACTIVE,
                mod,
                ActionSource.HUMAN,
                "Initial reason",
                null,
                null,
                false,
                Instant.now()));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE moderation_actions SET reason = 'tampered' WHERE id = ?", actionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("moderation_actions is append-only");

        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM moderation_actions WHERE id = ?", actionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("moderation_actions is append-only");
    }
}
