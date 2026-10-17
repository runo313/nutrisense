package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * One workout from the Wearable API. duration is the workout's length and is
 * used in place of end_time, because some rows have an end_time far later than
 * start_time plus duration. durationUnit should be "min"; if it is absent the
 * duration is assumed to be minutes.
 */
public class Workout {
    @SerializedName("activity_type")
    public String activityType;

    @SerializedName("start_time")
    public String startTime;

    public Double duration;

    @SerializedName("duration_unit")
    public String durationUnit;

    @SerializedName("source_device")
    public String sourceDevice;
}