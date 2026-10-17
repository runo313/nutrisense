package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * The activity block of the Trend Analysis response. The totals are the window's
 * moderate and vigorous minutes, and the targets are the CDC/PAG weekly figures
 * scaled to the window. status is judged on moderate-equivalent minutes
 * (moderate plus 2 times vigorous) against the scaled moderate target, so a
 * user who meets the vigorous target alone is on track.
 */
public class ActivityTrend {
    @SerializedName("sufficient_data")
    public boolean sufficientData;

    @SerializedName("evaluation_type")
    public EvaluationType evaluationType;

    @SerializedName("moderate_min_total")
    public Double moderateMinTotal;

    @SerializedName("vigorous_min_total")
    public Double vigorousMinTotal;

    @SerializedName("moderate_min_target")
    public Double moderateMinTarget;

    @SerializedName("vigorous_min_target")
    public Double vigorousMinTarget;

    public DimensionStatus status;

    @SerializedName("trend_direction")
    public TrendDirection trendDirection;
}