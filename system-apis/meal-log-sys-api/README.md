# Meal Log System API

Records what users eat and when. Each meal contains one or more items referencing foods and portions from the Nutrition System API.

The API owns meal records. The Experience API handles meal logging for the client, while the Daily Insight and Trend Analysis Process APIs read the logged meals to calculate nutrition intake over a day or date range.

## Endpoints

Base path: `/api/v1/meal-log-sapi`

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/users/{userId}/meals` | Create a meal with food items |
| GET | `/users/{userId}/meals` | Retrieve meals for a date or date range |
| GET | `/users/{userId}/meals/{mealId}` | Retrieve a meal and its non-deleted items |
| PATCH | `/users/{userId}/meals/{mealId}` | Update meal type or consumption time |
| DELETE | `/users/{userId}/meals/{mealId}` | Soft delete a meal and its items |
| POST | `/users/{userId}/meals/{mealId}/items` | Add items to an existing meal |
| PATCH | `/users/{userId}/meals/{mealId}/items/{itemId}` | Update an item or its portion |
| DELETE | `/users/{userId}/meals/{mealId}/items/{itemId}` | Soft delete an item |

## Data model

Two PostgreSQL tables hold the meal records:

| Table | Contents |
|---|---|
| `meals` | User ID, meal type, consumption time, logging time and deletion time |
| `meal_items` | Food ID, portion ID, portion amount, gram quantity and deletion time |

Both tables use `deleted_at` for soft deletion. Deleted meals and items are excluded from normal reads.

## Key design decisions

**Timezone-aware meal retrieval.** Consumption timestamps are stored as PostgreSQL `TIMESTAMPTZ`, but date filtering uses the user's IANA timezone. A request for November 18 in `America/New_York` retrieves meals belonging to that local day, even when the UTC date differs.

**Cascading soft deletes.** Deleting a meal also marks its items as deleted within the same operation. Records remain in the database without appearing in subsequent reads.

**Portion-based quantities.** Meal items reference food and portion identifiers rather than storing duplicate food descriptions. When a portion changes, the corresponding gram quantity is recalculated.

**Separation of meal and nutrition data.** This API records what was consumed. Food names and nutrient values come from the Nutrition System API, allowing the same food catalog to support meal logging and both Process APIs.

## Security

Internal service-to-service API protected by MuleSoft API Manager Client ID and Client Secret Enforcement. User-facing authentication is handled by the Experience API.