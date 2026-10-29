package com.nutrisense.trendanalysis.evaluation.evaluators;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.SleepSession;
/**
 * Builds nightly sleep totals from a window's raw sleep sessions. The
 * SleepEvaluator calls it once per request and then averages the result over
 * nights that have data.
 *
 * Only deep, core, rem and unspecified stages count toward sleep. Anything
 * else, including awake, is excluded, so a new stage value from the Wearable
 * API cannot silently inflate sleep. Each session is assigned to the night it
 * belongs to by wake date, using a noon cutoff.
 *
 * Known limitations: times are read as local without timezone conversion, so a
 * DST change can shift one night by an hour. A nap started after noon counts
 * toward the next night. Overlapping sessions from two devices are summed,
 * which double-counts, because SleepSession does not carry source_device.
 *
 * Stateless. Fetching the sessions is the caller's job.
 */

public class SleepAggregator {
	private static final Logger logger = LoggerFactory.getLogger(SleepAggregator.class);
	private static final Set<String> COUNTED_STAGES = Set.of("deep", "core", "rem", "unspecified");

	/**
	 * Step 1 of aggregateNightly: decides whether a session's stage counts as sleep.
	 *
	 * @param stage the stage name from the Wearable API, compared case-insensitively
	 * @return true for deep, core, rem or unspecified; false for anything else,
	 *         including null
	 */
    private static boolean isCountedStage(String stage) {
        return stage != null && COUNTED_STAGES.contains(stage.toLowerCase());
    }

    /**
     * Parses the first 19 characters of an ISO timestamp (yyyy-MM-ddTHH:mm:ss) as
     * a local date-time, ignoring any offset. Sleep needs the time of day, so the
     * date-only approach used for meals does not work here.
     *
     * @param iso the raw timestamp string
     * @return the local date-time, or null if the string is null, too short, or unparseable
     */
	private static LocalDateTime parseLocal(String iso) {
		if (iso == null || iso.length() < 19) {
			return null;
		}
		try {
			return LocalDateTime.parse(iso.substring(0, 19));

		} catch (DateTimeParseException e) {
			logger.debug("Unparseable date: date={}", iso);
			return null;
		}
	}
	/**
	 * Assigns a session to the night it belongs to by shifting at noon. A session
	 * starting before noon belongs to its own date, and one starting at or after
	 * noon belongs to the next date. A 23:00 session on the 13th and a 03:12
	 * session on the 14th both land on the 14th, the wake date.
	 *
	 * @param start the session start time
	 * @return the night's date, or null if start is null
	 */
	private static LocalDate nightOf(LocalDateTime start) {
		if (start == null) {
			return null;
		}

		LocalDate startDate = start.toLocalDate();
		LocalTime startTime = start.toLocalTime();
		return (startTime.isBefore(LocalTime.NOON)) ? startDate : startDate.plusDays(1);
	}
	/**
	 * Computes one session's length in hours.
	 *
	 * @param session the sleep session
	 * @return hours as seconds divided by 3600, or null if either time is
	 *         unparseable or the end is not after the start. Bad intervals are
	 *         skipped, never counted as zero.
	 */

	private static Double hoursOf(SleepSession session) {
		LocalDateTime startTime = parseLocal(session.startTime);
		LocalDateTime endTime = parseLocal(session.endTime);

		if (startTime == null || endTime == null) {
			return null;
		}
		
		if (!endTime.isAfter(startTime)) {
			return null;
		}
		
		double duration = Duration.between(startTime, endTime).toSeconds() / 3600.0;
		return duration;
	}
	
	/**
	 * Produces one total per night of counted sleep hours.
	 * 1. isCountedStage - skip null sessions and stages that do not count.
	 * 2. parseLocal and hoursOf - skip sessions with an unusable start or length.
	 * 3. nightOf - assign the session to its night.
	 * 4. window check - skip nights outside windowStart to windowStart plus
	 *    windowDays, so a stray session cannot change the coverage count.
	 * 5. merge - add the hours to that night's running total.
	 *
	 * A night appears only if at least one session counted, so the map's size is
	 * the night count CoverageGate needs. Skips are counted per reason and logged
	 * once at debug.
	 *
	 * @param sessions flat list of sleep sessions; the caller should fetch from the
	 *                 day before windowStart so the first night is not partial
	 * @param windowStart first calendar day of the window
	 * @param windowDays window length (7, 14, or 30)
	 * @return total hours per night with usable data; empty if there are no
	 *         sessions or no window start
	 */
	
	public static Map<LocalDate, Double> aggregateNightly(List<SleepSession> sessions, LocalDate windowStart, int windowDays){
		Map<LocalDate, Double> result = new HashMap<>();
		
		if (sessions == null || sessions.isEmpty() || windowStart == null) {
            logger.debug("Nothing to aggregate for sleep: sessions or windowStart missing");
            return result;
        }
		
		int skippedStage = 0;
        int skippedBadTime = 0;
        int skippedOutsideWindow = 0;
		
		for (SleepSession session : sessions ) {
			if (session == null || !isCountedStage(session.stage)){
				skippedStage++;
				continue;
			}
			
			LocalDateTime parsedStart = parseLocal(session.startTime);
			Double sleptHours = hoursOf(session);
			if (parsedStart == null || sleptHours == null) {
                skippedBadTime++;
                continue;
            }
			
			LocalDate night = nightOf(parsedStart);
            int offset = (int) ChronoUnit.DAYS.between(windowStart, night);
            if (offset < 0 || offset >= windowDays) {
                skippedOutsideWindow++;
                continue;
            }

            result.merge(night, sleptHours, Double::sum);
		}
		logger.debug("Aggregated sleep: nights={} skippedStage={} skippedBadTime={} skippedOutsideWindow={}",
                result.size(), skippedStage, skippedBadTime, skippedOutsideWindow);
        return result;
	}
}
