package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.Food;
import com.nutrisense.trendanalysis.evaluation.model.Meal;
import com.nutrisense.trendanalysis.evaluation.model.MicronutrientTarget;
import com.nutrisense.trendanalysis.evaluation.model.MicronutrientTrend;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdResolver;

/**
 * Evaluates every micronutrient surfaced for this user.
 * 1. For each of the five metrics (iron, zinc, calcium, B12, potassium),
 *    resolve a target. A null target means the metric is not surfaced or has
 *    no row for this bucket, so it is skipped with no entry.
 * 2. Aggregate the matching nutrient per day with DailyNutrientAggregator.
 * 3. If days covered fails CoverageGate, add an INSUFFICIENT_DATA entry with
 *    the rda still populated.
 * 4. Average over days with data only.
 * 5. If the target is unusable (direction flipped, or no base or hard bound),
 *    add an INSUFFICIENT_DATA entry with the average still reported.
 * 6. At or above the base is ON_TRACK, at or above the hard bound is
 *    SOFT_WARNING, below the hard bound is HARD_FLAG. The soft bound is not used.
 * 7. The trend runs on each day's shortfall below the base with higherIsBetter
 *    false, so improving means the shortfall is shrinking.
 *
 * Known limitation: users with an unspecified biological sex get no iron, zinc,
 * calcium or potassium entries, because those rows have no all-adult
 * variant. B12 is unaffected.
 *
 * @param meals meals from the Meal Log range endpoint, with consumedAt kept
 * @param foods resolved foods from the Nutrition API (flat list)
 * @param resolvedBucket the user's bucket from DemographicResolver.resolveBucket
 * @param activeConditions the user's active conditions; null is treated as none
 * @param activeConstraints the user's active dietary constraints; null is treated as none
 * @param windowStart first calendar day of the window
 * @param windowDays window length (7, 14, or 30)
 * @return one MicronutrientTrend per surfaced metric in a fixed order, or an
 *         empty list if nothing is surfaced. Never null.
 * @throws IOException if static-thresholds.json cannot be read
 */

public class MicronutrientEvaluator {
	private static final Logger logger = LoggerFactory.getLogger(MicronutrientEvaluator.class);

	private final Map<String, String> METRICS = new LinkedHashMap<>();
	{
		METRICS.put("iron_mg", "ironMg");
		METRICS.put("zinc_mg", "zincMg");
		METRICS.put("calcium_mg", "calciumMg");
		METRICS.put("vitamin_b12_mcg", "vitaminB12Mcg");
		METRICS.put("potassium_mg", "potassiumMg");
	}

	private ThresholdResolver resolveMicros;

	public MicronutrientEvaluator(ThresholdResolver resolveMicros) {
		this.resolveMicros = resolveMicros;
	}

	public List<MicronutrientTrend> evaluate(List<Meal> meals, List<Food> foods, String resolvedBucket,
			List<String> activeConditions, List<String> activeConstraints, LocalDate windowStart, int windowDays)
			throws IOException {

		List<MicronutrientTrend> result = new ArrayList<>();

		for (Map.Entry<String, String> entry : METRICS.entrySet()) {
			String metricKey = entry.getKey();
			String nutritionKey = entry.getValue();
			MicronutrientTarget resolvedTarget = resolveMicros.resolveMicronutrientTarget(metricKey, resolvedBucket,
					activeConditions, activeConstraints);

			if (resolvedTarget == null)
				continue;

			MicronutrientTrend trendResult = new MicronutrientTrend();
			trendResult.nutrient = metricKey;
			trendResult.evaluationType = resolvedTarget.evaluationType;
			trendResult.conditionContext = resolvedTarget.conditionContext;
			trendResult.rda = (resolvedTarget.directionFlipped) ? null : resolvedTarget.baseValue;

			Map<LocalDate, Double> dailyMicro = DailyNutrientAggregator.aggregateByDay(meals, foods, nutritionKey,
					windowStart, windowDays);

			int daysCovered = dailyMicro.size();
			int daysRequired = CoverageGate.requiredDays(windowDays);

			if (!CoverageGate.isSufficient(daysCovered, windowDays)) {
				logger.info("Micronutrient insufficient_data: metricKey={} daysCovered={} required={}", metricKey,
						daysCovered, daysRequired);
				trendResult.status = DimensionStatus.INSUFFICIENT_DATA;
				trendResult.sufficientData = false;
				result.add(trendResult);
				continue;
			}

			double sum = dailyMicro.values().stream().mapToDouble(Double::doubleValue).sum();
			double average = sum / daysCovered;
			trendResult.avgIntake = average;

			if (resolvedTarget.directionFlipped || resolvedTarget.baseValue == null
					|| resolvedTarget.hardBound == null) {
				logger.info("Micronutrient insufficient_data: no usable micro target, average={} metricKey={}", average,
						metricKey);
				trendResult.status = DimensionStatus.INSUFFICIENT_DATA;
				trendResult.sufficientData = false;
				result.add(trendResult);
				continue;
			}

			if (average >= resolvedTarget.baseValue) {
				trendResult.status = DimensionStatus.ON_TRACK;
			} else if (average >= resolvedTarget.hardBound) {
				trendResult.status = DimensionStatus.SOFT_WARNING;
			} else {
				trendResult.status = DimensionStatus.HARD_FLAG;
			}

			Map<LocalDate, Double> shortfall = new HashMap<>();
			dailyMicro.forEach((date, actual) -> shortfall.put(date, Math.max(0, resolvedTarget.baseValue - actual)));

			trendResult.trendDirection = TrendDirectionCalculator.calculate(shortfall, windowStart, windowDays, false);
			trendResult.sufficientData = true;
			
			logger.info("metricKey={} status={} average={} base={} daysCovered={} required={} trend={}", metricKey,
					trendResult.status, average, resolvedTarget.baseValue, daysCovered, daysRequired,
					trendResult.trendDirection);

			result.add(trendResult);
		}
		return result;

	}
}
