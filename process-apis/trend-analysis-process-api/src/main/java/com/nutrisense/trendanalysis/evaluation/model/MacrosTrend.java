package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * The macros block of the Trend Analysis response: one entry per macro.
 *
 * evaluationType is always GUIDELINE for the whole block, matching the
 * confidence tier in derived-targets.json.
 *
 * sufficientData is true when at least one macro was evaluated. A macro that
 * failed its own coverage check shows INSUFFICIENT_DATA on its entry, so
 * nothing is hidden.
 */
public class MacrosTrend {
	@SerializedName("sufficient_data")
	public boolean sufficientData;

	@SerializedName("evaluation_type")
	public EvaluationType evaluationType;

	public MacroTrendEntry protein;
	public MacroTrendEntry carbohydrates;
	public MacroTrendEntry fat;

	@SerializedName("saturated_fat")
	public MacroTrendEntry saturatedFat;

	public MacroTrendEntry fiber;
	public MacroTrendEntry sodium;
}