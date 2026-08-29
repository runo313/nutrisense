# Meal Log System API

## Overview

The Meal Log System API owns what users actually ate. It records meals and their constituent food items referencing food and portion IDs from the Nutrition System API and exposes CRUD operations consumed by the meal-logging Experience layer, and on the read side, the Daily Insight and Trend Analysis Process APIs.

## Base URI

| Environment | URL |
|---|---|
| Local | `https://localhost:8081/api/v1/meal-log-sapi` |
| CloudHub Test | `https://meal-log-sys-api-6b2j11.5sc6y6-2.usa-e2.cloudhub.io/api/v1/meal-log-sapi` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check with DB connectivity confirmation |
| `POST` | `/users/{userId}/meals` | Create a meal with one or more food items |
| `GET` | `/users/{userId}/meals` | Retrieve meals for a single date or date range (`consumed_at`-based, timezone-aware) |
| `GET` | `/users/{userId}/meals/{mealId}` | Full detail for one meal including all non-deleted items |
| `PATCH` | `/users/{userId}/meals/{mealId}` | Partially update a meal's type or consumed time |
| `DELETE` | `/users/{userId}/meals/{mealId}` | Soft delete a meal and cascade to its items |
| `POST` | `/users/{userId}/meals/{mealId}/items` | Add item(s) to an existing meal |
| `PATCH` | `/users/{userId}/meals/{mealId}/items/{itemId}` | Partially update a meal item, recomputing quantity if the portion changes |
| `DELETE` | `/users/{userId}/meals/{mealId}/items/{itemId}` | Soft delete a single item |

## Data Model

| Table | Purpose |
|---|---|
| `meals` | One row per logged meal. type, consumed time, logged time |
| `meal_items` | One row per food item within a meal. food/portion reference and computed gram quantity |

Both tables use soft deletes via `deleted_at`.

## Key Design Decisions

- **Timezone-aware date filtering** — `consumed_at` is stored as `TIMESTAMPTZ`, but date-range queries accept an IANA timezone name so a user's "today" reflects their local day boundary, not UTC's.
- **Cascading soft delete** — deleting a meal soft-deletes all of its items in the same operation, keeping the two tables consistent without a hard delete or orphaned rows.

## Security

Client ID / client secret enforcement (service-to-service, internal only).

## Environments

- **Local** — PostgreSQL 16 running in Docker.
- **Test** — Deployed to CloudHub 2.0, connected to a shared AWS RDS PostgreSQL instance (`us-east-1`).

## Dependencies

- `global-api-fragments` — shared error types, security scheme, health check type, timezone enum
- `nutrisense-api-fragments` — `Meal`, `MealItem`, and related create/patch input and response types

## Testing

MUnit coverage across meal and item creation (both input modes), reads (single day, range, empty results), patch and delete flows, and validation/error scenarios including invalid item modes and not-found cases. Run with: