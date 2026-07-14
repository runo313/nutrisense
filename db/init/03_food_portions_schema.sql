-- NutriSense Nutrition Schema
-- food_portions table seeded from USDA FDC food_portion.csv
-- Only portions for foods already in the foods table are seeded (FK enforced)
-- Nutrient values in foods table are per 100g; portions provide real-world serving context

CREATE TABLE food_portions (
    id                      INTEGER PRIMARY KEY,  -- USDA's own portion ID, not auto-generated
    fdc_id                  INTEGER NOT NULL REFERENCES foods(fdc_id) ON DELETE CASCADE,
    amount                  NUMERIC,              -- quantity of the measure unit (e.g. 1, 0.5, 2)
    measure_unit_description TEXT,               -- denormalized from measure_unit.csv (e.g. 'cup', 'tablespoon')
    gram_weight             NUMERIC NOT NULL,     -- actual weight in grams for this portion
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for fast lookup by food
CREATE INDEX idx_food_portions_fdc_id ON food_portions (fdc_id);
