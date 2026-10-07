package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;
import com.nutrisense.trendanalysis.evaluation.model.ProteinTarget;

/**
 * Evaluates protein for one window. Called by MacroEvaluator.evaluate, which
 * has already aggregated proteinG per day and resolved the target, so this
 * works on plain inputs and needs no meals or foods.
 *
 * Process: 1. coverage : days covered is the map's size. If it fails
 * CoverageGate, return INSUFFICIENT_DATA with the target still populated (when
 * one resolved) and the average and trend null.
 * 
 * 2. average : mean over days with data only.
 * 
 * 3. target : if the target is missing or unusable (null, or not above zero,
 * e.g. no weight on the profile), return INSUFFICIENT_DATA with the average
 * still reported, since there is nothing to compare it against.
 * 
 * 4. status : lower bound only. At or above targetG is ON_TRACK; below the
 * target but at or above hardBoundG (the 0.8 g/kg DRI floor) is SOFT_WARNING;
 * below the floor is HARD_FLAG. Eating more than the range maximum is never
 * flagged, because there is no evidence for penalizing high protein in healthy
 * adults.
 * 
 * 5. trend : each day becomes its shortfall below target (zero when at or above
 * target) and goes to TrendDirectionCalculator with higherIsBetter false, so
 * "improving" means shortfall shrinking. Days above target count as zero rather
 * than negative, which keeps the trend consistent with the rule that nothing
 * above target is flagged.
 *
 * Known limitation: the target uses the profile's current weight for every day
 * in the window, so a user whose weight changed during the window is judged
 * against today's target throughout.
 *
 *
 * @param dailyProtein: per-day protein totals in grams; days with no usable data
 *                     are absent
 * @param target: resolved protein target from DerivedTargetResolver, or
 *                     null if it could not be resolved
 * @param windowStart: first calendar day of the window
 * @param windowDays:  window length (7, 14, or 30)
 * @return a MacroTrendEntry, never null. status is INSUFFICIENT_DATA when
 *         coverage fails or the target is unusable. trendDirection is null in
 *         those cases, and also when either half of the window has no data.
 */

public class ProteinEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(ProteinEvaluator.class);

	public static MacroTrendEntry evaluateProtein(Map<LocalDate, Double> dailyProtein, ProteinTarget target,
			LocalDate windowStart, int windowDays) {
		MacroTrendEntry result = new MacroTrendEntry();

		int daysCovered = dailyProtein.size();
		int daysRequired = CoverageGate.requiredDays(windowDays);

		boolean targetUsable = target != null && target.targetG != null && target.hardBoundG != null
				&& target.targetG > 0;

		if (targetUsable) {
			result.target = target.targetG;
		}

		boolean sufficient = CoverageGate.isSufficient(daysCovered, windowDays);

		if (!sufficient) {
			logger.info("Protein insufficient_data: daysCovered={} required={}", daysCovered, daysRequired);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		double sum = dailyProtein.values().stream().mapToDouble(Double::doubleValue).sum();
		double average = sum / dailyProtein.size();
		result.avgValue = average;

		if (!targetUsable) {
			logger.info("Protein insufficient_data: no usable protein target, average={}", average);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		if (average >= target.targetG)
			result.status = DimensionStatus.ON_TRACK;
		else if (average >= target.hardBoundG)
			result.status = DimensionStatus.SOFT_WARNING;
		else
			result.status = DimensionStatus.HARD_FLAG;

		Map<LocalDate, Double> shortfall = new HashMap<>();
		dailyProtein.forEach((date, actual) -> {
			shortfall.put(date, Math.max(0, target.targetG - actual));
		});

		result.trendDirection = TrendDirectionCalculator.calculate(shortfall, windowStart, windowDays, false);
		logger.info("Proteins status={} average={} target={}  daysCovered={} required={} trend={}", result.status,
				average, result.target, daysCovered, daysRequired, result.trendDirection);
		return result;
	}

}
