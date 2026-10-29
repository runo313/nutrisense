package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class ProteinCoefficient {
	
	@SerializedName("threshold_id")
	public String thresholdId;
	
	@SerializedName("range_min") 
	public Double rangeMin;
	
	@SerializedName("range_max")
	public Double rangeMax;
	
	@SerializedName("soft_bound") 
	public Double softBound;
	
	@SerializedName("hard_bound") 
	public Double hardBound;
	
	@SerializedName("source_body") 
	public String sourceBody;
	
	@SerializedName("source_edition") 
	public String sourceEdition;
	
	@SerializedName("source_url")
	public String sourceUrl;
	
    public Match match;
    public Integer precedence;
    public String confidence;
    public String notes;

    public static class Match {
    	@SerializedName("goal_type")
        public String goalType;
    	
    	@SerializedName("goal_direction")
        public String goalDirection;
    	
    	@SerializedName("activity_baseline_in")
        public List<String> activityBaselineIn;
    }
}