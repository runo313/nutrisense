package com.nutrisense.trendanalysis.evaluation.model;

/**
 * Resolved sleep duration target for one user. targetHrs is
 * max(user target, consensus floor), and floorApplied is true when the floor
 * replaced a lower user target. hardBoundHrs is the effective target times the
 * row's hard bound percentage. Not serialized to the response.
 */

public class SleepDurationTarget {
	public String thresholdId;
    public Double targetHrs;
    public Double hardBoundHrs;
    public boolean floorApplied;
}
