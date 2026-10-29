package com.nutrisense.trendanalysis.evaluation.model;

/**
 * Resolved threshold for one micronutrient for one user. baseValue,
 * softBound and hardBound are the effective values after any dietary
 * multiplier and UL clamp, so the evaluator compares against these directly
 * and never touches the raw row. conditionContext is the condition or
 * constraint that surfaced the metric (anemia, vegetarian), shown on the
 * response entry. directionFlipped is true when a flipping condition applies
 * (potassium under kidney disease), in which case the metric is judged as an
 * upper bound and evaluationType is EXPOSURE instead of ADEQUACY.
 */
public class MicronutrientTarget {
    public String thresholdId;
    public Double baseValue;
    public Double softBound;
    public Double hardBound;
    public String conditionContext;
    public boolean directionFlipped;
    public EvaluationType evaluationType;
}