package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.PointMetric;

/**
 * Builds per-day values for one wearable metric from a window's raw point
 * metrics. Recovery uses the daily average (resting heart rate, HRV) and
 * activity will use the daily sum (steps, active energy). Both share the same
 * grouping step, so the skip rules and the window check are identical.
 *
 * A day appears in the result only if at least one valid reading landed on it,
 * so the map's size is the coverage day count CoverageGate needs. The window
 * average is later taken as the mean of these daily values, so a day with five
 * readings weighs the same as a day with one.
 *
 * Known limitation: the date comes from the first 10 characters of recordedAt
 * with no timezone conversion, so a late-evening reading can land on the wrong
 * day, the same as meals.
 *
 */
public class PointMetricAggregator {
	private static final Logger logger = LoggerFactory.getLogger(PointMetricAggregator.class);

	private static LocalDate dateOf(PointMetric point) {
		if (point == null || point.recordedAt == null || point.recordedAt.length() < 10) {
			return null;
		}

		try {
			return LocalDate.parse(point.recordedAt.substring(0, 10));
		} catch (DateTimeParseException e) {
			logger.debug("Unparseable recordedAt: date={}", point.recordedAt);
			return null;
		}

	}
	/**
	 * Groups one metric's readings by calendar day. Skips null points, other
	 * metric types, null values, unusable dates, and days outside the window.
	 * Skips are counted by reason and logged once at debug.
	 *
	 * @param points flat list of wearable point metrics
	 * @param metricType the wearable metric name, e.g. "hrv" or "resting_heart_rate"
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return the readings for each date that has at least one valid reading; empty
	 *         if points, windowStart or metricType is null
	 */

	private static Map<LocalDate, List<Double>> groupByDay(List<PointMetric> points, String metricType,
			LocalDate windowStart, int windowDays) {
		Map<LocalDate, List<Double>> result = new HashMap<>();
		if (points == null || windowStart == null) {
			return result;
		}

		int nullPoint = 0;
		int otherMetricType = 0;
		int nullPointValue = 0;
		int unusableDate = 0;
		int outsideWindow = 0;

		for (PointMetric point : points) {
			if (point == null) {
				nullPoint++;
				continue;
			}
			if (!metricType.equals(point.metricType)) {
				otherMetricType++;
				continue;
			}
			if (point.value == null) {
				nullPointValue++;
				continue;
			}

			LocalDate pointDate = dateOf(point);
			if (pointDate == null) {
				unusableDate++;
				continue;
			}

			int offset = (int) ChronoUnit.DAYS.between(windowStart, pointDate);
			if (offset < 0 || offset >= windowDays) {
				outsideWindow++;
				continue;
			}

			result.computeIfAbsent(pointDate, d -> new ArrayList<>()).add(point.value);
		}
		logger.debug(
				"Grouped {}: days={} nullPoint={} otherMetricType={} nullValue={} unusableDate={} outsideWindow={}",
				metricType, result.size(), nullPoint, otherMetricType, nullPointValue, unusableDate, outsideWindow);
		return result;
	}
	/**
	 * Reduces each day's readings to their mean. Used for point-in-time
	 * physiological readings such as resting heart rate and HRV.
	 *
	 * @param points flat list of wearable point metrics
	 * @param metricType the wearable metric name to keep
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return mean reading per date with data; empty if there is none
	 */
	public static Map<LocalDate, Double> averageByDay(List<PointMetric> points, String metricType,
			LocalDate windowStart, int windowDays){
		
		Map<LocalDate, Double> result = new HashMap<>();
		
		Map<LocalDate, List<Double>> groupedDays = groupByDay(points, metricType, windowStart, windowDays);
		
		groupedDays.forEach((date,values) -> {
			Double average = values.stream().mapToDouble(Double::doubleValue).sum() /( values.size());
			result.put(date, average);
		});
		logger.debug("Averaged {} by day: days={}", metricType, result.size());
		return result;	
	}
	/**
	 * Reduces each day's readings to their total. Used for incremental counts
	 * such as steps and active energy, where summing gives the day's total and
	 * averaging would give a meaningless per-reading figure.
	 *
	 * @param points flat list of wearable point metrics
	 * @param metricType the wearable metric name to keep
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return total per date with data; empty if there is none
	 */
	
	public static Map<LocalDate, Double> sumByDay(List<PointMetric> points, String metricType,
	        LocalDate windowStart, int windowDays) {

	    Map<LocalDate, Double> result = new HashMap<>();
	    groupByDay(points, metricType, windowStart, windowDays).forEach((date, values) ->
	            result.put(date, values.stream().mapToDouble(Double::doubleValue).sum()));

	    logger.debug("Summed {} by day: days={}", metricType, result.size());
	    return result;
	}

}
