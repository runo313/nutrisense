-- NutriSense Nutrition Schema
-- foods table seeded from USDA FDC Foundation Foods dataset
-- Nutrient values are per 100g unless serving_size_g is specified

CREATE TABLE foods (
    id                  BIGSERIAL PRIMARY KEY,
    fdc_id              INTEGER NOT NULL UNIQUE,
    food_name           TEXT NOT NULL,
    category            TEXT,
    source              TEXT NOT NULL DEFAULT 'USDA_FDC_FOUNDATION',
    serving_size_g      NUMERIC NOT NULL DEFAULT 100,
    portion_description TEXT,

    -- Core macronutrients (USDA Dietary Guidelines)
    energy_kcal         NUMERIC,
    protein_g           NUMERIC,
    total_fat_g         NUMERIC,
    saturated_fat_g     NUMERIC,
    carbohydrates_g     NUMERIC,
    dietary_fiber_g     NUMERIC,
    sugars_g            NUMERIC,

    -- Key minerals (AHA/CDC guidance)
    sodium_mg           NUMERIC,
    potassium_mg        NUMERIC,
    calcium_mg          NUMERIC,
    iron_mg             NUMERIC,
    magnesium_mg        NUMERIC,
    zinc_mg             NUMERIC,

    -- Key vitamins (AHA/CDC guidance)
    vitamin_c_mg        NUMERIC,
    vitamin_d_mcg       NUMERIC,
    vitamin_b12_mcg     NUMERIC,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for food name search (case-insensitive)
CREATE INDEX idx_foods_name ON foods USING gin(to_tsvector('english', food_name));

-- Index for category filtering
CREATE INDEX idx_foods_category ON foods (category);

-- Index for source filtering (future-proofs for SR Legacy or other datasets)
CREATE INDEX idx_foods_source ON foods (source);
