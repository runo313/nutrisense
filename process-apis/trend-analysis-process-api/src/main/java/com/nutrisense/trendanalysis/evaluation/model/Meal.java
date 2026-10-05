package com.nutrisense.trendanalysis.evaluation.model;

import java.util.List;

/** One logged meal as returned by the Meal Log API's range endpoint. */
public class Meal {
    public String mealType;

    public String consumedAt;

    public List<MealItem> items;
}