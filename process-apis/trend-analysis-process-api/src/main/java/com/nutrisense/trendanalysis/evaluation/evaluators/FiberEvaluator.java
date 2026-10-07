package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.FiberTarget;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;

/**
 * Evaluates fiber for one window. Called by MacroEvaluator.evaluate, which has
 * already aggregated dietaryFiberG per day and resolved the target, so this
 * works on plain inputs and needs no meals or foods.
 *
 * Process: 
 * 1. coverage - days covered is the map's size. If it fails
 * CoverageGate, return INSUFFICIENT_DATA with the target still populated  
 * and the average and trend null. 
 * 
 * 2. average - mean over days with data only, not the window length. 
 * 
 * 3. target - if the target is missing or unusable, return INSUFFICIENT_DATA 
 * with the average still reported. 
 * 
 * 4. status - lower bound only. At or above targetG is ON_TRACK; below the target
 * but at or above hardBoundG is SOFT_WARNING; below the hard bound is
 * HARD_FLAG. Nothing above target is flagged. 
 * 
 * 5. trend - each day becomes its
 * shortfall below target (zero when at or above) and goes to
 * TrendDirectionCalculator with higherIsBetter false, so "improving" means
 * shortfall shrinking.
 *
 * Known limitation: many foods have no fiber value, and DailyNutrientAggregator
 * skips those items rather than counting them as zero. A day with partial fiber
 * data still counts as covered but understates real intake, and a day where no
 * food reported fiber is absent. Fiber is therefore the macro most likely to
 * fail coverage while the others pass, or to read low on a day that looks fine.
 * When fiber is flagged unexpectedly, check which logged foods have fiber data.
 *
 * @param dailyFiber  per-day fiber totals in grams. days with no usable data
 *                    are absent
 * @param target      resolved fiber target, or null if it could not be resolved
 * @param windowStart first calendar day of the window
 * @param windowDays  window length (7, 14, or 30)
 * @return a MacroTrendEntry, never null. status is INSUFFICIENT_DATA when
 *         coverage fails or the target is unusable. trendDirection is null in
 *         those cases, and also when either half of the window has no data.
 */
public class FiberEvaluator {
	private static final Logger logger = LoggerFactory.getLogger(FiberEvaluator.class);

	public static MacroTrendEntry evaluateFiber(Map<LocalDate, Double> dailyFiber, FiberTarget target,
			LocalDate windowStart, int windowDays) {
		MacroTrendEntry result = new MacroTrendEntry();

		int daysCovered = dailyFiber.size();
		int daysRequired = CoverageGate.requiredDays(windowDays);

		boolean targetUsable = target != null && target.targetG != null && target.hardBoundG != null
				&& target.targetG > 0;

		if (targetUsable) {
			result.target = target.targetG;
		}

		if (!CoverageGate.isSufficient(daysCovered, windowDays)) {
			logger.info("Fiber insufficient_data: daysCovered={} required={}", daysCovered, daysRequired);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		double sum = dailyFiber.values().stream().mapToDouble(Double::doubleValue).sum();
		double average = sum / daysCovered;
		result.avgValue = average;

		if (!targetUsable) {
			logger.info("Fiber insufficient_data: no usable fiber target, average={}", average);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		if (average >= target.targetG) {
			result.status = DimensionStatus.ON_TRACK;
		} else if (average >= target.hardBoundG) {
			result.status = DimensionStatus.SOFT_WARNING;
		} else {
			result.status = DimensionStatus.HARD_FLAG;
		}

		Map<LocalDate, Double> shortfall = new HashMap<>();
		dailyFiber.forEach((date, actual) -> shortfall.put(date, Math.max(0, target.targetG - actual)));

		result.trendDirection = TrendDirectionCalculator.calculate(shortfall, windowStart, windowDays, false);

		logger.info("Fiber status={} average={} target={} daysCovered={} required={} trend={}", result.status, average,
				result.target, daysCovered, daysRequired, result.trendDirection);
		return result;

	}
}
