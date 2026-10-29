package com.nutrisense.trendanalysis.evaluation.model;
import com.google.gson.annotations.SerializedName;

public class BaselineDeviationRow extends BaseThresholdRow {

    @SerializedName("baseline_window_days")
    public Integer baselineWindowDays;

    @SerializedName("baseline_statistic")
    public String baselineStatistic;

    @SerializedName("min_data_days")
    public Integer minDataDays;

    @SerializedName("missing_day_handling")
    public String missingDayHandling;

    @SerializedName("measurement_context")
    public String measurementContext;

    @SerializedName("soft_bound")
    public Double softBound;

    @SerializedName("hard_bound")
    public Double hardBound;

    @SerializedName("soft_bound_low")
    public Double softBoundLow;

    @SerializedName("hard_bound_low")
    public Double hardBoundLow;

    @SerializedName("soft_bound_high")
    public Double softBoundHigh;

    @SerializedName("hard_bound_high")
    public Double hardBoundHigh;
}
