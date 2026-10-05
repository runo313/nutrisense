package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

public enum DimensionStatus {
	@SerializedName("on_track") ON_TRACK,
    @SerializedName("soft_warning") SOFT_WARNING,
    @SerializedName("hard_flag") HARD_FLAG,
    @SerializedName("insufficient_data") INSUFFICIENT_DATA
}
