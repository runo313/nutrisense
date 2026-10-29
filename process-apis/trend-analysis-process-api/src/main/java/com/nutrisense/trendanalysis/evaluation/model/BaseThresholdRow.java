package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;
import com.google.gson.annotations.SerializedName;

/**
 * Shared metadata for a single threshold row: identity, scope, sourcing,
 * and surfacing rules. Deliberately holds no bound values of its own —
 * ThresholdRow and RangeThresholdRow each define bounds differently,
 * since a plain floor/ceiling and a min/max range aren't the same shape.
 */
public abstract class BaseThresholdRow {

    @SerializedName("threshold_id")
    public String thresholdId;

    public String dimension;

    @SerializedName("metric_key")
    public String metricKey;

    @SerializedName("value_type")
    public String valueType;

    @SerializedName("evaluation_scope")
    public String evaluationScope;

    public String direction;

    @SerializedName("bound_mode")
    public String boundMode;

    public String unit;

    @SerializedName("demographic_key")
    public String demographicKey;

    @SerializedName("surfacing_rule")
    public SurfacingRule surfacingRule;

    @SerializedName("primary_evaluation")
    public boolean primaryEvaluation;

    @SerializedName("source_body")
    public String sourceBody;

    @SerializedName("source_edition")
    public String sourceEdition;

    @SerializedName("source_url")
    public String sourceUrl;

    public String confidence;
    public String notes;

    public static class SurfacingRule {
        public List<String> conditions;
        public List<String> constraints;
    }
}