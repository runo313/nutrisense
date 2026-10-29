package com.nutrisense.trendanalysis.evaluation.evaluators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.Map;

import com.nutrisense.trendanalysis.evaluation.model.AmdrTarget;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;
import com.nutrisense.trendanalysis.evaluation.model.ThresholdRow;

/**
 * Evaluates carbohydrates for one window. MacroEvaluator has already
 * aggregated carbohydratesG per day and resolved both inputs.
 * 1. Run the AMDR range evaluation, which handles coverage, the average,
 *    the range status, and the trend. If it returns INSUFFICIENT_DATA,
 *    return that as is.
 * 2. If the floor row is missing or incomplete, log a warning and keep the
 *    range status.
 * 3. Check the average against the floor: at or above the base value is
 *    ON_TRACK, at or above the hard bound is SOFT_WARNING, below it is HARD_FLAG.
 * 4. Take the worse of the range status and the floor status.
 * The trend is unchanged from the range evaluation. The floor does not
 * affect it.
 *
 * @param dailyCarbs per-day carbohydrate totals in grams; days without data are absent
 * @param target resolved AMDR range, or null if it could not be resolved
 * @param floorRow the 130g floor row from ThresholdResolver.resolveCarbFloorRow, or null
 * @param windowStart first calendar day of the window
 * @param windowDays window length (7, 14, or 30)
 * @return a MacroTrendEntry, never null. The range is in rangeMin and
 *         rangeMax; target stays null.
 */

public class CarbohydrateEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(CarbohydrateEvaluator.class);

	public static MacroTrendEntry evaluateCarbohydrate(Map<LocalDate, Double> dailyCarbs, AmdrTarget target,
			ThresholdRow floorRow, LocalDate windowStart, int windowDays) {

		MacroTrendEntry result = AmdrRangeEvaluator.evaluateAmdrRange(dailyCarbs, target, windowStart, windowDays);

		if (result.status == DimensionStatus.INSUFFICIENT_DATA) {
			return result;
		}

		if (floorRow == null || floorRow.baseValue == null || floorRow.hardBound == null) {
			logger.warn("Carbohydrate floor row missing or incomplete, using range status only");
			return result;
		}

		DimensionStatus rangeStatus = result.status;
		DimensionStatus floorStatus;

		if (result.avgValue >= floorRow.baseValue) {
			floorStatus = DimensionStatus.ON_TRACK;
		} else if (result.avgValue >= floorRow.hardBound) {
			floorStatus = DimensionStatus.SOFT_WARNING;
		} else {
			floorStatus = DimensionStatus.HARD_FLAG;
		}
		
		result.status = (severity(rangeStatus) >= severity(floorStatus)) ? rangeStatus : floorStatus;
		logger.info("Carbohydrate rangeStatus={} floorStatus={} final={} average={} floor={}",
                rangeStatus, floorStatus, result.status, result.avgValue, floorRow.baseValue);
        return result;
	}

	private static int severity(DimensionStatus status) {
		switch(status) {
		case HARD_FLAG: return 2;
        case SOFT_WARNING: return 1;
        default: return 0;
		}
	}
}
