# NutriSense

NutriSense is a personal health insights platform that brings wearable biometric data, food logs, and user health context together to produce personalized daily insights and longer-term trend analysis.

Wearable and nutrition data are useful on their own, but they often live in separate systems. NutriSense connects them so that a day's sleep, activity, food intake, and personal goals can be evaluated together rather than treated as unrelated measurements.

The backend is built using the API-led architecture. It consists of independently deployed System, Process, and Experience APIs, with PostgreSQL for data storage, AWS Cognito for authentication, and Splunk for centralized logging.

## Architecture

NutriSense follows MuleSoft's three-layer API-led connectivity pattern.

```text
                      CLIENTS
                         |
                         v
                   EXPERIENCE APIs
                  Personal Dashboard
                         |
                         v
                     PROCESS APIs
                 Daily Insight | Trends
                         |
                         v
                      SYSTEM APIs
      User Profile | Meal Log | Nutrition | Wearable
                         |
                         v
                 PostgreSQL / Data Sources
```

Each layer has a separate responsibility:

| Layer | Responsibility |
|---|---|
| System | Own domain data and expose reusable API contracts |
| Process | Combine data from multiple systems and apply evaluation logic |
| Experience | Authenticate user-facing requests, coordinate downstream operations, and shape responses for the client |

Experience APIs can also call System APIs directly when no Process API orchestration is needed, such as retrieving a user profile or updating a meal.

### How the data moves

A typical Daily Insight request involves several parts of the platform:

1. An Apple Health export is parsed by a Python client and ingested through the Wearable System API.
2. The user logs meals containing food and portion references. The Meal Log System API stores what was consumed.
3. The Nutrition System API resolves those food references against a PostgreSQL catalog populated from USDA FoodData Central.
4. The User Profile System API provides body measurements, goals, health conditions, dietary constraints, preferences, and a calculated daily calorie target.
5. The Daily Insight Process API brings these sources together, evaluates calories, macronutrients, sleep, recovery and activity, and ranks relevant findings.
6. The Personal Dashboard Experience API exposes the result through an authenticated `/me` endpoint.

## APIs

### System APIs

| API | Responsibility |
|---|---|
| [User Profile](system-apis/userprofile-sys-api/README.md) | Profile, goals, conditions, dietary constraints and preferences |
| [Meal Log](system-apis/meal-log-sys-api/README.md) | Meals, food items, portions and consumption timestamps |
| [Nutrition](system-apis/nutrition-sys-api/README.md) | USDA Foundation Foods lookup, nutrients and serving sizes |
| [Wearable](system-apis/wearable-sys-api/README.md) | Apple Health metrics, sleep sessions, workouts and summaries |

### Process APIs

| API | Responsibility |
|---|---|
| [Daily Insight](process-apis/daily-insight-process-api/README.md) | Single-day evaluation, ranked recommendations and combined signals |
| [Trend Analysis](process-apis/trend-analysis-process-api/README.md) | Historical nutrition, activity, sleep and recovery analysis |

The Daily Insight API evaluates a day using reference values from published guidance where appropriate, plus explicitly defined heuristic warning bands. It also checks combinations of signals, such as low calorie intake alongside unusually high activity.

Insight text can be generated through rule-based templates or an LLM. The template engine acts as a fallback when LLM generation is unavailable.

The Trend Analysis API evaluates repeated measurements over longer periods rather than interpreting every metric in isolation.

Detailed calculations, reference values, evaluation thresholds and limitations are documented in the individual Process API READMEs.

### Experience APIs

| API | Responsibility | Status |
|---|---|---|
| [Personal Dashboard](experience-apis/personal-dashboard-exp-api/README.md) | Authenticated access to the user's profile, meals, wearable data, daily insights and trends | Implemented |
| Coach/Provider | A separate experience for providers or coaches working with multiple users | Planned |

The Personal Dashboard Experience API extracts the authenticated user's identity from Cognito claims, calls downstream APIs, and adjusts responses for the client.

One example is meal retrieval: the Meal Log System API stores food references, while the Experience API resolves food names through the Nutrition System API so clients receive meaningful meal descriptions without making additional requests.

## Authentication and API security

NutriSense uses AWS Cognito for user authentication and MuleSoft API Manager for API policy enforcement.

- **User-facing requests:** OAuth 2.0 authorization code flow with PKCE. Cognito issues JWTs, which are validated through API Manager.
- **User identity:** The Experience API extracts the Cognito `sub` claim and passes the corresponding user ID to downstream APIs.
- **Internal APIs:** Client ID and Client Secret Enforcement protects service-to-service access to System and Process APIs.

The Personal Dashboard exposes `/me` resources so clients do not need to supply another user's identifier for their own data.

## Data storage and ingestion

PostgreSQL stores user profiles, meal records, nutrition reference data and wearable measurements.

**Nutrition catalog.** A Python/pandas ETL pipeline imports the USDA FoodData Central Foundation Foods dataset into PostgreSQL. The Nutrition System API provides read-only access to those records.

**Wearable data.** A standalone Python parser processes Apple Health exports into supported metrics, sleep sessions and workouts. The Wearable System API supports idempotent batch ingestion so repeated imports do not create duplicate measurements.

Meal logs reference the nutrition catalog through food and portion identifiers instead of duplicating nutrient records.

## Deployment and CI/CD

The primary development environment runs Mule Enterprise Edition standalone on a server registered with Anypoint Runtime Manager.

GitHub Actions handles secret scanning, Maven builds, MUnit testing and environment-specific deployment using Maven profiles.

| Branch | Purpose |
|---|---|
| `main` | Validated source without automatic deployment |
| `standalone-dev` | Deploy to the registered standalone Mule server |
| `cloudhub-test` | Deploy to CloudHub 2.0 |

### Centralized logging

Mule applications provide structured JSON logs containing application, layer, flow, severity and correlation ID information.

Logs are forwarded to Splunk through the HTTP Event Collector. 

## Repository structure

```text
nutrisense/
├── system-apis/
│   ├── wearable-sys-api/
│   ├── nutrition-sys-api/
│   ├── meal-log-sys-api/
│   ├── userprofile-sys-api/
│   └── notification-sys-api/
├── process-apis/
│   ├── daily-insight-process-api/
│   └── trend-analysis-process-api/
├── experience-apis/
│   ├── personal-dashboard-exp-api/
│   └── coach-provider-exp-api/
├── clients/
│   └── apple-health-parser/
├── db/
│   └── init/
├── .github/
│   └── workflows/
├── docker-compose.yml
└── pom.xml
```

The root Maven POM manages the multi-module project. Each implemented API has its own configuration, RAML specification, Mule flows and tests.

API-specific endpoints, data models and implementation decisions are documented in their respective READMEs.

## Current scope and future work

The Personal Dashboard backend, Daily Insight and Trend Analysis APIs, core System APIs, authentication, centralized logging and development deployment pipeline are implemented.

Areas reserved for further development include:

- **Photo-based meal logging:** A user photographs a meal and receives a short list of likely food matches mapped to Nutrition System API `foodId`s. The user confirms the foods and selects portions through the existing meal-logging flow.
- **Custom insight model:** Develop and integrate a custom model for generating personalized health insights, as an alternative to the existing template-based and LLM generation modes.
- **Additional wearable integrations:** Support data sources beyond Apple Health exports.
- **Coach/Provider experience:** Introduce multi-user access, provider-specific views and the authorization model required for those workflows.

## License

Licensed under the Apache License 2.0 License. See [LICENSE](LICENSE) for details.