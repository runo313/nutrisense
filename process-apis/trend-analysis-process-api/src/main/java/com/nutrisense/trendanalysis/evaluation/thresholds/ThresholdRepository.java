package com.nutrisense.trendanalysis.evaluation.thresholds;

import com.google.gson.Gson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.*;

/**
 * Loads and caches the three bundled threshold reference files
 * (static-thresholds.json, derived-targets.json, baseline-deviations.json)
 * from the classpath. Each file is parsed once and held in memory for the
 * lifetime of this instance. repeated calls to any load* method return the
 * cached result rather than re-reading and re-parsing the file.
 *
 * This class provides what the reference data says
 */

public class ThresholdRepository {

	private static final Logger logger = LoggerFactory.getLogger(ThresholdRepository.class);
	private StaticThresholds cachedStaticThresholds;
	private DerivedTargets cachedDerivedTargets;
	private BaselineDeviations cachedBaselineDeviations;
	private List<BaseThresholdRow> cachedAllStaticRows;

	/**
	 * Reads a classpath resource (expected under src/main/resources) into a
	 * single UTF-8 string.
	 *
	 * @param resourcePath classpath-relative path, e.g. "/thresholds/static-thresholds.json"
	 * @return the full file contents as a string
	 * @throws IOException if the resource does not exist on the classpath
	 */
	public static String readResourceAsString(String resourcePath) throws IOException {
		try (InputStream in = ThresholdRepository.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				logger.error("Threshold resource not found on classpath: {}", resourcePath);
				throw new IOException("Resource not found: " + resourcePath);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Parses a JSON array of uniformly-shaped rows (soft/hard bound, no range)
	 * directly into ThresholdRow objects via Gson's annotation-based mapping.
	 * Used for every static-thresholds.json section except sleep, which mixes
	 * in range-shaped rows and needs per-row type branching instead.
	 *
	 * @param array the JSON array for one section, e.g. "micronutrients"
	 * @param gson  shared Gson instance used for the mapping
	 * @return one ThresholdRow per JSON element, in source order
	 */

	private List<ThresholdRow> parseThresholdRows(JsonArray array, Gson gson) {
		List<ThresholdRow> list = new ArrayList<>();
		for (JsonElement element : array) {
			ThresholdRow row = gson.fromJson(element, ThresholdRow.class);
			list.add(row);
		}
		return list;
	}
	
	/**
	 * Parses the sleep section specifically, since it mixes two row shapes:
	 * the duration row (a plain ThresholdRow) and the deep/REM percentage rows
	 * (RangeThresholdRow). Branches on each row's value_type to decide which
	 * class to deserialize into. "range" means RangeThresholdRow, anything
	 * else means ThresholdRow.
	 *
	 * @param array the "sleep" JSON array
	 * @param gson  shared Gson instance used for the mapping
	 * @return a mixed list of ThresholdRow and RangeThresholdRow, as BaseThresholdRow
	 */
	private List<BaseThresholdRow> parseSleepRows(JsonArray array, Gson gson) {
		List<BaseThresholdRow> list = new ArrayList<>();
		for (JsonElement element : array) {
			JsonObject obj = element.getAsJsonObject();
			String valueType = obj.has("value_type") && !obj.get("value_type").isJsonNull()
					? obj.get("value_type").getAsString()
					: null;
			BaseThresholdRow row;
			if ("range".equals(valueType)) {
				row = gson.fromJson(obj, RangeThresholdRow.class);
			} else {
				row = gson.fromJson(obj, ThresholdRow.class);
			}
			list.add(row);
		}
		return list;
	}
	
	/**
	 * Loads static-thresholds.json on first call and caches the result for
	 * every subsequent call. Also builds and caches a flattened list of every
	 * row across all five sections.
	 *
	 * @return the fully parsed static thresholds, grouped by section
	 * @throws IOException if the underlying JSON resource cannot be read
	 */
	public StaticThresholds loadStaticThresholds() throws IOException {
		if (cachedStaticThresholds != null) {
			logger.debug("Returning cached static thresholds ({} rows)", cachedAllStaticRows.size());
			return cachedStaticThresholds;
		}

		logger.info("Loading static thresholds from classpath");

		String json = ThresholdRepository.readResourceAsString("/thresholds/static-thresholds.json");
		Gson gson = new Gson();
		JsonObject rootObject = JsonParser.parseString(json).getAsJsonObject();

		StaticThresholds thresholds = new StaticThresholds();
		thresholds.micronutrients = parseThresholdRows(rootObject.getAsJsonArray("micronutrients"), gson);
		thresholds.sodium = parseThresholdRows(rootObject.getAsJsonArray("sodium"), gson);
		thresholds.static_macros = parseThresholdRows(rootObject.getAsJsonArray("static_macros"), gson);
		thresholds.activity = parseThresholdRows(rootObject.getAsJsonArray("activity"), gson);
		thresholds.sleep = parseSleepRows(rootObject.getAsJsonArray("sleep"), gson);

		cachedStaticThresholds = thresholds;

		// build flattened list
		List<BaseThresholdRow> all = new ArrayList<>();
		all.addAll(thresholds.micronutrients);
		all.addAll(thresholds.sodium);
		all.addAll(thresholds.static_macros);
		all.addAll(thresholds.sleep);
		all.addAll(thresholds.activity);
		cachedAllStaticRows = all;

		logger.info(
				"Loaded static thresholds: {} micronutrient rows, {} sodium rows, {} macro rows, {} sleep rows, {} activity rows ({} total)",
				thresholds.micronutrients.size(), thresholds.sodium.size(), thresholds.static_macros.size(),
				thresholds.sleep.size(), thresholds.activity.size(), all.size());

		return cachedStaticThresholds;
	}
	
	/**
	 * Loads derived-targets.json on first call and caches the result.
	 *
	 * @return the fully parsed derived targets
	 * @throws IOException if the underlying JSON resource cannot be read
	 */

	public DerivedTargets loadDerivedTargets() throws IOException {
		if (cachedDerivedTargets != null) {
			logger.debug("Returning cached derived targets");
			return cachedDerivedTargets;
		}

		logger.info("Loading derived targets from classpath");
		String json = ThresholdRepository.readResourceAsString("/thresholds/derived-targets.json");
		Gson gson = new Gson();
		DerivedTargets targets = gson.fromJson(json, DerivedTargets.class);

		cachedDerivedTargets = targets;

		logger.info("Loaded derived targets: {} protein coefficients, {} AMDR ranges, {} saturated fat rows",
				targets.proteinCoefficients.size(), targets.amdrRanges.size(), targets.saturatedFat.size());

		return cachedDerivedTargets;
	}
	
	/**
	 * Loads baseline-deviations.json on first call and caches the result.
	 * Covers the three metrics with no population reference at all: HRV,
	 * resting heart rate, and active energy each evaluated as a percentage
	 * deviation from the user's own 30-day average.
	 *
	 * @return the fully parsed baseline deviation rows
	 * @throws IOException if the underlying JSON resource cannot be read
	 */

	public BaselineDeviations loadBaselineDeviations() throws IOException {
		if (cachedBaselineDeviations != null) {
			logger.debug("Returning cached baseline deviations");
			return cachedBaselineDeviations;
		}

		logger.info("Loading baseline deviations from classpath");
		String json = readResourceAsString("/thresholds/baseline-deviations.json");
		Gson gson = new Gson();
		BaselineDeviations deviations = gson.fromJson(json, BaselineDeviations.class);

		cachedBaselineDeviations = deviations;

		logger.info("Loaded baseline deviations for hrv_sdnn, resting_hr_bpm, active_energy_kcal");

		return cachedBaselineDeviations;
	}
	
	/**
	 * Returns the flattened list of every static threshold row across all
	 * five sections, triggering a load if one hasn't happened yet. This is
	 * the entry point ThresholdResolver uses to search across metrics without
	 * needing to know which section (micronutrients, sodium, etc.) a given
	 * metric key belongs to.
	 *
	 * @return every parsed row from static-thresholds.json, in load order
	 * @throws IOException if static-thresholds.json cannot be read
	 */

	public List<BaseThresholdRow> getAllStaticRows() throws IOException {
		if (cachedAllStaticRows == null) {
			loadStaticThresholds();
		}
		return cachedAllStaticRows;
	}


}
