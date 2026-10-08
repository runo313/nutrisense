package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;
import com.nutrisense.trendanalysis.evaluation.model.SatFatTarget;

/**
 * Evaluates saturated fat for one window. MacroEvaluator has already aggregated
 * saturatedFatG per day and resolved the target.
 * 1. If days covered fails CoverageGate, return INSUFFICIENT_DATA with the
 *    cap still populated when one resolved.
 * 2. Average over days with data only.
 * 3. If the target is unusable, return INSUFFICIENT_DATA with the average reported.
 * 4. At or below the cap is ON_TRACK, at or below the hard bound is
 *    SOFT_WARNING, above the hard bound is HARD_FLAG.
 * 5. The trend runs on each day's excess above the cap (zero when under),
 *    with higherIsBetter false, so improving means the excess is shrinking.
 *
 * @param dailySatFat per-day saturated fat totals in grams; days without data are absent
 * @param target resolved cap, or null if it could not be resolved
 * @param windowStart first calendar day of the window
 * @param windowDays window length (7, 14, or 30)
 * @return a MacroTrendEntry, never null. The cap is in target. trendDirection
 *         is null when status is INSUFFICIENT_DATA or either half has no data.
 */ 
class SaturatedFatEvaluator {
	private static final Logger logger = LoggerFactory.getLogger(SaturatedFatEvaluator.class);
	
	public static MacroTrendEntry evaluateSatFat(Map<LocalDate, Double> dailySatFat, SatFatTarget target,
			LocalDate windowStart, int windowDays) {
		
		MacroTrendEntry result = new MacroTrendEntry();

		int daysCovered = dailySatFat.size();
		int daysRequired = CoverageGate.requiredDays(windowDays);
		
		boolean targetUsable = target != null && target.capG != null && target.hardBoundG != null
				&& target.capG > 0;

		if (targetUsable) {
			result.target = target.capG;
		}
		
		if (!CoverageGate.isSufficient(daysCovered, windowDays)) {
			logger.info("Saturated Fat insufficient_data: daysCovered={} required={}", daysCovered, daysRequired);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}
		
		double sum = dailySatFat.values().stream().mapToDouble(Double::doubleValue).sum();
		double average = sum / daysCovered;
		result.avgValue = average;
		
		if (!targetUsable) {
			logger.info("Saturated Fat insufficient_data: no usable saturated fat target, average={}", average);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}
		
		if (average <= target.capG){
			result.status = DimensionStatus.ON_TRACK;
		} else if (average <= target.hardBoundG) {
			result.status = DimensionStatus.SOFT_WARNING;
		} else {
			result.status = DimensionStatus.HARD_FLAG;
		}
		
		Map<LocalDate, Double> excess = new HashMap<>();
		dailySatFat.forEach((date, actual) -> excess.put(date, Math.max(0, actual - target.capG)));

		result.trendDirection = TrendDirectionCalculator.calculate(excess, windowStart, windowDays, false);

		logger.info("Saturated Fat status={} average={} target={} daysCovered={} required={} trend={}", result.status, average,
				result.target, daysCovered, daysRequired, result.trendDirection);
		return result;
		
	}

}
