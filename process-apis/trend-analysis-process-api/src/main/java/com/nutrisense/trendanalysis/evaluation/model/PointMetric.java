package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * One wearable point metric reading from the Wearable API. recordedAt is kept
 * as the raw ISO string so Gson needs no date adapter. metricType uses the
 * wearable names (resting_heart_rate, hrv), which differ from the baseline
 * row keys (resting_hr_bpm, hrv_sdnn).
 */
public class PointMetric {
	@SerializedName("metric_type")
    public String metricType;

    public Double value;

    @SerializedName("recorded_at")
    public String recordedAt;
}
