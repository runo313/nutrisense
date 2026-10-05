package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

public class CaloriesTrend {
	@SerializedName("sufficient_data")
    public boolean sufficientData;

    @SerializedName("evaluation_type")
    public EvaluationType evaluationType;

    @SerializedName("avg_calories")
    public Double avgCalories;

    @SerializedName("caloric_target")
    public Double caloricTarget;

    public DimensionStatus status;

    @SerializedName("trend_direction")
    public TrendDirection trendDirection;
}
