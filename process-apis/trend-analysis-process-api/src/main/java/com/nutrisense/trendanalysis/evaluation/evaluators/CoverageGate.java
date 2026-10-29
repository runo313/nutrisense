package com.nutrisense.trendanalysis.evaluation.evaluators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether a data source has enough logged days within a window to trust
 * an average computed from it. Every dimension evaluator calls this before
 * evaluating, and a failed gate means that dimension reports insufficient_data
 * instead of a status.
 * 
 * One shared 70% ratio applies to every source (meal log and wearable alike).
 * That gives 5 of 7 days, 10 of 14, and 21 of 30. Rounding is always up, so the
 * requirement never falls below 70%.
 */
public class CoverageGate {
	private static final Logger logger = LoggerFactory.getLogger(CoverageGate.class);
	private static final double MIN_COVERAGE_RATIO = 0.70;

	/**
	 * @param windowDays window length (7, 14, or 30)
	 * @return the minimum number of days with data needed for the window to count
	 *         as sufficiently covered
	 */
	public static int requiredDays(int windowDays) {
		return (int) Math.ceil(windowDays * MIN_COVERAGE_RATIO);
	}

	public static boolean isSufficient(int daysCovered, int windowDays) {
		boolean sufficient = daysCovered >= requiredDays(windowDays);
		logger.debug("Coverage check: daysCovered={} required={} window={} sufficient={}", daysCovered,
				requiredDays(windowDays), windowDays, sufficient);
		return sufficient;
	}

}
