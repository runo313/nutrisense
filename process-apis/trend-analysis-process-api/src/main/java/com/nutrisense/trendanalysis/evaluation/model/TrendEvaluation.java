package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;
import com.google.gson.annotations.SerializedName;

/**
 * The result TrendEvaluationService returns to Mule.
 *  Mule adds the envelope fields (user id, window dates,
 * generated time, generation mode, threshold version, metrics shown).
 *
 * The service builds a TrendEvaluation for every in-scope request and also for an
 * out-of-scope one, where every dimension is INSUFFICIENT_DATA. None of the six
 * dimension fields is ever null, and micronutrients is an empty list when nothing
 * is surfaced.
 */
public class TrendEvaluation {

    @SerializedName("dimension_trends")
    public DimensionTrends dimensionTrends;

    public static class DimensionTrends {
        public ActivityTrend activity;
        public SleepTrend sleep;
        public RecoveryTrend recovery;
        public CaloriesTrend calories;
        public MacrosTrend macros;
        public List<MicronutrientTrend> micronutrients;
    }
}