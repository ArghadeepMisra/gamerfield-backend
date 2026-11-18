CREATE TABLE players (
    id                 UUID PRIMARY KEY,
    username           VARCHAR(20)  NOT NULL,
    email              VARCHAR(254) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(16)  NOT NULL CHECK (role IN ('PLAYER','MODERATOR','SERVICE')),
    status             VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE','FLAGGED','BANNED','APPEALED','REINSTATED')),
    display_name       VARCHAR(40),
    country            CHAR(2),
    rating             INTEGER      NOT NULL CHECK (rating >= 0),
    matches_played     INTEGER      NOT NULL DEFAULT 0,
    total_kills        BIGINT       NOT NULL DEFAULT 0,
    total_deaths       BIGINT       NOT NULL DEFAULT 0,
    total_headshots    BIGINT       NOT NULL DEFAULT 0,
    total_shots_fired  BIGINT       NOT NULL DEFAULT 0,
    total_shots_hit    BIGINT       NOT NULL DEFAULT 0,
    sum_reaction_ms    BIGINT       NOT NULL DEFAULT 0,
    status_changed_at  TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_players_username_lower ON players (lower(username));
CREATE UNIQUE INDEX ux_players_email_lower    ON players (lower(email));
CREATE INDEX ix_players_rating ON players (rating DESC, id);
CREATE INDEX ix_players_status ON players (status, status_changed_at);

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    player_id    UUID        NOT NULL REFERENCES players(id),
    token_hash   CHAR(64)    NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID
);
CREATE INDEX ix_refresh_tokens_player ON refresh_tokens (player_id);

CREATE TABLE matches (
    id           UUID PRIMARY KEY,
    external_id  VARCHAR(64) NOT NULL UNIQUE,
    game_mode    VARCHAR(16) NOT NULL CHECK (game_mode IN ('FFA','BATTLE_ROYALE')),
    map_name     VARCHAR(40) NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    ended_at     TIMESTAMPTZ NOT NULL,
    recorded_by  UUID        NOT NULL REFERENCES players(id),
    created_at   TIMESTAMPTZ NOT NULL,
    CHECK (ended_at > started_at)
);
CREATE INDEX ix_matches_ended_at ON matches (ended_at DESC);

CREATE TABLE match_participants (
    match_id         UUID    NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    player_id        UUID    NOT NULL REFERENCES players(id),
    placement        INTEGER NOT NULL CHECK (placement >= 1),
    kills            INTEGER NOT NULL CHECK (kills >= 0),
    deaths           INTEGER NOT NULL CHECK (deaths >= 0),
    headshots        INTEGER NOT NULL CHECK (headshots >= 0 AND headshots <= kills),
    shots_fired      INTEGER NOT NULL CHECK (shots_fired >= 0),
    shots_hit        INTEGER NOT NULL CHECK (shots_hit >= 0 AND shots_hit <= shots_fired),
    avg_reaction_ms  INTEGER NOT NULL CHECK (avg_reaction_ms > 0),
    rating_before    INTEGER NOT NULL,
    rating_after     INTEGER NOT NULL,
    PRIMARY KEY (match_id, player_id)
);
CREATE INDEX ix_participants_player ON match_participants (player_id);

CREATE TABLE moderation_actions (
    id                     UUID PRIMARY KEY,
    player_id              UUID         NOT NULL REFERENCES players(id),
    action                 VARCHAR(20)  NOT NULL CHECK (action IN ('FLAG','BAN','CLEAR','NOTE','APPEAL_SUBMITTED','APPEAL_UPHELD','APPEAL_OVERTURNED')),
    from_status            VARCHAR(16)  NOT NULL,
    to_status              VARCHAR(16)  NOT NULL,
    actor_id               UUID         NOT NULL REFERENCES players(id),
    source                 VARCHAR(16)  NOT NULL CHECK (source IN ('HUMAN','TELEMETRY','JUDGE','SYSTEM')),
    reason                 VARCHAR(500) NOT NULL,
    evidence               JSONB,
    confidence             NUMERIC(4,3) CHECK (confidence BETWEEN 0 AND 1),
    requires_human_review  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_moderation_player_time ON moderation_actions (player_id, created_at DESC);

CREATE TABLE appeals (
    id             UUID PRIMARY KEY,
    player_id      UUID          NOT NULL REFERENCES players(id),
    ban_action_id  UUID          NOT NULL UNIQUE REFERENCES moderation_actions(id),
    status         VARCHAR(16)   NOT NULL CHECK (status IN ('PENDING','UPHELD','OVERTURNED')),
    statement      VARCHAR(2000) NOT NULL,
    decided_by     UUID REFERENCES players(id),
    decision_note  VARCHAR(1000),
    created_at     TIMESTAMPTZ   NOT NULL,
    decided_at     TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_appeals_status_time ON appeals (status, created_at);
