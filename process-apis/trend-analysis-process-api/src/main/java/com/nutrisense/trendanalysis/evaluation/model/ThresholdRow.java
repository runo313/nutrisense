package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;
import java.util.Map;
import com.google.gson.annotations.SerializedName;
/**
 * A threshold with a single soft/hard bound (plus an optional base value),
 * e.g. micronutrient RDAs, sodium limits, sleep duration. The common shape
 * for most rows in static-thresholds.json.
 */
public class ThresholdRow extends BaseThresholdRow {

    @SerializedName("base_value")
    public Double baseValue;

    @SerializedName("soft_bound")
    public Double softBound;

    @SerializedName("hard_bound")
    public Double hardBound;

    @SerializedName("dietary_multiplier")
    public Map<String, Double> dietaryMultiplier;

    @SerializedName("flips_direction_under")
    public List<String> flipsDirectionUnder;

    @SerializedName("condition_override_of")
    public String conditionOverrideOf;
}