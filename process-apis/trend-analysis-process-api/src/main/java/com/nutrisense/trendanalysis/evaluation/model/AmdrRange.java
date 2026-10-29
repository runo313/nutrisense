package com.nutrisense.trendanalysis.evaluation.model;
import com.google.gson.annotations.SerializedName;
public class AmdrRange {
	@SerializedName("threshold_id") 
	public String thresholdId;
	
	@SerializedName("metric_key") 
	public String metricKey;
	
	@SerializedName("pct_min")
	public Double pctMin;
	
	@SerializedName("pct_max")
	public Double pctMax;
	
	@SerializedName("hard_pct_below")
	public Double hardPctBelow;
	
	@SerializedName("hard_pct_above")
	public Double hardPctAbove;
	
	@SerializedName("kcal_per_gram")
	public Double kcalPerGram;
	
	@SerializedName("primary_evaluation")
	public boolean primaryEvaluation;
	
	@SerializedName("source_body")
	public String sourceBody;
	
	@SerializedName("source_edition")
	public String sourceEdition;
	
	@SerializedName("source_url")
	
	public String sourceUrl;
    public String confidence;
    public String notes;
}