package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;
public class MetricSummary {

	@SerializedName("metric_type")
    public String metricType;

    public Double average;
    public Integer count;
}
