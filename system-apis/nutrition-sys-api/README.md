# Nutrition System API

A read-only food catalog backed by the USDA FoodData Central Foundation Foods dataset.

It answers two questions for the rest of NutriSense: what a food is, and what nutrients it contains.

The Meal Log Experience functionality uses it to resolve food names and serving options. The Daily Insight and Trend Analysis Process APIs use its nutrient values to calculate intake from logged meals.

## Endpoints

Base path: `/api/v1/nutrition-sapi`

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| GET | `/foods/search` | Search foods by name, including approximate matches |
| GET | `/foods/{foodId}` | Retrieve a food's metadata and nutrient profile |
| GET | `/foods/{foodId}/nutrients` | Retrieve selected nutrient values |

`GET /foods/{foodId}` supports `portions=true` to include available serving sizes and their gram equivalents.

The nutrient endpoint accepts a `fields` parameter so callers can request only the values they need.

## Data source and model

The database is populated through a one-time Python/pandas ETL pipeline using the [USDA FoodData Central](https://fdc.nal.usda.gov/) Foundation Foods dataset.

| Table | Contents |
|---|---|
| `foods` | Food metadata and 16 canonical nutrient values per 100 g |
| `food_portions` | Serving descriptions and corresponding gram weights |

Nutrients are stored per 100 g. Consumed quantities are handled by the calling layer rather than modifying the catalog's nutrient values.

## Key design decisions

**Fuzzy food search.** Food lookup combines PostgreSQL full-text search (`to_tsvector` and `plainto_tsquery`) with `pg_trgm` similarity matching. This allows approximate matches rather than requiring an exact food description.

**Selective nutrient retrieval.** The nutrient endpoint accepts a set of requested fields. A reusable DataWeave mapping translates supported nutrient names into database columns, allowing Process APIs to retrieve only the nutrients relevant to a calculation.

**Single nutrition reference.** Meal records store food IDs instead of duplicating descriptions and nutrient values. This keeps food definitions separate from consumption records and allows both Process APIs to use the same nutrient source.

**Read-only access.** The API does not expose food creation or update operations. Food data is populated through the ETL process rather than individual client requests.

## Known limitations

The USDA Foundation Foods import does not currently populate the allergen and dietary-label fields used for strict dietary constraint evaluation.

As a result, Daily Insight cannot reliably detect food-level allergen or dietary violations, even when the user's profile contains dietary constraints.

## Security

Internal service-to-service API protected by Client ID and Client Secret Enforcement.