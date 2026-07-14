
CREATE TABLE point_metrics (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    metric_type TEXT NOT NULL,
    value NUMERIC NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    duration_seconds  NUMERIC,              
    source_device TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_point_metrics_identity
        UNIQUE (user_id, metric_type, source_device, recorded_at)
);

CREATE INDEX idx_point_metrics_user_type_time
    ON point_metrics (user_id, metric_type, recorded_at);

CREATE TABLE sleep_sessions (
    id                BIGSERIAL PRIMARY KEY,
    user_id           TEXT NOT NULL,
    stage             TEXT NOT NULL,
    start_time        TIMESTAMPTZ NOT NULL,
    end_time          TIMESTAMPTZ NOT NULL,
    duration_seconds  NUMERIC NOT NULL,
    source_device     TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_sleep_sessions_identity
        UNIQUE (user_id, source_device, start_time, stage)
);

CREATE INDEX idx_sleep_sessions_user_time
    ON sleep_sessions (user_id, start_time);


CREATE TABLE workouts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           TEXT NOT NULL,
    activity_type     TEXT NOT NULL,
    start_time        TIMESTAMPTZ NOT NULL,
    end_time          TIMESTAMPTZ NOT NULL,
    duration_minutes  NUMERIC NOT NULL,
    energy_burned     NUMERIC,              -- nullable; sparsely populated in source data
    source_device     TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_workouts_identity
        UNIQUE (user_id, source_device, start_time, end_time)
);

CREATE INDEX idx_workouts_user_time
    ON workouts (user_id, start_time);

CREATE TABLE workout_statistics (
    id                BIGSERIAL PRIMARY KEY,
    workout_id        BIGINT NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    metric_type       TEXT NOT NULL,
    sum_value         NUMERIC,
    average_value     NUMERIC,
    minimum_value     NUMERIC,
    maximum_value     NUMERIC,
    unit              TEXT NOT NULL
);

CREATE INDEX idx_workout_statistics_workout_id
    ON workout_statistics (workout_id);