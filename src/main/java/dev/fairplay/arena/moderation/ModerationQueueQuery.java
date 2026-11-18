package dev.fairplay.arena.moderation;

import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.moderation.dto.QueueItemResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ModerationQueueQuery {

    private static final String QUEUE_SQL = """
            SELECT p.id, p.username, p.rating, p.status_changed_at,
                   a.id AS action_id, a.action, a.source, a.reason, a.confidence,
                   a.requires_human_review, a.created_at AS action_at
            FROM players p
            JOIN LATERAL (
                SELECT * FROM moderation_actions m
                WHERE m.player_id = p.id
                ORDER BY m.created_at DESC
                LIMIT 1
            ) a ON TRUE
            WHERE p.status = 'FLAGGED'
              AND (:humanReviewOnly = FALSE OR a.requires_human_review)
            ORDER BY p.status_changed_at ASC, p.id ASC
            LIMIT :size OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM players p
            JOIN LATERAL (
                SELECT * FROM moderation_actions m
                WHERE m.player_id = p.id
                ORDER BY m.created_at DESC
                LIMIT 1
            ) a ON TRUE
            WHERE p.status = 'FLAGGED'
              AND (:humanReviewOnly = FALSE OR a.requires_human_review)
            """;

    private final JdbcClient jdbcClient;

    public ModerationQueueQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public PageResponse<QueueItemResponse> fetchQueue(boolean humanReviewOnly, int page, int size) {
        long offset = (long) page * size;
        long total = jdbcClient.sql(COUNT_SQL)
                .param("humanReviewOnly", humanReviewOnly)
                .query(Long.class)
                .single();

        List<QueueItemResponse> items = jdbcClient.sql(QUEUE_SQL)
                .param("humanReviewOnly", humanReviewOnly)
                .param("size", size)
                .param("offset", offset)
                .query((rs, rowNum) -> new QueueItemResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getInt("rating"),
                        rs.getTimestamp("status_changed_at").toInstant(),
                        new QueueItemResponse.LatestAction(
                                rs.getObject("action_id", UUID.class),
                                ModerationActionType.valueOf(rs.getString("action")),
                                ActionSource.valueOf(rs.getString("source")),
                                rs.getString("reason"),
                                rs.getBigDecimal("confidence"),
                                rs.getBoolean("requires_human_review"),
                                rs.getTimestamp("action_at").toInstant())))
                .list();

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }
}
