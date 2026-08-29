# Nutrition System API

## Overview

The Nutrition System API is a read-only System API that answers a single question for the rest of the NutriSense architecture: *"what's in this food?"* It's backed by a Postgres database seeded once from the [USDA FoodData Central](https://fdc.nal.usda.gov/) Foundation Foods dataset, and exposes search and nutrient-lookup endpoints consumed by the process APIS. 

## Base URI

| Environment | URL |
|---|---|
| Local | `https://localhost:8082/api/v1/nutrition-sapi` |
| CloudHub Test | `https://nutrition-sys-api-6b2j11.5sc6y6-2.usa-e2.cloudhub.io/api/v1/nutrition-sapi` |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check |
| `GET` | `/foods/search` | Fuzzy search for foods by name, returns top K matches |
| `GET` | `/foods/{foodId}` | Full food record — metadata, all 16 canonical nutrients, allergens, diet labels. Optional `portions=true` provides real-world serving sizes |
| `GET` | `/foods/{foodId}/nutrients` | Filtered nutrient profile for a food, returning only the requested fields |

## Data Model

| Table | Purpose |
|---|---|
| `foods` | USDA Foundation Foods records — metadata plus 16 canonical nutrient columns per 100g |
| `food_portions` | Real-world serving size options (e.g. "1 cup") with gram-weight equivalents |

Sourced from [USDA FoodData Central](https://fdc.nal.usda.gov/), Foundation Foods dataset.

## Key Design Decisions

- **Fuzzy search** — `GET /foods/search` uses PostgreSQL full-text search (`to_tsvector`/`plainto_tsquery`) with `pg_trgm` trigram matching for typo-tolerant lookups.
- **Dynamic field selection** — `GET /foods/{foodId}/nutrients` accepts a `fields` query parameter which are mapped to their snake_case Postgres columns via a reusable DataWeave module before being interpolated into the query.

## Security

Client ID / client secret enforcement (service-to-service, internal only).

## Environments

- **Local** — PostgreSQL 16 running in Docker, seeded via a one-time Python/pandas ETL pipeline.
- **Test** — Deployed to CloudHub 2.0, connected to a shared AWS RDS PostgreSQL instance (`us-east-1`).

## Dependencies

- `global-api-fragments` — shared error types, security scheme, health check type
- `nutrisense-api-fragments` — `Food`, `FoodPortion`, `NutrientProfile`, `NutrientField` enum, and related search/response types

## Testing

MUnit coverage across search (with and without results), full food profile retrieval (found and not found), and filtered nutrient retrieval across single and multiple requested fields. Run with:


