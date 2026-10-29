package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * The sleep block of the Trend Analysis response. status is the worse of the
 * duration result and the consistency result, and durationStatus is the
 * duration result alone. consistency is "regular" or "irregular", and
 * consistencyCv is the coefficient of variation of nightly sleep hours.
 */
public class SleepTrend {
    @SerializedName("sufficient_data")
    public boolean sufficientData;

    @SerializedName("evaluation_type")
    public EvaluationType evaluationType;

    @SerializedName("avg_duration_hrs")
    public Double avgDurationHrs;

    @SerializedName("target_hrs")
    public Double targetHrs;

    @SerializedName("duration_status")
    public DimensionStatus durationStatus;

    @SerializedName("consistency_cv")
    public Double consistencyCv;

    public String consistency;

    public DimensionStatus status;

    @SerializedName("trend_direction")
    public TrendDirection trendDirection;
}