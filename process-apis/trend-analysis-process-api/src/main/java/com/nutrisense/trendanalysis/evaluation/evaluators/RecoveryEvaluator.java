package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.BaselineDeviationRow;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.EvaluationType;
import com.nutrisense.trendanalysis.evaluation.model.MetricSummary;
import com.nutrisense.trendanalysis.evaluation.model.PointMetric;
import com.nutrisense.trendanalysis.evaluation.model.RecoveryTrend;
import com.nutrisense.trendanalysis.evaluation.model.TrendDirection;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdResolver;

/**
 * Evaluates the recovery dimension of a Trend Analysis window from resting
 * heart rate and HRV. The result has two shapes depending on the window.
 *
 * Windows 7 and 14: each metric's window average is compared against the
 * user's own 30-day baseline from the Wearable summary call, using the
 * percentage deviation bounds in baseline-deviations.json. The evaluation type
 * is ADEQUACY and status is populated.
 *
 * Window 30: the window is the baseline period, so no baseline is fetched or
 * compared. The first half of the window is compared against the second half
 * and the trend direction is the only signal. The evaluation type is
 * PERSONAL_BASELINE and status stays null.
 *
 * Both metrics are required. If either one fails coverage, or its baseline is
 * missing or too short, the whole dimension is INSUFFICIENT_DATA, because a
 * recovery status built from one metric would be a weaker claim than the
 * dimension represents.
 *
 * Known limitation: the 30-day baseline includes the days of the 7 or 14-day
 * window, so the baseline is slightly pulled toward the window average.
 *
 * Requires a ThresholdResolver built on a loaded ThresholdRepository.
 */

public class RecoveryEvaluator {

	private static final Logger logger = LoggerFactory.getLogger(RecoveryEvaluator.class);

	private ThresholdResolver resolveRecovery;

	public RecoveryEvaluator(ThresholdResolver resolveRecovery) {
		this.resolveRecovery = resolveRecovery;
	}
	
	/**
	 * Runs the full recovery evaluation for one window.
	 * 1. Set the evaluation type: PERSONAL_BASELINE at window 30, otherwise ADEQUACY.
	 * 2. Build a daily average map for resting heart rate and for HRV with
	 *    PointMetricAggregator.
	 * 3. If either map fails CoverageGate, return INSUFFICIENT_DATA with nothing else set.
	 * 4. Take the mean of each daily map. These are the window averages, taken
	 *    over days with data, not over the window length.
	 *
	 * Windows 7 and 14:
	 * 5. Set the window averages on the result.
	 * 6. Load the baseline rows for resting_hr_bpm and hrv_sdnn. If either is
	 *    missing or lacks its bounds or minDataDays, return INSUFFICIENT_DATA with
	 *    the averages still reported.
	 * 7. Find each metric's entry in the summaries. If one is missing, has no
	 *    average, or has a count below the row's minDataDays, return
	 *    INSUFFICIENT_DATA with the averages still reported.
	 * 8. Set the baselines, then compute each metric's edges as baseline times
	 *    (1 + bound).
	 * 9. Resting HR status: at or below the soft edge is ON_TRACK, at or below the
	 *    hard edge is SOFT_WARNING, above is HARD_FLAG. HRV is the mirror image, with
	 *    the edges below the baseline.
	 * 10. Status is the worse of the two, using Severity.worse.
	 *
	 * Window 30:
	 * 5. Set the first-half and second-half averages for each metric with
	 *    TrendDirectionCalculator.splitAverages. Status stays null.
	 *
	 * Every window that reaches the end:
	 * 11. Compute each metric's trend with TrendDirectionCalculator, resting HR
	 *     with higherIsBetter false and HRV with it true, and combine them.
	 * 12. Set sufficientData to true and log the outcome.
	 *
	 * @param points flat list of wearable point metrics; resting_heart_rate and
	 *        hrv readings are used and everything else is ignored
	 * @param summaries the 30-day baseline entries from the Wearable summary call,
	 *        using the wearable names (resting_heart_rate, hrv). Null at window 30.
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return a RecoveryTrend, never null. At windows 7 and 14, status is
	 *         INSUFFICIENT_DATA when coverage fails, a baseline row is unusable, or a
	 *         baseline is missing or too short. At window 30, status is null and
	 *         trendDirection is the signal. trendDirection is null when neither
	 *         metric has data in both halves.
	 * @throws IOException if baseline-deviations.json cannot be read
	 */

	public RecoveryTrend evaluate(List<PointMetric> points, List<MetricSummary> summaries, LocalDate windowStart,
			int windowDays) throws IOException {

		RecoveryTrend result = new RecoveryTrend();

		result.evaluationType = (windowDays == 30) ? EvaluationType.PERSONAL_BASELINE : EvaluationType.ADEQUACY;

		Map<LocalDate, Double> dailyHr = PointMetricAggregator.averageByDay(points, "resting_heart_rate", windowStart,
				windowDays);
		Map<LocalDate, Double> dailyHrv = PointMetricAggregator.averageByDay(points, "hrv", windowStart, windowDays);

		int hrDays = dailyHr.size();
		int hrvDays = dailyHrv.size();
		int daysRequired = CoverageGate.requiredDays(windowDays);

		if (!CoverageGate.isSufficient(hrDays, windowDays) || !CoverageGate.isSufficient(hrvDays, windowDays)) {
			logger.info("Recovery insufficient_data: hrDays={} hrvDays={} required={}", hrDays, hrvDays, daysRequired);
			result.status = DimensionStatus.INSUFFICIENT_DATA;
			return result;
		}

		double avgHr = dailyHr.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double avgHrv = dailyHrv.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

		if (windowDays == 30) {
			Double[] hrSplit = TrendDirectionCalculator.splitAverages(dailyHr, windowStart, windowDays);
			Double[] hrvSplit = TrendDirectionCalculator.splitAverages(dailyHrv, windowStart, windowDays);
			result.avgRestingHrFirstHalf = hrSplit[0];
			result.avgRestingHrSecondHalf = hrSplit[1];
			result.avgHrvSdnnFirstHalf = hrvSplit[0];
			result.avgHrvSdnnSecondHalf = hrvSplit[1];
		} else {
			result.avgRestingHr = avgHr;
			result.avgHrvSdnn = avgHrv;

			BaselineDeviationRow hrRow = resolveRecovery.resolveBaselineRow("resting_hr_bpm");
			BaselineDeviationRow hrvRow = resolveRecovery.resolveBaselineRow("hrv_sdnn");

			boolean hrRowUsable = hrRow != null && hrRow.softBound != null && hrRow.hardBound != null
					&& hrRow.minDataDays != null;
			boolean hrvRowUsable = hrvRow != null && hrvRow.softBound != null && hrvRow.hardBound != null
					&& hrvRow.minDataDays != null;

			if (!hrRowUsable || !hrvRowUsable) {
				logger.info("Recovery insufficient_data: baseline row missing or incomplete");
				result.status = DimensionStatus.INSUFFICIENT_DATA;
				return result;
			}

			MetricSummary hrSummary = findSummary(summaries, "resting_heart_rate");
			MetricSummary hrvSummary = findSummary(summaries, "hrv");

			boolean summaryUsable = hrSummary != null && hrvSummary != null && hrSummary.average != null
					&& hrvSummary.average != null && hrSummary.count != null && hrvSummary.count != null
					&& hrSummary.count >= hrRow.minDataDays && hrvSummary.count >= hrvRow.minDataDays;

			if (!summaryUsable) {
				logger.info("Recovery insufficient_data: baseline summary missing or below minimum days");
				result.status = DimensionStatus.INSUFFICIENT_DATA;
				return result;
			}

			result.baselineRestingHr = hrSummary.average;
			result.baselineHrvSdnn = hrvSummary.average;

			double hrSoftEdge = hrSummary.average * (1 + hrRow.softBound);
			double hrHardEdge = hrSummary.average * (1 + hrRow.hardBound);
			double hrvSoftEdge = hrvSummary.average * (1 + hrvRow.softBound);
			double hrvHardEdge = hrvSummary.average * (1 + hrvRow.hardBound);

			DimensionStatus hrStatus;
			if (avgHr <= hrSoftEdge) {
				hrStatus = DimensionStatus.ON_TRACK;
			} else if (avgHr <= hrHardEdge) {
				hrStatus = DimensionStatus.SOFT_WARNING;
			} else {
				hrStatus = DimensionStatus.HARD_FLAG;
			}

			DimensionStatus hrvStatus;
			if (avgHrv >= hrvSoftEdge) {
				hrvStatus = DimensionStatus.ON_TRACK;
			} else if (avgHrv >= hrvHardEdge) {
				hrvStatus = DimensionStatus.SOFT_WARNING;
			} else {
				hrvStatus = DimensionStatus.HARD_FLAG;
			}

			result.status = Severity.worse(hrStatus, hrvStatus);
		}
		TrendDirection hrTrend = TrendDirectionCalculator.calculate(dailyHr, windowStart, windowDays, false);
	    TrendDirection hrvTrend = TrendDirectionCalculator.calculate(dailyHrv, windowStart, windowDays, true);
	    result.trendDirection = combineTrends(hrTrend, hrvTrend);
	    result.sufficientData = true;

	    logger.info("Recovery window={} type={} status={} avgHr={} avgHrv={} hrDays={} hrvDays={} required={} trend={}",
	            windowDays, result.evaluationType, result.status, avgHr, avgHrv, hrDays, hrvDays, daysRequired,
	            result.trendDirection);
	    return result;
	}
	
	/**
	 * Finds one metric's entry in the baseline summaries.
	 *
	 * @param summaries the summary entries; may be null
	 * @param metricType the wearable metric name, e.g. "hrv"
	 * @return the matching entry, or null if the list is null or has no match
	 */

	private static MetricSummary findSummary(List<MetricSummary> summaries, String metricType) {
		if (summaries == null) {
			return null;
		}
		return summaries.stream().filter(s -> s != null && metricType.equals(s.metricType)).findFirst().orElse(null);
	}
	
	/**
	 * Combines the resting heart rate trend and the HRV trend into one. A trend
	 * that could not be computed is ignored in favor of the other, so a metric
	 * with a missing half does not hide a real signal in the other. If either is
	 * DECLINING the result is DECLINING, which also settles a conflict where one
	 * metric improves and the other declines, matching the worse-wins rule used
	 * for statuses. Otherwise IMPROVING if either is IMPROVING, else STABLE.
	 *
	 * @param a the resting heart rate trend; may be null
	 * @param b the HRV trend; may be null
	 * @return the combined trend, or null if both are null
	 */
	
	private static TrendDirection combineTrends(TrendDirection a, TrendDirection b) {
	    if (a == null) return b;
	    if (b == null) return a;
	    if (a == TrendDirection.DECLINING || b == TrendDirection.DECLINING) return TrendDirection.DECLINING;
	    if (a == TrendDirection.IMPROVING || b == TrendDirection.IMPROVING) return TrendDirection.IMPROVING;
	    return TrendDirection.STABLE;
	}

}
