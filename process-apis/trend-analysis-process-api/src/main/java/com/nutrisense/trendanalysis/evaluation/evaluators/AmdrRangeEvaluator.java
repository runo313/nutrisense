package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.AmdrTarget;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;

/**
 * Evaluates one AMDR macro for one window. MacroEvaluator has already
 * aggregated the nutrient per day and resolved the target.
 * 1. If days covered fails CoverageGate, return INSUFFICIENT_DATA with the
 *    range still populated when one resolved.
 * 2. Average over days with data only.
 * 3. If the target is unusable, return INSUFFICIENT_DATA with the average reported.
 * 4. Inside the range (boundaries included) is ON_TRACK, outside the range but
 *    within the hard bounds is SOFT_WARNING, beyond a hard bound is HARD_FLAG.
 *    Direction-agnostic: too little is flagged the same as too much.
 * 5. The trend runs on each day's distance outside the range (zero when
 *    inside) with higherIsBetter false, so improving means moving toward the range.
 *
 * @param dailyValues per-day totals in grams; days without data are absent
 * @param target resolved range, or null if it could not be resolved
 * @param windowStart first calendar day of the window
 * @param windowDays window length (7, 14, or 30)
 * @return a MacroTrendEntry, never null. The range is in rangeMin and
 *         rangeMax; target stays null. trendDirection is null when status is
 *         INSUFFICIENT_DATA or either half has no data.
 */

public class AmdrRangeEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(AmdrRangeEvaluator.class);

	public static MacroTrendEntry evaluateAmdrRange(Map<LocalDate, Double> dailyValues, AmdrTarget target,
	        LocalDate windowStart, int windowDays) {

	    MacroTrendEntry result = new MacroTrendEntry();

	    int daysCovered = dailyValues.size();
	    int daysRequired = CoverageGate.requiredDays(windowDays);

	    boolean targetUsable = target != null && target.rangeMinG != null && target.rangeMaxG != null
	            && target.hardBelowG != null && target.hardAboveG != null;

	    if (targetUsable) {
	        result.rangeMin = target.rangeMinG;
	        result.rangeMax = target.rangeMaxG;
	    }

	    if (!CoverageGate.isSufficient(daysCovered, windowDays)) {
	        logger.info("AMDR insufficient_data: daysCovered={} required={}", daysCovered, daysRequired);
	        result.status = DimensionStatus.INSUFFICIENT_DATA;
	        return result;
	    }

	    double sum = dailyValues.values().stream().mapToDouble(Double::doubleValue).sum();
	    double average = sum / daysCovered;
	    result.avgValue = average;

	    if (!targetUsable) {
	        logger.info("AMDR insufficient_data: no usable target, average={}", average);
	        result.status = DimensionStatus.INSUFFICIENT_DATA;
	        return result;
	    }

	    if (average >= target.rangeMinG && average <= target.rangeMaxG) {
	        result.status = DimensionStatus.ON_TRACK;
	    } else if (average >= target.hardBelowG && average <= target.hardAboveG) {
	        result.status = DimensionStatus.SOFT_WARNING;
	    } else {
	        result.status = DimensionStatus.HARD_FLAG;
	    }

	    Map<LocalDate, Double> distanceOutside = new HashMap<>();
	    dailyValues.forEach((date, actual) -> {
	        double distance = 0.0;
	        if (actual < target.rangeMinG) {
	            distance = target.rangeMinG - actual;
	        } else if (actual > target.rangeMaxG) {
	            distance = actual - target.rangeMaxG;
	        }
	        distanceOutside.put(date, distance);
	    });

	    result.trendDirection = TrendDirectionCalculator.calculate(distanceOutside, windowStart, windowDays, false);

	    logger.info("AMDR status={} average={} range={}-{} daysCovered={} required={} trend={}",
	            result.status, average, target.rangeMinG, target.rangeMaxG, daysCovered, daysRequired,
	            result.trendDirection);
	    return result;
	}
}
