# User Profile System API

Owns the user information needed to personalize NutriSense: body measurements, health goals, conditions, dietary constraints and application preferences.

It also calculates the user's daily calorie target, which the Daily Insight and Trend Analysis Process APIs use when evaluating nutrition intake.

## Endpoints

Base path: `/api/v1/userprofile-sapi`

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/profiles` | Create or reactivate a user profile |
| GET | `/profiles/{userId}` | Retrieve the complete profile |
| PATCH | `/profiles/{userId}` | Update profile information |
| DELETE | `/profiles/{userId}` | Soft delete a profile |
| GET / POST | `/profiles/{userId}/goals` | List or create goals |
| PATCH | `/profiles/{userId}/goals/{goalId}` | Update a goal |
| GET / POST | `/profiles/{userId}/conditions` | List or create conditions |
| PATCH | `/profiles/{userId}/conditions/{conditionId}` | Update a condition |
| GET / POST | `/profiles/{userId}/constraints` | List or add dietary constraints |
| DELETE | `/profiles/{userId}/constraints/{constraintId}` | Delete a dietary constraint |
| GET / PATCH | `/profiles/{userId}/preferences` | Retrieve or update preferences |

## Data model

| Table | Contents |
|---|---|
| `users` | Identity, body measurements and calculated calorie target |
| `user_goals` | Health goals, target values, active state and primary-goal designation |
| `user_conditions` | Health conditions and management status |
| `dietary_constraints` | Allergies, intolerances and dietary preferences |
| `user_preferences` | Insight generation, metric preferences and other application settings |

## Key design decisions

**Server-computed calorie targets.** The daily calorie target is calculated using the Mifflin-St Jeor equation, based on age, biological sex, height, weight and activity baseline.

Updates to any of those inputs trigger recalculation. Clients cannot independently assign an arbitrary calculated target.

**Profile reactivation.** User IDs come from the authentication service and remain stable. If a previously deleted user creates a profile again, the existing record is reactivated rather than creating a second profile with the same identity.

**Single primary goal.** A user can maintain several goals, but only one can be designated as primary. Setting a new primary goal automatically clears the previous designation within the same transaction.

**Goal and condition lifecycle.** Goals and conditions have their own identifiers and active states. Their types remain immutable after creation, while supported management fields can be updated.

This lets historical records remain available without treating inactive goals or conditions as current user context.

**Preferences upsert.** Preferences use a one-to-one relationship with the profile. The PATCH operation creates the preferences record if one does not exist and updates it otherwise.

## Security

Internal service-to-service API protected by Client ID and Client Secret Enforcement.

The Experience API obtains the user identifier from the authenticated Cognito session before calling this API.