
-- ENUM TYPES

CREATE TYPE biological_sex_enum AS ENUM (
  'male', 'female', 'prefer_not_to_say'
);

CREATE TYPE activity_baseline_enum AS ENUM (
  'sedentary', 'lightly_active', 'moderately_active', 'very_active', 'athlete'
);

CREATE TYPE goal_type_enum AS ENUM (
  'weight_management', 'athletic_performance', 'chronic_condition_management',
  'general_wellness', 'sleep_improvement', 'energy_optimization'
);

CREATE TYPE goal_direction_enum AS ENUM (
  'lose', 'gain', 'maintain', 'improve'
);

CREATE TYPE condition_type_enum AS ENUM (
  'type2_diabetes', 'prediabetes', 'hypertension', 'high_cholesterol',
  'anemia', 'celiac_disease', 'ibs', 'gerd', 'kidney_disease'
);

CREATE TYPE management_status_enum AS ENUM (
  'monitoring', 'diet_controlled', 'medication_managed', 'in_remission'
);

CREATE TYPE constraint_type_enum AS ENUM (
  'allergy', 'intolerance', 'preference'
);

CREATE TYPE constraint_value_enum AS ENUM (
  'gluten', 'dairy', 'nuts', 'shellfish', 'eggs', 'soy',
  'meat', 'pork', 'alcohol', 'vegetarian', 'vegan',
  'halal', 'kosher', 'gluten_free'
);

CREATE TYPE severity_enum AS ENUM (
  'strict', 'moderate', 'preferred'
);

CREATE TYPE insight_frequency_enum AS ENUM (
  'daily', 'weekly', 'both'
);

CREATE TYPE units_system_enum AS ENUM (
  'metric', 'imperial'
);

CREATE TYPE wearable_metric_enum AS ENUM (
  'heart_rate', 'sleep', 'steps', 'hrv', 'active_energy'
);

CREATE TYPE nutrition_metric_enum AS ENUM (
  'protein', 'carbohydrates', 'sodium', 'iron', 'energy_kcal'
);


CREATE TABLE users (
  user_id               VARCHAR                  NOT NULL PRIMARY KEY,
  name                  VARCHAR                  NOT NULL,
  email                 VARCHAR                  NOT NULL UNIQUE,
  date_of_birth         DATE                     NOT NULL,
  biological_sex        biological_sex_enum      NOT NULL,
  height_cm             DECIMAL                  NOT NULL,
  weight_kg             DECIMAL                  NOT NULL,
  activity_baseline     activity_baseline_enum   NOT NULL,
  daily_caloric_target  INTEGER,
  deleted_at            TIMESTAMPTZ,
  created_at            TIMESTAMPTZ              NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ              NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);



CREATE TABLE user_goals (
  goal_id          UUID                   NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          VARCHAR                NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
  goal_type        goal_type_enum         NOT NULL,
  goal_direction   goal_direction_enum,
  target_value     DECIMAL,
  target_unit      VARCHAR,
  is_primary       BOOLEAN                NOT NULL DEFAULT false,
  is_active        BOOLEAN                NOT NULL DEFAULT true,
  set_at           TIMESTAMPTZ            NOT NULL DEFAULT now(),
  achieved_at      TIMESTAMPTZ
);

CREATE INDEX idx_user_goals_user_id ON user_goals(user_id);



CREATE TABLE user_conditions (
  condition_id       UUID                     NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            VARCHAR                  NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
  condition_type     condition_type_enum      NOT NULL,
  management_status  management_status_enum   NOT NULL,
  diagnosed_at       DATE,
  notes              TEXT,
  is_active          BOOLEAN                  NOT NULL DEFAULT true,
  created_at         TIMESTAMPTZ              NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ              NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_conditions_user_id ON user_conditions(user_id);

-- ============================================================

CREATE TABLE dietary_constraints (
  constraint_id     UUID                    NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           VARCHAR                 NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
  constraint_type   constraint_type_enum    NOT NULL,
  constraint_value  constraint_value_enum   NOT NULL,
  severity          severity_enum           NOT NULL,
  is_active         BOOLEAN                 NOT NULL DEFAULT true,
  created_at        TIMESTAMPTZ             NOT NULL DEFAULT now()
);

CREATE INDEX idx_dietary_constraints_user_id ON dietary_constraints(user_id);

-- ============================================================

CREATE TABLE user_preferences (
  preference_id                UUID                      NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                      VARCHAR                   NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE RESTRICT,
  insight_frequency            insight_frequency_enum    NOT NULL DEFAULT 'daily',
  preferred_wearable_metrics   wearable_metric_enum[]    DEFAULT '{}',
  preferred_nutrition_metrics  nutrition_metric_enum[]   DEFAULT '{}',
  typical_breakfast_time       TIME,
  typical_lunch_time           TIME,
  typical_dinner_time          TIME,
  typical_sleep_time           TIME,
  target_sleep_duration_hrs    DECIMAL,
  units_system                 units_system_enum         NOT NULL DEFAULT 'metric',
  created_at                   TIMESTAMPTZ               NOT NULL DEFAULT now(),
  updated_at                   TIMESTAMPTZ               NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);