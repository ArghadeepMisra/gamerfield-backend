# Fairplay Arena

![CI](https://github.com/fairplay/arena/actions/workflows/ci.yml/badge.svg)
![Coverage](https://img.shields.io/badge/coverage-%E2%89%A585%25-brightgreen)

Measured line coverage: **89.6%** (JaCoCo, `./mvnw verify`, 71 tests); the build fails below 85%.

Fairplay Arena is the stateless REST backend that stores the source of truth for the Fairplay anti-cheat platform: player accounts, match results, a Redis-backed global leaderboard, moderation actions (`FLAG`, `BAN`, `CLEAR`, `NOTE`) backed by an append-only PostgreSQL audit log, and ban appeals. It is a single Java 21 / Spring Boot 4.1 service backed by PostgreSQL 17 and Redis 7.

## Architecture

```text
  Players (HTTP) ─────────┐
  Moderators (HTTP) ──────┼──► Fairplay Arena (Spring Boot 4.1 / REST + JWT)
  Service clients (HTTP) ─┘         │                         │
                                    ▼                         ▼
                              PostgreSQL 17              Redis 7 (ZSET)
                         (source of truth,          (O(log N) leaderboard
                          append-only audit log)     & rank lookups)
```

## Quick Start

```bash
cp .env.example .env
# Set your own secrets in .env (JWT_SECRET needs at least 32 characters)
docker compose up --build
```

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- Observability stack (Prometheus + Grafana): `docker compose --profile observability up --build`

> **Note:** `/actuator/prometheus` is exposed without authentication for local Prometheus scraping. In production, restrict Actuator endpoints to an internal network or require admin authentication.

## REST API (18 Endpoints under `/api/v1`)

| # | Method | Path | Who may call | Success |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/auth/register` | Public | 201 |
| 2 | `POST` | `/auth/login` | Public | 200 |
| 3 | `POST` | `/auth/refresh` | Public | 200 |
| 4 | `GET` | `/players/me` | Any authenticated | 200 |
| 5 | `PATCH` | `/players/me` | `PLAYER` (in good standing) | 200 |
| 6 | `GET` | `/players/{id}` | Any authenticated | 200 |
| 7 | `GET` | `/players` | `MODERATOR`, `SERVICE` | 200 |
| 8 | `POST` | `/matches` | `SERVICE` | 201 / 200 on replay |
| 9 | `GET` | `/matches/{id}` | Any authenticated | 200 |
| 10 | `GET` | `/players/{id}/matches` | Any authenticated | 200 |
| 11 | `GET` | `/leaderboard` | Public | 200 |
| 12 | `GET` | `/leaderboard/players/{id}` | Public | 200 |
| 13 | `POST` | `/moderation/players/{id}/actions` | `MODERATOR`, `SERVICE` | 201 |
| 14 | `GET` | `/moderation/queue` | `MODERATOR` | 200 |
| 15 | `GET` | `/moderation/players/{id}/history` | `MODERATOR`, `SERVICE` | 200 |
| 16 | `POST` | `/appeals` | `PLAYER` (status `BANNED`) | 201 |
| 17 | `GET` | `/appeals` | `MODERATOR` | 200 |
| 18 | `POST` | `/appeals/{id}/decision` | `MODERATOR` | 200 |

## Design Decisions

- **Pure state machine (`AccountStateMachine`):** Keeping account transition rules in a dependency-free Java class allows exhaustive unit testing across all 35 `(action, status)` pairs without Spring or database overhead, while guaranteeing every status change goes through one contract.
- **Database-enforced audit log:** Flyway migration `V2__audit_log_append_only.sql` attaches a `BEFORE UPDATE OR DELETE` PostgreSQL trigger to `moderation_actions`, so even an application bug cannot rewrite or erase moderation history.
- **Confidence guardrail:** Automated `SERVICE` accounts (seeded as `judge-agent` and `telemetry-sim`) are blocked at the service layer from issuing `BAN` actions without a `confidence` score of at least `0.80` (`422 CONFIDENCE_REQUIRED` / `CONFIDENCE_TOO_LOW`), ensuring uncertain cases must be routed through `FLAG` or `NOTE` with `requiresHumanReview = true`.
- **Post-commit Redis sync & atomic rebuild:** Rating changes and ban removals publish Spring application events handled via `@TransactionalEventListener(phase = AFTER_COMMIT)`, preventing rolled-back transactions from leaking into Redis. On startup mismatches, `LeaderboardRebuilder` streams eligible players via keyset pagination into a temporary ZSET and swaps it with `RENAME`.
- **Ordered pessimistic locking for matches:** `POST /matches` sorts participant UUIDs before issuing `SELECT ... FOR UPDATE` (`ORDER BY id`), eliminating deadlocks when concurrent matches share overlapping players, while `externalId` uniqueness makes retries idempotent (`200 OK`).
- **Refresh-token reuse detection:** Refresh tokens are stored as SHA-256 hashes and rotated on each use. Presenting an already-revoked token triggers `REQUIRES_NEW` revocation of all active refresh tokens for that player and returns `401 REFRESH_TOKEN_REUSED`.

## Benchmark

Method (`benchmark/rank-lookup.js`):
1. Seed 1,000,000 players (`scripts/seed-1m-players.sql`) with ratings distributed across 500..2499 (~500 ties per rating) and restart Arena so `LeaderboardRebuilder` populates Redis.
2. Export 10,000 sampled player IDs to `benchmark/player-ids.json`.
3. Run `k6 run -e SOURCE=db benchmark/rank-lookup.js` and `k6 run -e SOURCE=redis benchmark/rank-lookup.js` (15s warmup at 50 VUs, 60s measure at 200 VUs), three times each, reporting the last run alongside `arena_leaderboard_rank_lookup_seconds` from `/actuator/prometheus`.

| Source | p50 | p95 | p99 | Throughput | Notes |
| --- | --- | --- | --- | --- | --- |
| `db` (PostgreSQL `COUNT(*) + 1`) | 15.62 s | 18.01 s | 29.39 s | 12.6 req/s | 2 of 1,127 requests failed (0.17%) |
| `redis` (`ZREVRANK`) | 33.07 ms | 63.53 ms | 90.21 ms | 5,764 req/s | 0 of 432,474 requests failed |

k6 `{phase:measure}` results (200 VUs for 60 s, after a 15 s warm-up at 50 VUs). Each source was run three times; the table shows the third run, because the full summaries of the first two runs were not captured. The Redis path was about 280x faster at p95 and served about 460x the throughput of the DB path. The `db` run misses the script's 2 s p95 sanity threshold, as expected for a `COUNT(*)` over 1,000,000 rows.

Server-side cross-check (`arena_leaderboard_rank_lookup_seconds`, cumulative over all runs on this stack, histogram upper bounds):

| Source | Requests | Mean | p50 | p95 | p99 |
| --- | --- | --- | --- | --- | --- |
| `db` | 3,466 | 1.54 s | <= 1.79 s | <= 2.51 s | <= 2.86 s |
| `redis` | 1,249,847 | 2.9 ms | <= 2.8 ms | <= 5.6 ms | <= 8.4 ms |

The server-side `db` time (about 1.5 s) is far below the k6 latency (about 15 s). The timer starts after the transaction has acquired its connection, so it excludes time spent waiting for one of the 20 Hikari connections while 200 virtual users compete for them. k6 includes that queueing plus network and HTTP overhead.

**Environment:** AMD Ryzen 5 3600 (6 cores / 12 threads), 15.9 GB RAM, Windows 11, Docker Desktop 29.7.2 with 12 vCPUs and 8 GB RAM available to the engine and no per-container limits. Arena, PostgreSQL 17 (default configuration), Redis 7 and the k6 container (`grafana/k6`, run on the compose network) all shared that one Docker VM, so the load generator competed with the services for CPU. The dataset was 1,000,001 players (1,000,000 seeded plus one smoke-test account) across 2,000 distinct ratings. Redis was rebuilt from PostgreSQL at startup in 22.6 s.

## What I'd Do Next

- Add per-IP and per-account rate limiting on authentication and appeal endpoints.
- Replace in-memory post-commit Redis event listeners with a transactional outbox table for guaranteed delivery across crashes.
- Publish moderation and match domain events to a message broker such as Kafka, so other services can consume them instead of polling the API.
