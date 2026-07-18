-- NutriSense Meal Log Schema
-- meals and meal_items tables for the Meal Log System API
-- Soft deletes via deleted_at — no hard deletes
-- meal_items.food_id references the Nutrition System API's foods table
-- No FK constraint on food_id — enforced at service level, not DB level (separate API boundary)
-- Two item input modes:
--   Mode A: portionId + portionAmount → quantity_g calculated from food_portions
--   Mode B: quantityG provided directly → portion_id and portion_amount are null

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- meals
-- One row per meal event (breakfast, lunch, dinner, snack)
-- ============================================================
CREATE TABLE meals (
    meal_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       TEXT NOT NULL,
    meal_type     TEXT NOT NULL,
    consumed_at   TIMESTAMPTZ NOT NULL,
    logged_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT chk_meal_type CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack'))
);

CREATE INDEX idx_meals_user_id ON meals (user_id);
CREATE INDEX idx_meals_user_consumed ON meals (user_id, consumed_at);
CREATE INDEX idx_meals_deleted_at ON meals (deleted_at) WHERE deleted_at IS NULL;


CREATE TABLE meal_items (
    item_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meal_id        UUID NOT NULL REFERENCES meals(meal_id) ON DELETE CASCADE,
    food_id        INTEGER NOT NULL,
    portion_id     INTEGER,
    portion_amount NUMERIC,
    quantity_g     NUMERIC NOT NULL,
    logged_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT chk_mode_a_or_b CHECK (
        (portion_id IS NOT NULL AND portion_amount IS NOT NULL)
        OR
        (portion_id IS NULL AND portion_amount IS NULL)
    )
);

CREATE INDEX idx_meal_items_meal_id ON meal_items (meal_id);
CREATE INDEX idx_meal_items_food_id ON meal_items (food_id);
CREATE INDEX idx_meal_items_deleted_at ON meal_items (deleted_at) WHERE deleted_at IS NULL;