package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * Data sufficiency for one source (meal log or wearable) over the window, matching the
 * RAML CoverageEntry. daysCovered is the number of calendar days in the window with usable
 * data, daysRequired is CoverageGate
 */
public class CoverageEntry {
    @SerializedName("days_covered")
    public int daysCovered;

    @SerializedName("days_required")
    public int daysRequired;

    public boolean sufficient;
}