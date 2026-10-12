package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.TrendDirection;

/**
 * Shared helper that decides whether a metric moved up, down, or held steady
 * across a trend window. Every dimension evaluator in Trend Analysis calls it
 * once per metric, then maps the result onto the response's trend_direction.
 *
 * The window is split by calendar date into an older half and a newer half.
 * Each half is averaged, and the two averages are compared against a 5%
 * stability band. Splitting by date (not by list position) matters because
 * users often have gaps: with 10 of 14 days logged, a midpoint split of the
 * list would put the wrong days in each half.
 *
 * Takes everything as parameters.
 */

public class TrendDirectionCalculator {

	private static final Logger logger = LoggerFactory.getLogger(TrendDirectionCalculator.class);
	private static final double STABLE_DELTA = 0.05;

	/**
	 * Compares the newer half of a window against the older half and classifies
	 * the change.
	 *
	 * Process:
	 * 1. splitAverages - splits the window by calendar date and averages each
	 *    half. Recovery at window 30 reads the same split to report the half
	 *    averages, so the trend and the reported values always agree. For odd
	 *    windows the older half gets the extra day (4/3 at window=7), a
	 *    deliberately conservative bias that makes the recent half work harder to
	 *    register as a change.
	 * 2. guard - if either half has no usable average (null, NaN, or infinite),
	 *    no trend can be determined, so return null.
	 * 3. stability - if the change between halves is within 5%, return STABLE.
	 *    When the first-half average is 0 a percentage is undefined, so two zeros
	 *    count as STABLE and zero to nonzero skips the band and counts as a real
	 *    change.
	 * 4. direction - otherwise classify by which way the metric moved and whether
	 *    that way is good for it. A rise is IMPROVING when higherIsBetter is true
	 *    and DECLINING when it is false, and a fall is the reverse.
	 *
	 * @param dailyValues one value per date that has data, e.g. daily average
	 *        resting HR; days with no data are simply absent
	 * @param windowStart the first calendar day of the window, so the split is
	 *        anchored to real dates; values outside the window are ignored
	 * @param windowDays window length (7, 14, or 30)
	 * @param higherIsBetter polarity of the metric. true if a rise is an
	 *        improvement (HRV), false if a rise is a decline (resting HR, or a
	 *        distance from a target)
	 * @return IMPROVING, DECLINING, or STABLE; null when either half has no usable
	 *         data, meaning no trend can be determined
	 */

	public static TrendDirection calculate(Map<LocalDate, Double> dailyValues, LocalDate windowStart, int windowDays,
			boolean higherIsBetter) {

		Double[] halves = splitAverages(dailyValues, windowStart, windowDays);
		Double firstAvg = halves[0];
		Double secondAvg = halves[1];
		
		if (firstAvg == null || secondAvg == null || Double.isNaN(firstAvg) || Double.isNaN(secondAvg)
				|| Double.isInfinite(firstAvg) || Double.isInfinite(secondAvg)) {
			
			logger.debug("No trend: firstAvg={} secondAvg={} (a half had no usable average)", firstAvg, secondAvg);
			return null;
		}

		if (firstAvg == 0.0) {
			// A percentage change from zero is undefined, so skip the 5% band.
			// Two zeros means nothing moved. Zero to nonzero is a real change.
			if (secondAvg == 0.0) {
				logger.debug("Trend stable: firstAvg={} secondAvg={}", firstAvg, secondAvg);
				return TrendDirection.STABLE;
			}
		} else {
			double delta = deltaPct(firstAvg, secondAvg);
			if (Math.abs(delta) <= STABLE_DELTA) {
				logger.debug("Trend stable: firstAvg={} secondAvg={}", firstAvg, secondAvg);
				return TrendDirection.STABLE;
			}
		}

		boolean wentUp = secondAvg > firstAvg;
		TrendDirection result = (wentUp == higherIsBetter) ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
		logger.debug("Trend {}: firstAvg={} secondAvg={} higherIsBetter={}",
		    result, firstAvg, secondAvg, higherIsBetter);
		return result;
	}

	private static int firstHalfDays(int windowDays) {
		return (windowDays + 1) / 2;
	}

	private static String halfOf(LocalDate date, LocalDate windowStart, int windowDays) {
		int offset = (int) ChronoUnit.DAYS.between(windowStart, date);
		int firstHalf = firstHalfDays(windowDays);
		if (offset < 0 || offset >= windowDays)
			return "ignore";
		else if (offset < firstHalf)
			return "first";
		else
			return "second";
	}

	private static Double average(List<Double> values) {
		if (!values.isEmpty()) {
			double sum = 0.0;
			for (double i : values) {
				sum += i;
			}
			return sum / values.size();
		}
		return null;
	}

	private static double deltaPct(double firstAvg, double secondAvg) {
		return (secondAvg - firstAvg) / firstAvg;
	}
	
/**
 * Splits a window into its older and newer halves by calendar date and
 * averages each. calculate uses this to decide a trend direction, and recovery
 * at window 30 reports the two averages directly, so both read the same split.
 * The older half gets the extra day on odd windows (4/3 at window=7).
 *
 * @param dailyValues one value per date with data; days with no data are absent
 * @param windowStart first calendar day of the window
 * @param windowDays window length (7, 14, or 30)
 * @return a two-element array with the first-half average at index 0 and the
 *         second-half average at index 1. Either is null when that half has
 *         no data, and both are null if the map or windowStart is null.
 */
	
	public static Double[] splitAverages(Map<LocalDate, Double> dailyValues, LocalDate windowStart, int windowDays) {
		Double[] result = new Double[2];
		
		if (dailyValues == null || windowStart == null) {
			return result;
		}
		
		List<Double>firstHalfList = new ArrayList<>();
		List<Double>secondHalfList = new ArrayList<>();
		
		for (Map.Entry<LocalDate, Double> entry : dailyValues.entrySet()) {
	        Double value = entry.getValue();
	        if (value == null) {
	            continue;
	        }
	        String whichHalf = halfOf(entry.getKey(), windowStart, windowDays);
	        if (whichHalf.equals("first")) {
	            firstHalfList.add(value);
	        } else if (whichHalf.equals("second")) {
	            secondHalfList.add(value);
	        }
	    }

	    result[0] = average(firstHalfList);
	    result[1] = average(secondHalfList);

	    logger.debug("Split averages: firstHalfDays={} secondHalfDays={} firstAvg={} secondAvg={}",
	            firstHalfList.size(), secondHalfList.size(), result[0], result[1]);
	    return result;
	} 

}
