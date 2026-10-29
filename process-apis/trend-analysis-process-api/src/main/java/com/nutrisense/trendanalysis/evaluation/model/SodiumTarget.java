package com.nutrisense.trendanalysis.evaluation.model;

/**
 * Resolved sodium cap and hard bound in mg. condition is the condition that
 * selected the override row (hypertension), or null for the general row. It is
 * carried for ranking and is not shown on the response entry.
 */
public class SodiumTarget {
    public String thresholdId;
    public Double capMg;
    public Double hardBoundMg;
    public String condition;
}