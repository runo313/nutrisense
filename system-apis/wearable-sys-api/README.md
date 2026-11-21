# Wearable System API

Stores wearable measurements imported from Apple Health, including point-in-time metrics, sleep sessions and workouts.

Apple Health exports are parsed by a Python ingestion pipeline before being submitted to this API. The Daily Insight and Trend Analysis Process APIs use the stored measurements to evaluate individual days and longer-term patterns.

## Endpoints

Base path: `/api/v1/wearables-sapi`

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/wearable-data/{userId}` | Batch ingest metrics, sleep sessions and workouts |
| GET | `/wearable-data/{userId}` | Retrieve raw measurements using optional filters |
| GET | `/wearable-data/{userId}/summary` | Aggregate measurements over a date window |

Raw-data queries support filters for metric type, sleep stage, workout activity type, date range and pagination.

## Data model

| Table | Contents |
|---|---|
| `point_metrics` | Timestamped values such as heart rate, steps and body mass |
| `sleep_sessions` | Sleep intervals and their recorded stages |
| `workouts` | Workout sessions, types and timestamps |
| `workout_statistics` | Measurements associated with individual workouts |

## Key design decisions

**Idempotent ingestion.** The same Apple Health export can be submitted more than once without duplicating records. PostgreSQL unique constraints identify existing measurements, and `INSERT ... ON CONFLICT DO NOTHING` skips them.

The ingestion response reports inserted and skipped counts for each data type.

**Transactional batch writes.** A batch is inserted within a database transaction. If part of the operation fails, the transaction is rolled back rather than leaving a partially imported batch.

**Raw records and aggregated summaries.** The API provides both individual measurements and summarized values over a requested period. This avoids requiring every Process API to retrieve large sets of raw records for simple aggregations.

**Flexible filtering.** Optional request parameters are translated into database query conditions using DataWeave. Only relevant filters are included, so clients can request specific measurements without retrieving unrelated wearable data.

## Security

Internal service-to-service API protected by Client ID and Client Secret Enforcement. It does not authenticate end users directly; that responsibility belongs to the Experience layer.