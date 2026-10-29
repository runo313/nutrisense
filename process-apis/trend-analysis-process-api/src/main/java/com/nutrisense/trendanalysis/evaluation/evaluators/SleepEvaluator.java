package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.EvaluationType;
import com.nutrisense.trendanalysis.evaluation.model.SleepDurationTarget;
import com.nutrisense.trendanalysis.evaluation.model.SleepSession;
import com.nutrisense.trendanalysis.evaluation.model.SleepTrend;
import com.nutrisense.trendanalysis.evaluation.model.ThresholdRow;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdResolver;

/**
 * Runs the full sleep evaluation for one window.
 * 1. Resolve the duration target. A missing or incomplete target gives
 *    INSUFFICIENT_DATA with the average still reported.
 * 2. SleepAggregator turns sessions into nightly hours. Coverage counts nights,
 *    not sessions, so a night with many stage intervals still counts once.
 * 3. If coverage fails, return INSUFFICIENT_DATA with targetHrs still populated.
 * 4. Average over nights with data only.
 * 5. Duration status: at or above the target is ON_TRACK, at or above the hard
 *    bound is SOFT_WARNING, below is HARD_FLAG. There is no upper bound.
 * 6. Consistency: the coefficient of variation (sample standard deviation over
 *    the mean). Below the soft bound is regular, from the soft bound up to the
 *    hard bound is irregular with SOFT_WARNING, and at or above the hard bound
 *    is HARD_FLAG. If the row is missing or the mean is zero, consistency is
 *    skipped and the duration status stands.
 * 7. Overall status is the worse of the two, so consistency can escalate a
 *    result but never soften it.
 * 8. The trend runs on each night's shortfall below the target with
 *    higherIsBetter false, on duration only.
 *
 * @param sessions flat list of sleep sessions, fetched from the day before windowStart
 * @param userTargetHrs the profile's target sleep duration; null falls back to the floor
 * @param windowStart first calendar day of the window
 * @param windowDays window length (7, 14, or 30)
 * @return a SleepTrend, never null. evaluationType is ADEQUACY. status is
 *         INSUFFICIENT_DATA when coverage fails or the target is unusable.
 * @throws IOException if static-thresholds.json cannot be read
 */

public class SleepEvaluator {
	private static final Logger logger = LoggerFactory.getLogger(SleepEvaluator.class);

	private ThresholdResolver resolveSleep;

	public SleepEvaluator(ThresholdResolver resolveSleep) {
		this.resolveSleep = resolveSleep;
	}

	public SleepTrend evaluate(List<SleepSession> sessions, Double userTargetHrs, LocalDate windowStart, int windowDays)
			throws IOException {

		SleepTrend result = new SleepTrend();
		result.evaluationType = EvaluationType.ADEQUACY;

		SleepDurationTarget target = resolveSleep.resolveSleepDurationTarget(userTargetHrs);
		boolean targetUsable = target != null && target.targetHrs != null && target.hardBoundHrs != null;
		if (targetUsable) {
			result.targetHrs = target.targetHrs;
		}

		Map<LocalDate, Double> nightly = SleepAggregator.aggregateNightly(sessions, windowStart, windowDays);
		int nightsCovered = nightly.size();
		int nightsRequired = CoverageGate.requiredDays(windowDays);

		if (!CoverageGate.isSufficient(nightsCovered, windowDays)) {
			logger.info("Sleep insufficient_data: nightsCovered={} required={}", nightsCovered, nightsRequired);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			result.durationStatus = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		double sum = nightly.values().stream().mapToDouble(Double::doubleValue).sum();
		double average = sum / nightsCovered;
		result.avgDurationHrs = average;

		if (!targetUsable) {
			logger.info("Sleep insufficient_data: no usable duration target, average={}", average);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			result.durationStatus = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		if (average >= target.targetHrs) {
			result.durationStatus = DimensionStatus.ON_TRACK;
		} else if (average >= target.hardBoundHrs) {
			result.durationStatus = DimensionStatus.SOFT_WARNING;
		} else {
			result.durationStatus = DimensionStatus.HARD_FLAG;
		}
		result.status = result.durationStatus;

		ThresholdRow cvRow = resolveSleep.resolveSleepConsistencyRow();
		if (cvRow == null || cvRow.softBound == null || cvRow.hardBound == null || average <= 0) {
			logger.warn("Sleep consistency not evaluated: row missing or incomplete, or zero average");
		} else {
			double sumSq = 0;
			for (double hours : nightly.values()) {
				sumSq += (hours - average) * (hours - average);
			}
			double cv = nightsCovered > 1 ? Math.sqrt(sumSq / (nightsCovered - 1)) / average : 0.0;
			result.consistencyCv = cv;

			DimensionStatus consistencyStatus;
			if (cv < cvRow.softBound) {
				result.consistency = "regular";
				consistencyStatus = DimensionStatus.ON_TRACK;
			} else {
				result.consistency = "irregular";
				consistencyStatus = cv < cvRow.hardBound ? DimensionStatus.SOFT_WARNING : DimensionStatus.HARD_FLAG;
			}
			result.status = Severity.worse(result.durationStatus, consistencyStatus);
		}
		Map<LocalDate, Double> shortfall = new HashMap<>();
		nightly.forEach((date, hours) -> shortfall.put(date, Math.max(0, target.targetHrs - hours)));
		result.trendDirection = TrendDirectionCalculator.calculate(shortfall, windowStart, windowDays, false);
		result.sufficientData = true;

		logger.info(
				"Sleep status={} duration={} consistency={} cv={} average={} target={} nights={} required={} trend={}",
				result.status, result.durationStatus, result.consistency, result.consistencyCv, average,
				target.targetHrs, nightsCovered, nightsRequired, result.trendDirection);
		return result;
	}

}
