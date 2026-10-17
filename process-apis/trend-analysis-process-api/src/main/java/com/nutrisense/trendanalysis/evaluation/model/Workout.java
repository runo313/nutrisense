package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * One workout from the Wearable API. duration is the workout's length and is used
 * instead of end_time, because some rows have an end_time far later than
 * start_time plus duration. durationUnit should be "min"; if it is absent,
 * minutes are assumed. The row's own energy_burned is always null, so energy and
 * distance come from statistics. startTime is kept as the raw string and its
 * first 10 characters give the workout's date. Fields no evaluator reads
 * (user_id, end_time, energy_burned, source_device) are ignored by Gson.
 */
public class Workout {
    @SerializedName("activity_type")
    public String activityType;

    @SerializedName("start_time")
    public String startTime;

    public Double duration;

    @SerializedName("duration_unit")
    public String durationUnit;

    public List<WorkoutStatistic> statistics;
}