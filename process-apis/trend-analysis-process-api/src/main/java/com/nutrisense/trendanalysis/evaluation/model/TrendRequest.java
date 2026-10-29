package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;

/**
 * The single input document Mule sends to TrendEvaluationService, built from
 * vars.window, vars.userProfile, vars.mealStatus and vars.wearableStatus.
 */
public class TrendRequest {

    public Window window;
    public Profile profile;

    /** Meals in the window, each with consumedAt kept so days can be separated. */
    public List<Meal> meals;

    /** Resolved foods for those meals as a flat list */
    public List<Food> foods;

    /** Sleep sessions fetched from the day before the window start. */
    public List<SleepSession> sleepSessions;

    /** Point metrics: resting_heart_rate, hrv and step_count readings. */
    public List<PointMetric> pointMetrics;

    /** Workouts, each with its statistics array intact. */
    public List<Workout> workouts;

    /** The 30-day baseline entries for resting_heart_rate and hrv; null at window 30. */
    public List<MetricSummary> baselineSummaries;

    /**
     * The evaluated window. days must be 7, 14 or 30. start is the first calendar
     * day as yyyy-MM-dd, kept as a string.
     */
    public static class Window {
        public int days;
        public String start;
    }

    /**
     * The profile values the evaluators use. Mule resolves these before the call:
     * age in whole years from the date of birth, the primary goal's type and
     * direction, only active conditions, and dietary constraint values reduced to
     * plain strings. Any value can be null, except that a null age is treated as
     * out of scope.
     */
    public static class Profile {
        public Double weightKg;
        public Double dailyCaloricTarget;
        public String biologicalSex;
        public Integer age;
        public String activityBaseline;
        public Double targetSleepDurationHrs;
        public String goalType;
        public String goalDirection;
        public List<String> conditions;
        public List<String> constraints;
    }
}