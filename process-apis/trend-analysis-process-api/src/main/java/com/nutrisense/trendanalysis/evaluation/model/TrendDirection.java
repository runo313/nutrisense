package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

public enum TrendDirection {
	@SerializedName("improving") IMPROVING,
    @SerializedName("declining") DECLINING,
    @SerializedName("stable") STABLE
}
