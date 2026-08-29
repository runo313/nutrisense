# User Profile System API

## Overview

The User Profile System API owns everything about a user that isn't wearable data, nutrition data, or meal logs: biometrics, health goals, medical conditions, dietary constraints, and app preferences. It computes each user's daily caloric target and provides the full profile picture consumed primarily by the Experience layer and the Daily Insight Process API.

## Base URI

| Environment | URL |
|---|---|
| Local | `https://localhost:8084/api/v1/userprofile-sapi` |
| CloudHub Test | `https://user-profile-sys-api-6b2j11.5sc6y6-4.usa-e2.cloudhub.io/api/v1/userprofile-sapi` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/profiles` | Create a user profile or reactivate a previously soft-deleted one |
| `GET` | `/profiles/{userId}` | Full profile including goals, conditions, dietary constraints, and preferences |
| `PATCH` | `/profiles/{userId}` | Partially update a profile, recomputing caloric target if relevant fields change |
| `DELETE` | `/profiles/{userId}` | Soft delete a profile |
| `GET` / `POST` | `/profiles/{userId}/goals` | List or add health goals |
| `PATCH` | `/profiles/{userId}/goals/{goalId}` | Update a goal |
| `GET` / `POST` | `/profiles/{userId}/conditions` | List or add medical conditions |
| `PATCH` | `/profiles/{userId}/conditions/{conditionId}` | Update a condition |
| `GET` / `POST` | `/profiles/{userId}/constraints` | List or add dietary constraints |
| `DELETE` | `/profiles/{userId}/constraints/{constraintId}` | Hard delete a dietary constraint |
| `GET` / `PATCH` | `/profiles/{userId}/preferences` | Retrieve or upsert user preferences |

## Data Model

| Table | Purpose |
|---|---|
| `users` | Core identity and biometrics; computed `daily_caloric_target` |
| `user_goals` | One-to-many health goals, with a single designated primary goal |
| `user_conditions` | One-to-many medical conditions and their management status |
| `dietary_constraints` | One-to-many allergies, intolerances, and preferences |
| `user_preferences` | One-to-one insight, metric, and meal-timing preferences |


## Key Design Decisions

- **Server-computed caloric target** — `daily_caloric_target` is calculated using the Mifflin-St Jeor equation from date of birth, biological sex, height, weight, and activity baseline. It is automatically recomputed whenever any of its underlying inputs change via `PATCH`.
- **Soft-delete reactivation** — since `user_id` is a permanent identifier from the authenticaton service, re-creating a profile for a previously soft-deleted `userId` reactivates the existing row (clearing `deleted_at` and repopulating fields) rather than rejecting the request or creating a duplicate.
- **Single-primary-goal invariant** — setting a new goal as primary automatically demotes any existing primary goal within the same transaction, so a user never has more than one active primary goal.
- **Upsert preferences** — `PATCH /profiles/{userId}/preferences` inserts a preferences row on first use and updates it thereafter, avoiding the need for a separate creation endpoint for a strictly one-to-one resource.

## Security

Client ID / client secret enforcement (service-to-service, internal only).

## Environments

- **Local** — PostgreSQL 16 running in Docker.
- **Test** — Deployed to CloudHub 2.0, connected to a shared AWS RDS PostgreSQL instance (`us-east-1`).

## Dependencies

- `global-api-fragments` — shared error types, security scheme, health check type
- `nutrisense-api-fragments` — `UserProfile`, `UserGoal`, `UserCondition`, `DietaryConstraint`, `UserPreferences`, and related input types

## Testing

MUnit coverage across profile creation (including soft-delete reactivation), full profile retrieval, patch flows for profile/goals/conditions, primary-goal demotion, constraint creation and deletion, preferences upsert (both insert and update paths), and common error scenarios. Run with: