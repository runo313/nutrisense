# Personal Dashboard Experience API

The client-facing API for NutriSense's personal dashboard. It gives an authenticated user access to their profile, goals, conditions, meal logs, wearable data, daily insights and trends without exposing the System and Process API structure to the client.

The main responsibility here is routing requests to the right API and shaping the response for the dashboard. Health evaluations, nutrient calculations and trend analysis stay in the Process APIs.

## Authentication and user context

Requests are authenticated through AWS Cognito using OAuth 2.0. The MuleSoft API Manager validates the JWT before the request reaches the application.

The Experience API extracts the user's Cognito `sub` claim and uses it as the user identifier when calling downstream APIs. Clients use `/me` endpoints rather than supplying a user ID in the URL.

This keeps the identity used for profile, meal and insight operations tied to the authenticated session.

## Endpoints

Base path: `https://nutrisense-api-dev.ogheneruno313labs.com/api/v1/personal-dashboard-xapi`

Every path sits under `/me`. The user is always taken from the JWT, never from the URL.

### Insights

| Method | Path | Purpose |
|---|---|---|
| GET | `/me/insights/daily?date=` | Daily insight. `date` defaults to today in the user's timezone. Today's insight returns `409` before 6 PM local. |
| POST | `/me/insights/daily/regenerate?date=` | Forces a fresh daily insight. Same 6 PM restriction. |
| GET | `/me/insights/daily/preview?mode=` | Generates the text in `template` or `llm` mode without changing the saved preference. Not time-restricted. |
| GET | `/me/insights/trend?window=&asOf=` | Trend for a 7, 14 or 30-day window ending on `asOf`. Defaults to yesterday in the user's timezone. |
| POST | `/me/insights/trend/regenerate?window=&asOf=` | Forces a fresh trend run. |

### Meals

| Method | Path | Purpose |
|---|---|---|
| GET | `/me/meals?date=` | Meals for one day. Empty array when nothing is logged. |
| GET | `/me/meals?from=&to=` | Meals across a date range. |
| GET | `/me/meals/{mealId}` | One meal, with food names added to each item. |
| POST | `/me/meals` | Logs a meal. Each item takes `quantityG`, or `portionId` with `portionAmount`. |
| PATCH | `/me/meals/{mealId}` | Updates meal details such as type, time or notes. |
| DELETE | `/me/meals/{mealId}` | Deletes a meal. |
| POST | `/me/meals/{mealId}/items` | Adds an item to a meal. |
| DELETE | `/me/meals/{mealId}/items/{itemId}` | Removes an item from a meal. |

### Food search

| Method | Path | Purpose |
|---|---|---|
| GET | `/foods/search?query=&limit=` | Searches foods. Returns ID, name, category and portions only, with no nutrient data. |
| GET | `/foods/{foodId}?portions=` | Returns food with nutrient data and optional portions |
| GET | `/foods/{foodId}/nutrients?fields=` | Returns specified nutrient data |


### Profile

| Method | Path | Purpose |
|---|---|---|
| POST | `/me/profile` | Creates a profile. |
| GET | `/me/profile` | The user's profile. |
| PATCH | `/me/profile` | Updates the profile. `dailyCaloricTarget` is calculated and rejected if sent. |
| DELETE | `/me/profile` | Deletes a profile. |
| GET | `/me/profile/goals` | All goals, active and inactive. |
| POST | `/me/profile/goals` | Adds a goal. |
| PATCH | `/me/profile/goals/{goalId}` | Updates a goal, including deactivating it with `isActive`. |
| GET | `/me/profile/conditions` | All conditions, active and inactive. |
| POST | `/me/profile/conditions` | Adds a condition. Must be one of `anemia`, `hypertension`, `high_cholesterol`, `kidney_disease`, `type2_diabetes`, `prediabetes`. |
| PATCH | `/me/profile/conditions/{conditionId}` | Updates a condition, including deactivating it. |
| GET | `/me/profile/constraints` | All dietary constraints, active and inactive. |
| POST | `/me/profile/constraints` | Adds a constraint. Must be one of `vegetarian`, `vegan`, `dairy`. |
| GET | `/me/profile/preferences` | Preferences, including `insightGenerationMode` and `timezone`. |
| PATCH | `/me/profile/preferences` | Updates preferences. This is how a mode chosen through preview is saved. |

Responses: `200`, `201` (created), `400` (invalid request), `401` (missing or invalid token), `404` (record not found), `409` (today's insight requested before 6 PM), `500` (anything unexpected).

## Features

| Area | What the Experience API handles |
|---|---|
| Profile | Create, retrieve and update the user's profile |
| Goals and conditions | Manage the user's goals and health conditions; return active records for dashboard use |
| Meal logging | Create, retrieve, update and delete meals and meal items |
| Wearable data | Provide access to the user's recorded activity, sleep and other wearable metrics |
| Daily insights | Expose the Daily Insight Process API's daily evaluation, regeneration and preview functionality |
| Trends | Expose the Trend Analysis Process API's historical analysis |

The Experience API does not independently evaluate calories, nutrient targets, recovery or trends. Those rules belong to their respective Process APIs.

## Response shaping

Most requests involve forwarding an authenticated request downstream and returning the result in a form the dashboard can use.

Some operations need additional processing:

- **User Ids:** Removes redundant fields from the downstream response, including repeated user identifiers. 
- **Meal retrieval:** Enriches individual meal items with food names from the Nutrition System API.
- **Goals and conditions:** Filters inactive entries from dashboard-facing results.

## Example: retrieving a meal with food names

`GET /me/meals/{mealId}`

The Meal Log System API stores meal items using food IDs and portion references. Those identifiers are useful for keeping meal records consistent, but they are not particularly useful to a client displaying a meal.

For an individual meal request, the Experience API retrieves the meal and resolves the food names through the Nutrition System API.

An illustrative response:

```json
{
  "mealId": "9b418a0b-4a8f-4ec9-a82e-d25e5f75f101",
  "mealType": "breakfast",
  "consumedAt": "2026-11-18T08:30:00Z",
  "loggedAt": "2026-11-18T08:35:12Z",
  "items": [
    {
      "itemId": "f21e5a0e-979f-43d0-987e-6fe07a721601",
      "foodId": "334536",
      "name": "Rolled oats",
      "portionId": "portion-001",
      "portionAmount": 1,
      "quantityG": 80
    },
    {
      "itemId": "c311b8f2-75a4-4a5f-a5c2-58a593b86202",
      "foodId": "31960",
      "name": "Banana, raw",
      "portionId": "portion-002",
      "portionAmount": 1,
      "quantityG": 118
    }
  ]
}
```

The addition is `name`. The meal record still owns the items, quantities and timestamps; the Nutrition System API supplies the food information.

If a food name cannot be resolved, the item can still be returned with `name: null`. A missing food name should not make the entire meal unavailable.

## Daily insights and trends

The dashboard exposes two different kinds of analysis:

- **Daily Insight:** Evaluates a selected day using the user's profile, meal data and wearable measurements. Returns dimension statuses, recommendations and cross-signal insights.
- **Trend Analysis:** Examines measurements and nutrition over longer periods, where patterns and repeated behavior matter more than a single day's values.

The Experience API forwards these requests to their corresponding Process APIs rather than implementing the analysis again.

Daily Insight also has a same-day restriction: the dashboard cannot request the current day's insight before 6 PM in the user's configured timezone. This avoids evaluating an incomplete day against full-day targets.

See the [Daily Insight Process API](../../process-apis/daily-insight-process-api/README.md) and [Trend Analysis Process API](../../process-apis/trend-analysis-process-api/README.md) for their evaluation logic.

## Error handling

Errors from downstream APIs are translated into appropriate client-facing responses.

Expected errors, such as invalid requests, missing records and duplicate resources, retain their HTTP meaning and useful error details. Unexpected failures return `500`.

Authentication and authorization are enforced at the API gateway.

## Logging

Application events use structured JSON logging with the application name, layer, flow, environment, log level and correlation ID.

The correlation ID is propagated across API calls so that a request can be traced from the Experience API into the relevant Process and System APIs.

In Splunk, the request can be located using:

```spl
index="nutrisense-dev" message.correlationId="YOUR_CORRELATION_ID"
```