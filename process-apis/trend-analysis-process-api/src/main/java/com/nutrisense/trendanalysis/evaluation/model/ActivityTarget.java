package com.nutrisense.trendanalysis.evaluation.model;

/**
 * Weekly CDC/PAG activity targets scaled to the window length. The scaling is
 * the weekly figure times windowDays over 7, so at window 7 the targets are the
 * published figures and at 14 and 30 they are derived, not published.
 * moderateHardBound is the scaled hard bound of the moderate row. The vigorous
 * target is for display only, since status is judged on moderate-equivalent
 * minutes. Not serialized to the response.
 */
public class ActivityTarget {
    public String thresholdId;
    public Double scaleFactor;
    public Double moderateTarget;
    public Double moderateHardBound;
    public Double vigorousTarget;
}