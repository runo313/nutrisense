package com.nutrisense.trendanalysis.evaluation.model;

import java.util.Map;

/** One resolved food from the Nutrition API, with nutrients per servingSizeG grams. */
public class Food {
    public Integer foodId;
    public String name;
    public Double servingSizeG;

    // Keys are the Nutrition API's camelCase names (energyKcal, proteinG, ironMg, ...).
    // A nutrient with no data is present with a null value or absent entirely.
    public Map<String, Double> nutrients;
}