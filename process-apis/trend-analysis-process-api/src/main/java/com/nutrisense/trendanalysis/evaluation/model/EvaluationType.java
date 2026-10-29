package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

public enum EvaluationType {
	@SerializedName("adequacy") ADEQUACY,
    @SerializedName("exposure") EXPOSURE,
    @SerializedName("guideline") GUIDELINE,
    @SerializedName("personal_baseline") PERSONAL_BASELINE
}
