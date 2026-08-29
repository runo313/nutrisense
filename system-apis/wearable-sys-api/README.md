# Wearable System API

## Overview
The Wearable System API owns all wearable biometric data, point-in-time metrics (heart rate, steps, body mass, etc.), sleep sessions, and workouts with nested statistics ingested from an Apple Health export via a Python parser. This API is designed for reuse by multiple Process APIs (Daily Insight, Trend Analysis).

| Environment | URL |
|---|---|
| Local | `https://localhost:8083/api/v1/wearables-sapi` |
| CloudHub Test | `https://wearable-sys-api-6b2j11.5sc6y6-2.usa-e2.cloudhub.io/api/v1/wearables-sapi` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/wearable-data/{userId}` | Batch ingest of point metrics, sleep sessions, and workouts. Idempotent. |
| `GET` | `/wearable-data/{userId}` | Query raw wearable data with optional filters (`dataType`, `metricType`, `stage`, `activityType`, `date range`, `pagination`) |
| `GET` | `/wearable-data/{userId}/summary` | Aggregated summary of wearable data over a date window |

## Data Model

| Table | Purpose |
|---|---|
| `point_metrics` | Time-series point values (heart rate, steps, body mass, etc.) |
| `sleep_sessions` | Interval-shaped sleep stage records |
| `workouts` | Workout session data |
| `workout_statistics` | Per-metric aggregate statistics nested under a workout |

## Key Design Decisions
- **Idempotent ingest** — `INSERT ... ON CONFLICT DO NOTHING` against composite unique constraints (e.g. `user_id + metric_type + source_device + recorded_at`) means the same payload can be sent multiple times without creating duplicates. Inserted vs. skipped counts are returned per data type.
- **Transactional batch writes** — ingest wraps all inserts in a single transaction (Try scope), so a failure partway through rolls back the entire batch rather than leaving partial data.
- **Dynamic, filterable queries** — the raw and summary GET endpoints support optional filters (`metricType`, `stage`, `activityType`, date ranges) built using dynamic SQL construction in DataWeave.

## Security

Client ID / client secret enforcement (service-to-service, internal only). No user-facing OAuth2 at this layer — that's handled by the Experience API tier.

## Environments

- **Local** — PostgreSQL 16 running in Docker via `docker-compose.yml` at the repo root.
- **Test** — Deployed to CloudHub 2.0, connected to a shared AWS RDS PostgreSQL instance (`us-east-1`).

## Dependencies

- `global-api-fragments` — shared error types, security scheme, pagination trait, health check type
- `nutrisense-api-fragments` — `PointMetric`, `SleepSession`, `Workout`, `WorkoutStatistics`, and related response/summary types

## Testing

MUnit coverage across ingest happy paths, idempotency (duplicate-send) behavior, transaction rollback on DB failure, and query/summary endpoints across all data types and filter combinations. Run with:
