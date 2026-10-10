package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.nutrisense.trendanalysis.evaluation.model.AmdrTarget;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.EvaluationType;
import com.nutrisense.trendanalysis.evaluation.model.FiberTarget;
import com.nutrisense.trendanalysis.evaluation.model.Food;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;
import com.nutrisense.trendanalysis.evaluation.model.MacrosTrend;
import com.nutrisense.trendanalysis.evaluation.model.Meal;
import com.nutrisense.trendanalysis.evaluation.model.ProteinTarget;
import com.nutrisense.trendanalysis.evaluation.model.SatFatTarget;
import com.nutrisense.trendanalysis.evaluation.model.SodiumTarget;
import com.nutrisense.trendanalysis.evaluation.model.ThresholdRow;
import com.nutrisense.trendanalysis.evaluation.thresholds.DerivedTargetResolver;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdResolver;

/**
 * Builds the MacrosTrend block for one Trend Analysis window. The resolvers
 * are supplied at construction: DerivedTargetResolver for protein, fiber,
 * saturated fat and the AMDR macros, and ThresholdResolver for sodium and the
 * carbohydrate floor. Both must be built on the same loaded ThresholdRepository.
 *
 * @param derivedTargetResolver resolves targets that depend on the profile
 * @param thresholdResolver resolves targets from the static threshold rows
 */

public class MacroEvaluator { 
	private static final Logger logger = LoggerFactory.getLogger(MacroEvaluator.class);
	private DerivedTargetResolver resolveDerivedMacros;
	private ThresholdResolver resolveThresholdMacros;
	
	public MacroEvaluator(DerivedTargetResolver resolveDerivedMacros, ThresholdResolver resolveThresholdMacros) {
		this.resolveDerivedMacros = resolveDerivedMacros;
		this.resolveThresholdMacros =  resolveThresholdMacros;
		
	}

	/**
	 * Evaluates all six macros for one window.
	 * 1. Aggregate each nutrient per day with DailyNutrientAggregator, one call
	 *    per key: proteinG, dietaryFiberG, carbohydratesG, totalFatG,
	 *    saturatedFatG, sodiumMg.
	 * 2. Resolve each macro's target. A target that cannot be resolved comes back
	 *    null and the macro reports INSUFFICIENT_DATA.
	 * 3. Run each macro's evaluator on its per-day map and target.
	 * 4. Assemble the MacrosTrend. sufficientData is true when at least one macro
	 *    was evaluated.
	 *
	 * @param meals meals from the Meal Log range endpoint, with consumedAt kept
	 * @param foods resolved foods from the Nutrition API (flat list)
	 * @param weightKg current weight for the protein target; null gives no protein target
	 * @param goalType primary goal type, or null if the user has none
	 * @param direction primary goal direction, or null
	 * @param activityBaseline self-reported activity level
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @param dailyCaloricTarget the profile's target in kcal; null gives no
	 *        fiber, saturated fat, fat or carbohydrate targets
	 * @param activeConditions the user's active conditions; null is treated as none
	 * @return a MacrosTrend with all six entries populated, never null
	 * @throws IOException if a threshold file cannot be read
	 */
	
	public MacrosTrend evaluate(List<Meal> meals, List<Food> foods, Double weightKg, String goalType, String direction,
			String activityBaseline, LocalDate windowStart, int windowDays, Double dailyCaloricTarget, List<String> activeConditions) throws IOException {
		
		MacrosTrend result = new MacrosTrend();
		
		Map<LocalDate, Double> aggregateProtein = DailyNutrientAggregator.aggregateByDay(meals, foods, "proteinG",
				windowStart, windowDays);
		Map<LocalDate, Double> aggregateFiber = DailyNutrientAggregator.aggregateByDay(meals, foods, "dietaryFiberG",
				windowStart, windowDays);
		Map<LocalDate, Double> aggregateCarbs = DailyNutrientAggregator.aggregateByDay(meals, foods, "carbohydratesG",
				windowStart, windowDays);
		Map<LocalDate, Double> aggregateTotalFat = DailyNutrientAggregator.aggregateByDay(meals, foods, "totalFatG",
				windowStart, windowDays);
		Map<LocalDate, Double> aggregateSatFat= DailyNutrientAggregator.aggregateByDay(meals, foods, "saturatedFatG",
				windowStart, windowDays);
		Map<LocalDate, Double> aggregateSodium= DailyNutrientAggregator.aggregateByDay(meals, foods, "sodiumMg",
				windowStart, windowDays);
		
		ProteinTarget resolvedProtein = resolveDerivedMacros.resolveProteinTarget(weightKg, goalType, direction, activityBaseline);
		SodiumTarget resolvedSodium = resolveThresholdMacros.resolveSodiumTarget(activeConditions);
		SatFatTarget resolveSaturatedFatTarget = resolveDerivedMacros.resolveSaturatedFatTarget(dailyCaloricTarget, activeConditions);
		AmdrTarget resolveAmdrFat = resolveDerivedMacros.resolveAmdrTarget("total_fat_g", dailyCaloricTarget);
		AmdrTarget resolveAmdrCarbs = resolveDerivedMacros.resolveAmdrTarget("carbohydrate_g", dailyCaloricTarget);
		FiberTarget resolvedFiber = resolveDerivedMacros.resolveFiberTarget(dailyCaloricTarget);
		ThresholdRow carbFloorRow = resolveThresholdMacros.resolveCarbFloorRow();
		
		MacroTrendEntry evaluateProtein = ProteinEvaluator.evaluateProtein(aggregateProtein, resolvedProtein, windowStart, windowDays);
		MacroTrendEntry evaluateFiber = FiberEvaluator.evaluateFiber(aggregateFiber, resolvedFiber, windowStart, windowDays);
		MacroTrendEntry evaluateSodium = SodiumEvaluator.evaluateSodium(aggregateSodium, resolvedSodium, windowStart, windowDays);
		MacroTrendEntry evaluateSatFat = SaturatedFatEvaluator.evaluateSatFat(aggregateSatFat, resolveSaturatedFatTarget, windowStart, windowDays);
		MacroTrendEntry evaluateTotalFat = AmdrRangeEvaluator.evaluateAmdrRange(aggregateTotalFat, resolveAmdrFat, windowStart, windowDays);
		MacroTrendEntry evaluateCarbs = CarbohydrateEvaluator.evaluateCarbohydrate(aggregateCarbs, resolveAmdrCarbs, carbFloorRow, windowStart, windowDays);
		
		
		result.evaluationType = EvaluationType.GUIDELINE;
		result.carbohydrates = evaluateCarbs;
		result.fat = evaluateTotalFat;
		result.fiber = evaluateFiber;
		result.sodium = evaluateSodium;
		result.protein = evaluateProtein;
		result.saturatedFat = evaluateSatFat;
		
		result.sufficientData = Stream.of(evaluateProtein, evaluateCarbs, evaluateTotalFat,
		        evaluateSatFat, evaluateFiber, evaluateSodium)
		        .anyMatch(entry -> entry.status != DimensionStatus.INSUFFICIENT_DATA);

		logger.info("Macros evaluated: sufficientData={} protein={} carbs={} fat={} satFat={} fiber={} sodium={}",
		        result.sufficientData, evaluateProtein.status, evaluateCarbs.status, evaluateTotalFat.status,
		        evaluateSatFat.status, evaluateFiber.status, evaluateSodium.status);
		return result;
	}

}
