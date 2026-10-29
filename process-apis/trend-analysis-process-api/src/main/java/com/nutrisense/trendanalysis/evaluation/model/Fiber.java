package com.nutrisense.trendanalysis.evaluation.model;
import com.google.gson.annotations.SerializedName;
public class Fiber {
	@SerializedName("threshold_id") public String thresholdId;
	@SerializedName("metric_key") public String metricKey;
	@SerializedName("grams_per_1000_kcal") public Double gramsPer1000Kcal;
	@SerializedName("soft_pct_of_base") public Double softPctOfBase;
	@SerializedName("hard_pct_of_base") public Double hardPctOfBase;
	@SerializedName("source_body") public String sourceBody;
	@SerializedName("source_edition") public String sourceEdition;
	@SerializedName("source_url") public String sourceUrl;
    public String confidence;
    public String notes;
}