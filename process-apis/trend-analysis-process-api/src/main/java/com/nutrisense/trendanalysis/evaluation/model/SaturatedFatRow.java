package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

public class SaturatedFatRow {
    @SerializedName("threshold_id")
    public String thresholdId;

    @SerializedName("pct_cap")
    public Double pctCap;

    @SerializedName("soft_pct")
    public Double softPct;

    @SerializedName("hard_pct")
    public Double hardPct;

    public String condition;

    @SerializedName("condition_override_of")
    public String conditionOverrideOf;

    @SerializedName("source_body")
    public String sourceBody;

    @SerializedName("source_edition")
    public String sourceEdition;

    @SerializedName("source_url")
    public String sourceUrl;

    public String confidence;
    public String notes;
}