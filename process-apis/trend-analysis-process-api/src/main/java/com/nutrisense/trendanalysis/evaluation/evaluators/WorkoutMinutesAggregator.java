package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.DerivedTargets;
import com.nutrisense.trendanalysis.evaluation.model.PointMetric;
import com.nutrisense.trendanalysis.evaluation.model.Workout;
import com.nutrisense.trendanalysis.evaluation.model.WorkoutStatistic;

/**
 * Builds per-day moderate and vigorous activity minutes for a Trend Analysis
 * window. ActivityEvaluator calls it once per request and sums the result.
 *
 * Each workout is classified by the first rule that applies:
 *   1. energy: active energy per kg per minute against cutoffs derived from the
 *      CDC MET definitions (3.0 moderate, 6.0 vigorous, minus 1.0 for resting)
 *   2. speed: for walking and hiking, miles per hour against the walking cutoffs
 *   3. type: a fixed table, used only when the first two cannot run
 * Energy comes first because every workout carries it and METs are how the
 * guideline defines intensity. The workout's own energy_burned is always null,
 * so energy and distance come from its statistics.
 *
 * Each day's result is a two-element array: index 0 is moderate minutes and
 * index 1 is vigorous minutes. A day is covered if it has step readings or a
 * classified workout, including a light one that adds no minutes, so the map's
 * size is the day count CoverageGate needs. A workout that cannot be classified
 * is skipped and does not make its day covered.
 *
 * Known limitations: wrist energy estimates undercount cycling and strength
 * work, so those can read light. Workout times are used as local with no
 * timezone conversion, and a workout belongs to its start date. Active energy in
 * Cal is treated as kilocalories. Overlapping workouts recorded by two devices
 * would both count. Walking that is not recorded as a workout adds no minutes.
 *
 * Stateless. Fetching the workouts and step readings is the caller's job.
 */
public class WorkoutMinutesAggregator {

	private static final Logger logger = LoggerFactory.getLogger(WorkoutMinutesAggregator.class);

	private static final String LIGHT = "light";
	private static final String MODERATE = "moderate";
	private static final String VIGOROUS = "vigorous";
	private static final String TYPE_PREFIX = "hkworkoutactivitytype";
	private static final double KM_PER_MILE = 1.609344;
	
	/**
	 * Normalizes an activity type so the Wearable API's two forms match. Trims and
	 * lowercases the type, then strips the HKWorkoutActivityType prefix, so
	 * "HKWorkoutActivityTypeWalking" and "walking" both become "walking".
	 *
	 * @param rawType the activity_type from the workout
	 * @return the normalized type, or null if rawType is null
	 */
	private static String normalizeType(String rawType) {
		if (rawType == null) {
			return null;
		}
		String type = rawType.strip().toLowerCase();
		return type.startsWith(TYPE_PREFIX) ? type.substring(TYPE_PREFIX.length()) : type;
	}
	
	/**
	 * Returns the date a workout belongs to, taken from the first 10 characters of
	 * startTime with no timezone conversion. A workout that crosses midnight
	 * belongs to its start date.
	 *
	 * @param workout the workout
	 * @return the date, or null if the workout or startTime is missing or unparseable
	 */
	private static LocalDate dateOf(Workout workout) {
		if (workout == null || workout.startTime == null || workout.startTime.length() < 10) {
			return null;
		}
		try {
			return LocalDate.parse(workout.startTime.substring(0, 10));

		} catch (DateTimeParseException e) {
			logger.debug("Unparseable workout startTime: {}", workout.startTime);
			return null;
		}
	}
	/**
	 * Returns the workout's length in minutes. Uses duration, not end_time, because
	 * some rows have an end_time far later than start_time plus duration. A null
	 * duration_unit is assumed to be minutes.
	 *
	 * @param workout the workout
	 * @return the minutes, or null if the duration is missing or not positive, or
	 *         the unit is anything other than minutes
	 */
	private static Double minutesOf(Workout workout) {
		if (workout == null || workout.duration == null || workout.duration <= 0) {
			return null;
		}
		if (workout.durationUnit != null && !"min".equalsIgnoreCase(workout.durationUnit)) {
			return null;
		}
		return workout.duration;
	}
	/**
	 * Finds one statistic on a workout, e.g. active_energy_burned or
	 * distance_walking_running. Callers read sumValue and unit from the result.
	 *
	 * @param workout the workout
	 * @param metricType the statistic's metric_type
	 * @return the matching statistic, or null if the workout has no statistics or
	 *         none match
	 */
	private static WorkoutStatistic findStatistic(Workout workout, String metricType) {
		if (workout == null || workout.statistics == null || metricType == null) {
			return null;
		}

		return workout.statistics.stream().filter(s -> s != null && metricType.equals(s.metricType)).findFirst()
				.orElse(null);
	}

	/**
	 * Rule 1 of classify: classifies by active energy per kg per minute. The
	 * cutoffs are (METs minus resting METs) times kcal per kg per minute per MET,
	 * from activityIntensityCutoffs, which gives 0.035 for moderate and 0.0875 for
	 * vigorous at the current values. Cal and kcal are treated as the same unit.
	 *
	 * @param workout the workout, with its statistics
	 * @param minutes the workout's length in minutes
	 * @param weightKg the user's weight; the rule cannot run without it
	 * @param derivedTargets supplies the cutoffs
	 * @return "light", "moderate" or "vigorous", or null if weight is missing, a
	 *         cutoff is missing, or the workout has no usable energy statistic
	 */
	private static String intensityByEnergy(Workout workout, double minutes, Double weightKg,
			DerivedTargets derivedTargets) {

		if (weightKg == null || weightKg <= 0 || minutes <= 0 || derivedTargets == null
				|| derivedTargets.activityIntensityCutoffs == null) {
			return null;
		}

		Map<String, Double> cutoffs = derivedTargets.activityIntensityCutoffs;
		Double moderateMets = cutoffs.get("moderate_min_mets");
		Double vigorousMets = cutoffs.get("vigorous_min_mets");
		Double restingMets = cutoffs.get("resting_mets");
		Double kcalPerKgMinPerMet = cutoffs.get("kcal_per_kg_min_per_met");

		if (moderateMets == null || vigorousMets == null || restingMets == null || kcalPerKgMinPerMet == null) {
			logger.warn("Activity intensity cutoffs missing in derived targets, skipping energy classification");
			return null;
		}

		WorkoutStatistic energy = findStatistic(workout, "active_energy_burned");
		if (energy == null || energy.sumValue == null || energy.unit == null
				|| !(energy.unit.equalsIgnoreCase("Cal") || energy.unit.equalsIgnoreCase("kcal"))) {
			return null;
		}

		Double kcalPerKgPerMin = energy.sumValue / weightKg / minutes;
		double moderateCutoff = (moderateMets - restingMets) * kcalPerKgMinPerMet;
		double vigorousCutoff = (vigorousMets - restingMets) * kcalPerKgMinPerMet;

		if (kcalPerKgPerMin >= vigorousCutoff) {
			return VIGOROUS;
		} else if (kcalPerKgPerMin >= moderateCutoff) {
			return MODERATE;
		} else {
			return LIGHT;
		}
	}
	/**
	 * Rule 2 of classify: classifies walking and hiking by speed. Speed is the
	 * distance statistic over the workout's hours, with km converted to miles.
	 * Below walking_moderate_min_mph (2.5, the current CDC page) is light, up to
	 * walking_vigorous_min_mph (5.0) is moderate, and at or above is vigorous.
	 * Speed averages over the whole duration, so pauses lower it.
	 *
	 * @param workout the workout, with its statistics
	 * @param type the normalized activity type
	 * @param minutes the workout's length in minutes
	 * @param derivedTargets supplies the cutoffs and the list of speed types
	 * @return "light", "moderate" or "vigorous", or null if the type is not a speed
	 *         type, a cutoff is missing, or the workout has no usable distance
	 */
	private static String intensityBySpeed(Workout workout, String type, double minutes,
			DerivedTargets derivedTargets) {

		if (type == null || minutes <= 0 || derivedTargets == null || derivedTargets.activityIntensityCutoffs == null
				|| derivedTargets.activitySpeedTypes == null || !derivedTargets.activitySpeedTypes.contains(type)) {
			return null;
		}

		Double moderateMph = derivedTargets.activityIntensityCutoffs.get("walking_moderate_min_mph");
		Double vigorousMph = derivedTargets.activityIntensityCutoffs.get("walking_vigorous_min_mph");
		if (moderateMph == null || vigorousMph == null) {
			logger.warn("Walking speed cutoffs missing in derived targets, skipping speed classification");
			return null;
		}

		WorkoutStatistic distance = findStatistic(workout, "distance_walking_running");
		if (distance == null || distance.sumValue == null || distance.unit == null) {
			return null;
		}

		double miles;
		if (distance.unit.equalsIgnoreCase("mi")) {
			miles = distance.sumValue;
		} else if (distance.unit.equalsIgnoreCase("km")) {
			miles = distance.sumValue / KM_PER_MILE;
		} else {
			return null;
		}

		double mph = miles / (minutes / 60.0);

		if (mph >= vigorousMph) {
			return VIGOROUS;
		} else if (mph >= moderateMph) {
			return MODERATE;
		}
		return LIGHT;
	}
	/**
	 * Rule 3 of classify, the last resort: looks the type up in
	 * activityTypeIntensity. The table is an assumption and can be edited in the
	 * JSON.
	 *
	 * @param type the normalized activity type
	 * @param derivedTargets supplies the table
	 * @return the table's value for the type, or null if the type is not listed
	 */
	private static String intensityByType(String type, DerivedTargets derivedTargets) {
		if (type == null || derivedTargets == null || derivedTargets.activityTypeIntensity == null) {
			return null;
		}
		return derivedTargets.activityTypeIntensity.get(type);
	}
	
	/**
	 * Classifies one workout by trying energy, then speed, then type, and returning
	 * the first result that is not null. Which rule decided is logged at debug by
	 * the rule itself.
	 *
	 * @param workout the workout
	 * @param type the normalized activity type
	 * @param minutes the workout's length in minutes
	 * @param weightKg the user's weight; may be null
	 * @param derivedTargets supplies the cutoffs and tables
	 * @return the intensity, or null if all three rules fail
	 */
	private static String classify(Workout workout, String type, double minutes, Double weightKg,
			DerivedTargets derivedTargets) {
		String intensity = intensityByEnergy(workout, minutes, weightKg, derivedTargets);
		if (intensity != null) {
			return intensity;
		}

		intensity = intensityBySpeed(workout, type, minutes, derivedTargets);
		if (intensity != null) {
			return intensity;
		}

		intensity = intensityByType(type, derivedTargets);
		if (intensity == null) {
			logger.debug("Workout cannot be classified: type={}", type);
		}
		return intensity;
	}

	/**
	 * Produces per-day moderate and vigorous minutes for the window.
	 * 1. Mark every day with step_count readings as covered, with zero minutes.
	 * 2. For each workout, skip it if it has no usable date, falls outside the
	 *    window, or has no usable duration.
	 * 3. Classify it. A result other than light, moderate or vigorous, including an
	 *    unrecognized value from the table, counts as unclassified and is skipped.
	 * 4. Mark the workout's day as covered, then add the minutes to the moderate or
	 *    vigorous slot. A light workout adds nothing but still makes its day covered.
	 * Skips are counted by reason and logged once at debug.
	 *
	 * @param workouts flat list of workouts, each with its statistics; may be null
	 * @param points flat list of point metrics; only step_count is used, for coverage
	 * @param weightKg the user's current weight for the energy rule; may be null,
	 *        in which case speed and type decide
	 * @param derivedTargets supplies the intensity cutoffs and tables
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return a map of covered date to {moderateMinutes, vigorousMinutes}; empty if
	 *         windowStart or derivedTargets is null
	 */
	public static Map<LocalDate, double[]> aggregateDaily(List<Workout> workouts, List<PointMetric> points,
			Double weightKg, DerivedTargets derivedTargets, LocalDate windowStart, int windowDays) {

		Map<LocalDate, double[]> result = new HashMap<>();
		if (windowStart == null || derivedTargets == null) {
			return result;
		}

		Map<LocalDate, Double> summedDays = PointMetricAggregator.sumByDay(points, "step_count", windowStart,
				windowDays);
		summedDays.forEach((date, value) -> result.put(date, new double[] { 0, 0 }));

		int skippedWorkouts = 0;
		int unclassified = 0;

		if (workouts != null) {
			for (Workout w : workouts) {
				LocalDate workoutDate = dateOf(w);
				if (w == null || workoutDate == null) {
					skippedWorkouts++;
					continue;
				}

				int offset = (int) ChronoUnit.DAYS.between(windowStart, workoutDate);
				if (offset < 0 || offset >= windowDays) {
					skippedWorkouts++;
					continue;
				}

				Double workoutMinute = minutesOf(w);
				if (workoutMinute == null) {
					skippedWorkouts++;
					continue;
				}

				String classified = classify(w, normalizeType(w.activityType), workoutMinute, weightKg, derivedTargets);
				if (!LIGHT.equals(classified) && !MODERATE.equals(classified) && !VIGOROUS.equals(classified)) {
					unclassified++;
					logger.debug("Workout not counted: type={} intensity={}", w.activityType, classified);
					continue;
				}

				double[] dayMinutes = result.computeIfAbsent(workoutDate, d -> new double[] { 0, 0 });
				if (MODERATE.equals(classified)) {
					dayMinutes[0] += workoutMinute;
				} else if (VIGOROUS.equals(classified)) {
					dayMinutes[1] += workoutMinute;
				}

				logger.debug("Workout classified: date={} type={} minutes={} intensity={}", workoutDate, w.activityType,
						workoutMinute, classified);
			}
		}

		logger.debug("Aggregated activity: coveredDays={} skippedWorkouts={} unclassified={}", result.size(),
				skippedWorkouts, unclassified);
		return result;
	}

}
