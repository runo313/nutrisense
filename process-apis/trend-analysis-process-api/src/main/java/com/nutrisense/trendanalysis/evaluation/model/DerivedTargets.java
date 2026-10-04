package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;
import java.util.Map;

public class DerivedTargets {
    public List<ProteinCoefficient> proteinCoefficients;
    public List<AmdrRange> amdrRanges;
    public Fiber fiber;
    public List<SaturatedFatRow> saturatedFat;
    public Map<String, Double> activityMultipliers;
}