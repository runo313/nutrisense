package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * One per-workout statistic from the Wearable API, e.g. active_energy_burned or
 * distance_walking_running. Energy and distance carry their total in sumValue,
 * with unit giving Cal or mi/km. Heart rate carries averageValue, minimumValue
 * and maximumValue instead. Fields that do not apply to a statistic are null.
 * Heart rate is mapped but not used to classify intensity.
 */
public class WorkoutStatistic {
    @SerializedName("metric_type")
    public String metricType;

    public String unit;

    @SerializedName("sum_value")
    public Double sumValue;

    @SerializedName("average_value")
    public Double averageValue;

    @SerializedName("minimum_value")
    public Double minimumValue;

    @SerializedName("maximum_value")
    public Double maximumValue;
}