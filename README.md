# NutriSense

A personal health insights platform that ingests wearable biometric data and nutrition logs, generate personalized rule-based recommendations, and exposes that through differently shaped consumer interfaces (personal dashboard vs. coach/provider view).

## Architecture

NutriSense follows MuleSoft's API-led connectivity pattern: three layers, each with a distinct responsibility.

```
Experience APIs   → shape data for specific consumers (personal dashboard, coach/provider view)
        ↑
Process APIs      → orchestrate System APIs, apply business/recommendation logic
        ↑
System APIs       → own a single domain's data and expose it through a canonical contract
```

### Direction of flow — a concrete example

1. A wearable export (Apple Health) is parsed by a standalone Python client and Sent to the **Wearable System API**, which stores it.
2. A user logs a meal through the **Meal Log System API**, referencing food and portion IDs.
3. Those food IDs resolve against the **Nutrition System API** around USDA FoodData Central to get nutrient composition.
4. The **User Profile System API** holds the user's goals, conditions, dietary constraints, and computed daily caloric target.
5. **Daily Insight Process API** orchestrates all three System APIs — wearable trends, what was eaten, and the user's goals/conditions — and applies rule-based logic grounded in real guidance (CDC/NIH activity & sleep guidance, USDA Dietary Guidelines, AHA cardiovascular metrics) to produce a recommendation.
6. **Trend Analysis Process API** performs the same kind of orchestration over a longer window.
7. **Personal Dashboard** and **Coach/Provider Experience APIs** shape that output differently depending on who's asking  a single user viewing their own data vs. a coach viewing multiple clients.

## Repository Structure

```
nutrisense/
├── system-apis/
│   ├── wearable-sys-api/        — wearable biometric data 
│   ├── nutrition-sys-api/       — USDA FoodData Central wrapper
│   ├── meal-log-sys-api/        — logged meals and food items 
│   ├── userprofile-sys-api/     — identity, goals, conditions, preferences 
│   └── notification-sys-api/    — alerting 
├── process-apis/
│   ├── daily-insight-process-api/     — orchestration + rule-based recommendations
│   └── trend-analysis-process-api/    — weekly/monthly aggregation
├── experience-apis/
│   ├── personal-dashboard-exp-api/    — single-user dashboard
│   └── coach-provider-exp-api/        — multi-client coach-scoped
├── db/init/                     — numbered Postgres schema files
├── clients/
│   └── apple-health-parser/     — standalone Python ingestion client
├── docker-compose.yml
└── pom.xml                      — root pom
```

Each System API has its own README with endpoint details, data model, and key design decisions. Start there for anything domain-specific:

- [Wearable System API](system-apis/wearable-sys-api/README.md)
- [Nutrition System API](system-apis/nutrition-sys-api/README.md)
- [Meal Log System API](system-apis/meal-log-sys-api/README.md)
- [User Profile System API](system-apis/userprofile-sys-api/README.md)
