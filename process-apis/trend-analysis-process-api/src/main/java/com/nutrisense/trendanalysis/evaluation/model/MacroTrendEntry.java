package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * One macro's result within MacrosTrend. Which target fields are populated
 * depends on the macro's shape: protein, fiber, saturated fat and sodium use
 * target (for saturated fat and sodium it is the cap), while carbohydrates and
 * fat use rangeMin and rangeMax instead. Fields that don't apply to a given
 * macro stay null.
 */
public class MacroTrendEntry {
    @SerializedName("avg_value")
    public Double avgValue;

    public Double target;

    @SerializedName("range_min")
    public Double rangeMin;

    @SerializedName("range_max")
    public Double rangeMax;

    public DimensionStatus status;

    @SerializedName("trend_direction")
    public TrendDirection trendDirection;
}