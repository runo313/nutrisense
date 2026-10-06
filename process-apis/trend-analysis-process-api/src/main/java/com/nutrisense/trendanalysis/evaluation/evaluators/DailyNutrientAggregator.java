package com.nutrisense.trendanalysis.evaluation.evaluators;

import com.nutrisense.trendanalysis.evaluation.model.Food;
import com.nutrisense.trendanalysis.evaluation.model.Meal;
import com.nutrisense.trendanalysis.evaluation.model.MealItem;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds per-day nutrient totals from a window's worth of logged meals. Every
 * nutrition evaluator (calories, macros, micronutrients) calls this once per
 * nutrient and then averages the result over days that have data.
 *
 * Replaces Daily Insight's scaleAllItems and sumNutrients, which reduced the
 * whole day's items into a single total. This keeps one total per calendar
 * date, because trend analysis needs the days kept separate.
 *
 */

public class DailyNutrientAggregator {

	private static final Logger logger = LoggerFactory.getLogger(DailyNutrientAggregator.class);
	
	/**
     * Step 1 of aggregateByDay: decides which calendar day a meal belongs to.
     * Takes the date portion of consumedAt as returned by Meal Log, with no
     * timezone conversion (a known limitation: a late-night meal can land on
     * the wrong day if its offset differs from the user's local time).
     *
     * @param meal the logged meal
     * @return the meal's date, or null if the meal or its consumedAt is
     * missing or unparseable
     */

	private static LocalDate dateOf(Meal meal) {
		
		if (meal == null || meal.consumedAt == null || meal.consumedAt.length() < 10) {
            return null;
        }
		try {
			return LocalDate.parse(meal.consumedAt.substring(0, 10));

		} catch (DateTimeParseException e) {
			logger.debug("Unparseable consumedAt: date={}", meal.consumedAt);
			return null;
		}
	}
    
	/**
     * Step 2 of aggregateByDay: builds a foodId lookup once, so each item's
     * food is a single get instead of a scan of the whole list.
     *
     * @param foods resolved foods from the Nutrition API
     * @return foods keyed by foodId; empty if foods is null. Foods with a
     *         null foodId are left out.
     */
	private static Map<Integer, Food> indexFoods(List<Food> foods) {
		Map<Integer, Food> findFood = new HashMap<>();

		if (foods == null)
			return findFood;

		for (Food f : foods) {
			if (f.foodId != null)
				findFood.put(f.foodId, f);
		}
		return findFood;
	}
	/**
     * Step 3 of aggregateByDay: how much of one nutrient a single logged item
     * contributed, scaled from the food's per-servingSizeG values to the
     * quantity actually eaten.
     *
     * Returns null, not 0, when the amount can't be known (food not found,
     * servingSizeG null or 0, quantityG null, or the nutrient has no value).
     * Unknown is not the same as none, and treating it as 0 would drag
     * averages down.
     *
     * @param item : the logged item
     * @param food : the item's resolved food, or null if it didn't resolve
     * @param nutrientKey : Nutrition API camelCase key, e.g. "energyKcal"
     * @return the scaled amount, or null if it can't be computed
     */
	private static Double scaledValue(MealItem item, Food food, String nutrientKey) {
		if (food == null || nutrientKey == null || item == null || food.nutrients == null) {
			return null;
		}

		boolean isNullServingSizeG = food.servingSizeG == null || food.servingSizeG == 0;
		boolean isNullQuantityG = item.quantityG == null;
		boolean isNullNutrientValue = food.nutrients.get(nutrientKey) == null;

		if (isNullServingSizeG || isNullQuantityG || isNullNutrientValue) {
			return null;
		}
		return (item.quantityG * food.nutrients.get(nutrientKey)) / food.servingSizeG;
	}
    
	/**
     * Produces one total per calendar day for a single nutrient.
     *
     * Process:
     *   1. indexFood : build the foodId lookup once.
     *   2. dateOf : assign each meal to a date. skip meals with no usable date.
     *   3. window check : skip meals outside windowStart..windowStart+windowDays,
     *                     so a stray out-of-range meal can't change the
     *                     coverage count.
     *   4. scaledValue : for each item, compute its contribution and add it
     *                     to that date's running total. Items that can't be
     *                     scaled are skipped and counted.
     *
     * A date appears in the result only if at least one of its items produced
     * a value, so the map's size is the coverage day count CoverageGate needs.
     * A day where every item failed to resolve is absent, not 0.
     *
     * @param meals : meals from the Meal Log range endpoint
     * @param foods : resolved foods from the Nutrition API (expected flat)
     * @param nutrientKey :  Nutrition API camelCase key, e.g. "energyKcal"
     * @param windowStart first calendar day of the trend window
     * @param windowDays  window length (7, 14, or 30)
     * @return total per date with usable data; empty if there are no meals or no window start
     */
	
	public static Map<LocalDate, Double> aggregateByDay(List<Meal> meals, List<Food> foods, String nutrientKey,
			LocalDate windowStart, int windowDays) {

		Map<LocalDate, Double> totalPerDay = new HashMap<>();
		if (meals == null) {
			logger.debug("Nothing to aggregate for {}: meals or windowStart missing", nutrientKey);
			return totalPerDay;
		}
			
		Map<Integer, Food> items = indexFoods(foods);
		int skippedMeals = 0;
		int skippedItems = 0;

		for (Meal meal : meals) {
			LocalDate mealDate = dateOf(meal);

			if (mealDate == null || meal.items == null) {
				skippedMeals++;
                continue;
			}

			int offset = (int) ChronoUnit.DAYS.between(windowStart, mealDate);

			if (offset < 0 || offset >= windowDays) {
				skippedMeals++;
				logger.debug("Skipping meal outside range: mealDate={} windowStart={}", mealDate, windowStart);
				continue;
			}


			for (MealItem item : meal.items) {

				if (item == null) {
					skippedItems++;
					continue;
				}

				Double value = scaledValue(item, items.get(item.foodId), nutrientKey);
				if (value != null) {
					totalPerDay.merge(mealDate, value, Double::sum);
				} else {
					skippedItems++;
				}
			}

		}
		logger.debug("Aggregated {}: days={} skippedMeals={} skippedItems={}",
                nutrientKey, totalPerDay.size(), skippedMeals, skippedItems);
		return totalPerDay;
	}

}
