package com.nutrisense.trendanalysis.evaluation.thresholds;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import com.nutrisense.trendanalysis.evaluation.model.*;


public class ThresholdRepository {

	public static String readResourceAsString(String resourcePath) throws IOException {
		try (InputStream in = ThresholdRepository.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Resource not found: " + resourcePath);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private List<ThresholdRow> parseThresholdRows(JsonArray array, Gson gson) {
		List<ThresholdRow> list = new ArrayList<>();
		for (JsonElement element : array) {
			ThresholdRow row = gson.fromJson(element, ThresholdRow.class);
			list.add(row);
		}
		return list;
	}

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

	public StaticThresholds loadStaticThresholds() throws IOException {
		String json = ThresholdRepository.readResourceAsString("/thresholds/static-thresholds.json");
		Gson gson = new Gson();
		JsonObject rootObject = JsonParser.parseString(json).getAsJsonObject();
		
		StaticThresholds thresholds = new StaticThresholds();
		thresholds.micronutrients = parseThresholdRows(rootObject.getAsJsonArray("micronutrients"), gson);
		thresholds.sodium = parseThresholdRows(rootObject.getAsJsonArray("sodium"), gson);
		thresholds.static_macros = parseThresholdRows(rootObject.getAsJsonArray("static_macros"), gson);
		thresholds.activity = parseThresholdRows(rootObject.getAsJsonArray("activity"), gson);
		thresholds.sleep = parseSleepRows(rootObject.getAsJsonArray("sleep"), gson);
		return thresholds;
	}

	public DerivedTargets loadDerivedTargets() throws IOException {
		String json = ThresholdRepository.readResourceAsString("/thresholds/derived-targets.json");
		Gson gson = new Gson();
		return gson.fromJson(json, DerivedTargets.class);
	}

	public BaselineDeviations loadBaselineDeviations() throws IOException {
	    String json = readResourceAsString("/thresholds/baseline-deviations.json");
	    Gson gson = new Gson();
	    return gson.fromJson(json, BaselineDeviations.class);
	}


}
