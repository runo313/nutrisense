package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.ActivityTarget;
import com.nutrisense.trendanalysis.evaluation.model.ActivityTrend;
import com.nutrisense.trendanalysis.evaluation.model.DerivedTargets;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.EvaluationType;
import com.nutrisense.trendanalysis.evaluation.model.PointMetric;
import com.nutrisense.trendanalysis.evaluation.model.Workout;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdResolver;

/**
 * Evaluates the activity dimension of a Trend Analysis window against the
 * CDC/PAG weekly guideline, scaled to the window length. The evaluation type is
 * GUIDELINE. Status is judged on moderate-equivalent minutes (moderate plus the
 * vigorous-to-moderate ratio times vigorous), so a user who meets the vigorous
 * target alone is on track.
 *
 * Requires a ThresholdResolver built on a loaded ThresholdRepository.
 */
public class ActivityEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(ActivityEvaluator.class);
	private ThresholdResolver resolver;

	public ActivityEvaluator(ThresholdResolver resolver) {
		this.resolver = resolver;
	}
	/**
	 * Runs the full activity evaluation for one window.
	 * 1. Resolve the scaled targets. They are populated on the result whenever they
	 *    resolve, even if the dimension turns out to be INSUFFICIENT_DATA.
	 * 2. WorkoutMinutesAggregator builds per-day minutes. Coverage counts days with
	 *    step readings or a classified workout.
	 * 3. If coverage fails CoverageGate, return INSUFFICIENT_DATA with the targets
	 *    still populated.
	 * 4. Sum the moderate and vigorous minutes across covered days. These are the
	 *    totals reported in the response, unscaled.
	 * 5. If the target or the vigorous_to_moderate_ratio cutoff is missing, return
	 *    INSUFFICIENT_DATA with the totals still reported.
	 * 6. Moderate-equivalent minutes are moderate plus ratio times vigorous. For
	 *    status only, this is scaled by windowDays over days covered, so days with no
	 *    data do not count against the user, matching the other evaluators. The
	 *    response shows the raw totals and the full-window targets, so a displayed
	 *    total can sit below the target and still read ON_TRACK.
	 * 7. At or above the scaled moderate target is ON_TRACK, at or above its hard
	 *    bound is SOFT_WARNING, below is HARD_FLAG. The soft bound is not used.
	 * 8. The trend runs on each covered day's moderate-equivalent minutes with
	 *    higherIsBetter true. A covered day with no workout counts as zero.
	 *
	 * Known limitation: only workouts produce minutes, so walking that is not
	 * recorded as a workout counts as zero, and a user who wears the watch but does
	 * not start workouts reads as inactive.
	 *
	 * @param workouts flat list of workouts with statistics
	 * @param points flat list of point metrics; step_count is used for coverage
	 * @param weightKg the user's current weight for the energy rule; may be null
	 * @param derivedTargets supplies the intensity cutoffs, tables and ratio
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return an ActivityTrend, never null. status is INSUFFICIENT_DATA when
	 *         coverage fails or the target or ratio is missing. trendDirection is
	 *         null in those cases, and also when either half of the window has no data.
	 * @throws IOException if static-thresholds.json cannot be read
	 */
	public ActivityTrend evaluate(List<Workout> workouts, List<PointMetric> points, Double weightKg,
			DerivedTargets derivedTargets, LocalDate windowStart, int windowDays) throws IOException {

		ActivityTrend result = new ActivityTrend();
		result.evaluationType = EvaluationType.GUIDELINE;

		ActivityTarget target = resolver.resolveActivityTarget(windowDays);
		boolean targetUsable = target != null && target.moderateTarget != null && target.moderateHardBound != null
				&& target.vigorousTarget != null;

		if (targetUsable) {
			result.moderateMinTarget = target.moderateTarget;
			result.vigorousMinTarget = target.vigorousTarget;
		}

		Map<LocalDate, double[]> aggregate = WorkoutMinutesAggregator.aggregateDaily(workouts, points, weightKg,
				derivedTargets, windowStart, windowDays);

		int daysCovered = aggregate.size();
		int daysRequired = CoverageGate.requiredDays(windowDays);

		if (!CoverageGate.isSufficient(daysCovered, windowDays)) {
			logger.info("Activity insufficient_data: daysCovered={} required={}", daysCovered, daysRequired);
			result.sufficientData = false;
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		double moderateTotal = 0;
		double vigorousTotal = 0;

		for (double[] val : aggregate.values()) {
			moderateTotal += val[0];
			vigorousTotal += val[1];
		}

		result.moderateMinTotal = moderateTotal;
		result.vigorousMinTotal = vigorousTotal;

		Double ratio = (derivedTargets != null && derivedTargets.activityIntensityCutoffs != null)
				? derivedTargets.activityIntensityCutoffs.get("vigorous_to_moderate_ratio")
				: null;

		if (!targetUsable || ratio == null) {
			logger.info("Activity insufficient_data: no usable target or ratio, moderateTotal={} vigorousTotal={}",
					moderateTotal, vigorousTotal);
			result.sufficientData = false;
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		double equivalentTotal = moderateTotal + ratio * vigorousTotal;
		// Scaled to the full window so uncovered days do not count against the user.
		// For a strict comparison, use: double statusMinutes = equivalentTotal;
		double statusMinutes = equivalentTotal * windowDays / daysCovered;

		if (statusMinutes >= target.moderateTarget) {
			result.status = DimensionStatus.ON_TRACK;
		} else if (statusMinutes >= target.moderateHardBound) {
			result.status = DimensionStatus.SOFT_WARNING;
		} else {
			result.status = DimensionStatus.HARD_FLAG;
		}

		Map<LocalDate, Double> dailyEquivalent = new HashMap<>();
		aggregate.forEach((date, minutes) -> dailyEquivalent.put(date, minutes[0] + ratio * minutes[1]));

		result.trendDirection = TrendDirectionCalculator.calculate(dailyEquivalent, windowStart, windowDays, true);
		result.sufficientData = true;

		logger.info(
				"Activity status={} moderateTotal={} vigorousTotal={} statusMinutes={} moderateTarget={} daysCovered={} required={} trend={}",
				result.status, moderateTotal, vigorousTotal, statusMinutes, target.moderateTarget, daysCovered,
				daysRequired, result.trendDirection);
		return result;

	}

}
