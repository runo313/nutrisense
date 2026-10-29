package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * A threshold expressed as a min/max range rather than a single bound,
 * e.g. sleep_deep_pct_range, sleep_rem_pct_range. Exists specifically
 * because those two rows don't fit ThresholdRow's single-bound shape.
 */
public class RangeThresholdRow extends BaseThresholdRow {

    @SerializedName("range_min")
    public Double rangeMin;

    @SerializedName("range_max")
    public Double rangeMax;

    @SerializedName("hard_bound_low")
    public Double hardBoundLow;

    @SerializedName("hard_bound_high")
    public Double hardBoundHigh;
}