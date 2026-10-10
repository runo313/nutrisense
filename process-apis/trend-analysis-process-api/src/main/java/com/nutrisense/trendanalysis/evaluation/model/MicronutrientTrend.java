package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * One surfaced micronutrient in the Trend Analysis response. The array holds
 * only the metrics surfaced for this user, so a metric that does not apply is
 * absent, not shown as insufficient.
 */
public class MicronutrientTrend {
	public String nutrient;

	@SerializedName("sufficient_data")
	public boolean sufficientData;

	@SerializedName("evaluation_type")
	public EvaluationType evaluationType;

	@SerializedName("avg_intake")
	public Double avgIntake;

	public Double rda;

	@SerializedName("condition_context")
	public String conditionContext;

	public DimensionStatus status;

	@SerializedName("trend_direction")
	public TrendDirection trendDirection;
}