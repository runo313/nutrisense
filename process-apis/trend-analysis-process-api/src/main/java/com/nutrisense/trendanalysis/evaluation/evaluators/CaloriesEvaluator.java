package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.CaloriesTrend;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.EvaluationType;
import com.nutrisense.trendanalysis.evaluation.evaluators.DailyNutrientAggregator;
import com.nutrisense.trendanalysis.evaluation.model.DerivedTargets;
import com.nutrisense.trendanalysis.evaluation.model.Food;
import com.nutrisense.trendanalysis.evaluation.model.Meal;
import com.nutrisense.trendanalysis.evaluation.model.TrendDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the calories dimension of a Trend Analysis window. Takes the raw
 * meals and foods for the window plus the user's caloric target, and produces
 * the CaloriesTrend block of the response: average daily calories, a status
 * against the target, and a trend direction.
 *
 * Evaluation type is GUIDELINE: the window average is compared against a
 * profile-derived target using fixed deviation bands, not an RDA or an
 * upper-limit exposure.
 */

public class CaloriesEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(CaloriesEvaluator.class);
	
	/**
	 * Runs the full calories evaluation for one window.
	 *
	 * Process:
	 *   1. aggregateByDay : total energyKcal per calendar day. Days with no
	 *   usable data are absent, so the map size is the number of days covered.
	 *   
	 *   2. coverage gate : if days covered is below CoverageGate's requirement
	 *   (70% of the window), return insufficient_data with the target filled in 
	 *   and the average and trend null.
	 *   
	 *   3. average : mean over days with data only, not over the window length, 
	 *   so missing days don't deflate the average.
	 *   
	 *   4. target check : if the target is null or zero (e.g. biological sex
	 *      prefer_not_to_say, so no formula ran), return insufficient_data with the average 
	 *      still reported. There is nothing to compare it against.
	 *      
	 *   5. status : average as a fraction of target, checked against the bands in 
	 *   derivedTargets.caloricDeviationBounds: inside the on_track band is on_track, 
	 *   inside the wider soft band is soft_warning, anything beyond is hard_flag. 
	 *   Direction: being well under target is flagged the same as being well over, and
	 *   is never framed as success.
	 *   
	 *   6. trend : each day is converted to its absolute distance from
	 *   target and passed to TrendDirectionCalculator with higherIsBetter false, 
	 *   so "improving" means getting closer to target regardless of which side the user
	 *   is on. Absolute distance matters: with a signed distance, 
	 *   a user over target who moves back toward it would read as declining.
	 *
	 * @param meals : meals from the Meal Log range endpoint. 
	 * @param foods: resolved foods from the Nutrition API (flat list)
	 * @param dailyCaloricTarget : the User profile's computed target in kcal. may be null
	 * @param windowStart : first calendar day of the window
	 * @param windowDays : window length (7, 14, or 30)
	 * @param derivedTargets : loaded derived-targets.json; supplies the (on_track_min_pct,
	 * on_track_max_pct, soft_min_pct, soft_max_pct)

	 * @return a CaloriesTrend, never null. status is INSUFFICIENT_DATA with
	 *         sufficientData false when coverage fails or the target is unusable.
	 *         trendDirection is null in those cases, and also when either half of
	 *         the window has no data.
	 */
	
	public static CaloriesTrend evaluate(List<Meal> meals, List<Food> foods, Double dailyCaloricTarget,
			LocalDate windowStart, int windowDays) {
		
		CaloriesTrend result = new CaloriesTrend();
		result.evaluationType = EvaluationType.GUIDELINE;
	    result.caloricTarget = dailyCaloricTarget;
	    
		
		Map<LocalDate, Double> aggregate = DailyNutrientAggregator.aggregateByDay(meals, foods, "energyKcal",
				windowStart, windowDays);
		
		int daysCovered = aggregate.size();
		int daysRequired = CoverageGate.requiredDays(windowDays);
		
		boolean sufficient = CoverageGate.isSufficient(daysCovered, daysRequired);
		

		if (!sufficient) {
			logger.info("Calories insufficient_data: daysCovered={} required={}", daysCovered, daysRequired);
	        result.sufficientData = false;
	        result.status = DimensionStatus.INSUFFICIENT_DATA; 
			return result;
		}
		
		double sum = aggregate.values().stream().mapToDouble(Double::doubleValue).sum();
		double average = sum / aggregate.size();
		result.avgCalories = average;
		
		if (dailyCaloricTarget == null || dailyCaloricTarget <= 0) {
			logger.info("Calories insufficient_data: no usable caloric target, average={}", average);
			result.sufficientData = false;
			result.status = DimensionStatus.INSUFFICIENT_DATA; 
			return result;
		}
		
		DerivedTargets derivedTargets = new DerivedTargets();
		Map<String, Double> bounds = derivedTargets.caloricDeviationBounds;
		double pctOfTarget = average / dailyCaloricTarget;
		
		if (pctOfTarget >= bounds.get("on_track_min_pct") && pctOfTarget <= bounds.get("on_track_max_pct")) {
	        result.status = DimensionStatus.ON_TRACK;
	    } else if (pctOfTarget >= bounds.get("soft_min_pct") && pctOfTarget <= bounds.get("soft_max_pct")) {
	        result.status = DimensionStatus.SOFT_WARNING;
	    } else {
	        result.status = DimensionStatus.HARD_FLAG;
	    }
		
		Map<LocalDate, Double> distFromTarget = new HashMap<>();
		aggregate.forEach((date, actual) -> {
			double distance = Math.abs(dailyCaloricTarget - actual);
			distFromTarget.put(date, distance);
		});
		
		result.sufficientData = true;
		result.trendDirection = TrendDirectionCalculator.calculate(distFromTarget, windowStart, windowDays, false);
		logger.info("Calories status={} average={} target={} pctOfTarget={} daysCovered={} required={} trend={}",
	            result.status, average, dailyCaloricTarget, pctOfTarget, daysCovered, daysRequired,
	            result.trendDirection);
		return result;
	}

}
