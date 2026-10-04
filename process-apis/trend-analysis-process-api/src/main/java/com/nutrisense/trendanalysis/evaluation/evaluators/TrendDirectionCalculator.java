package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	 * Compares the newer half of a window against the older half and classifies the
	 * change.
	 *
	 * Process: 
	 * 1. halfOf assign each dated value to the first half, second half, or
	 * ignore it if it falls outside the window. For odd windows the older half gets
	 * the extra day (4/3 at window=7), a deliberately conservative bias that makes
	 * the recent half work harder to register as a change.
	 *  
	 * 2. average - average each half. An empty half has no average. 
	 * 
	 * 3. guard - if either half has no
	 * usable average, no trend can be determined, so return null. 
	 * 
	 * 4. stability - if the change between halves is within 5%, 
	 * return "stable". When the first-half average is 0 a percentage is undefined, 
	 * so two zeros count as stable and zero
	 * to nonzero skips the band and counts as a real change. 
	 * 
	 * 5. direction - otherwise classify by which way it moved and whether that way is good for
	 * this metric.
	 *
	 * @param dailyValues    one value per date that has data, e.g. daily average
	 *                       resting HR; days with no data are simply absent
	 *                       
	 * @param windowStart    the first calendar day of the window, so the split is
	 *                       anchored to real dates; values outside the window are
	 *                       ignored
	 *                       
	 * @param windowDays     window length (7, 14, or 30)
	 * 
	 * @param higherIsBetter polarity of the metric. true if a rise is an
	 *                       improvement (HRV), false if a rise is a decline
	 *                       (resting HR, or distance from a target)
	 *                       
	 * @return "improving", "declining", or "stable"; null when either half has no
	 *         usable data, meaning no trend can be determined
	 */

	public static String calculate(Map<LocalDate, Double> dailyValues, LocalDate windowStart, int windowDays,
			boolean higherIsBetter) {

		List<Double> firstHalfValues = new ArrayList<>();
		List<Double> secondHalfValues = new ArrayList<>();

		dailyValues.forEach((date, value) -> {
			String whichHalf = halfOf(date, windowStart, windowDays);
			if (whichHalf.equals("first")) {
				firstHalfValues.add(value);
			} else if (whichHalf.equals("second")) {
				secondHalfValues.add(value);
			}
		});

		Double firstAvg = average(firstHalfValues);
		Double secondAvg = average(secondHalfValues);
		if (firstAvg == null || secondAvg == null || Double.isNaN(firstAvg) || Double.isNaN(secondAvg)
				|| Double.isInfinite(firstAvg) || Double.isInfinite(secondAvg)) {
			
			logger.debug("No trend: firstHalfDays={} secondHalfDays={} (a half had no usable average)",
				    firstHalfValues.size(), secondHalfValues.size());
			return null;
		}

		if (firstAvg == 0.0) {
			// A percentage change from zero is undefined, so skip the 5% band.
			// Two zeros means nothing moved. Zero to nonzero is a real change.
			if (secondAvg == 0.0) {
				logger.debug("Trend stable: firstAvg={} secondAvg={}", firstAvg, secondAvg);
				return "stable";
			}
		} else {
			double delta = deltaPct(firstAvg, secondAvg);
			if (Math.abs(delta) <= STABLE_DELTA) {
				logger.debug("Trend stable: firstAvg={} secondAvg={}", firstAvg, secondAvg);
				return "stable";
			}
		}

		boolean wentUp = secondAvg > firstAvg;
		String result = (wentUp == higherIsBetter) ? "improving" : "declining";
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

}
