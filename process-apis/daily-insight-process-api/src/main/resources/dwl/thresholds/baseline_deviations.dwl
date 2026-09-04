%dw 2.0

/**
 * NutriSense Reference Thresholds — Category 3: Personal Baseline Deviation
 *
 * Metrics with no population reference value. A number is only meaningful
 * relative to the individual's own recent history.
 *
 * Every bound in this module is heuristic. source_body is null throughout,
 * deliberately — no published body defines these deviations.
 *
 * DEPENDENCY: baselines are computed by the Wearable System API summary
 * endpoint, which exposes average, min, max and count. It exposes neither
 * median nor standard deviation, so baseline_statistic is "average" and
 * bound_mode is "pct_of_base" for every row here. sd_from_baseline is not a
 * supported bound mode.
 */

var baselineRows = [
  {
    threshold_id: "hrv_sdnn_baseline_deviation",
    dimension: "recovery",
    metric_key: "hrv_sdnn",
    value_type: "personal_baseline_deviation",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: null,
    bound_mode: "pct_of_base",
    soft_bound: -0.15,
    hard_bound: -0.25,
    unit: "ms",
    baseline_window_days: 30,
    baseline_statistic: "average",
    min_data_days: 14,
    missing_day_handling: "exclude",
    measurement_context: "mixed",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    primary_evaluation: true,
    source_body: null,
    source_edition: null,
    source_url: null,
    confidence: "heuristic",
    notes: "HealthKit provides SDNN specifically; RMSSD from another source is not comparable and must not be substituted. Bands are wider than resting HR because HRV is a noisier signal — day-to-day swings of 10-15% are unremarkable. That asymmetry is deliberate and does not imply HRV matters less. High HRV is not flagged. Apple samples opportunistically rather than under standardised conditions, so measurement_context is recorded as mixed and held constant rather than corrected for."
  },
  {
    threshold_id: "resting_hr_baseline_deviation",
    dimension: "recovery",
    metric_key: "resting_hr_bpm",
    value_type: "personal_baseline_deviation",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: null,
    bound_mode: "pct_of_base",
    soft_bound: 0.05,
    hard_bound: 0.10,
    unit: "bpm",
    baseline_window_days: 30,
    baseline_statistic: "average",
    min_data_days: 14,
    missing_day_handling: "exclude",
    measurement_context: "mixed",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    primary_evaluation: true,
    source_body: null,
    source_edition: null,
    source_url: null,
    confidence: "heuristic",
    notes: "At a 60 bpm baseline, 5% is roughly 3 bpm (within ordinary daily fluctuation) and 10% is roughly 6 bpm (a genuine signal). Low resting HR is not flagged — it is common in trained individuals. The AHA 60-100 bpm population range is not used as a bound here; a person whose baseline is 52 sitting at 68 is meaningfully elevated for them while remaining inside the population band entirely."
  }
]

/**
 * Known property, documented rather than corrected for: a flat percentage does
 * not adapt to individual variability. A user with very stable HRV and one with
 * erratic HRV receive the same band. An SD-based band would adapt, but SD is not
 * computable from the Wearable API's current surface. Simpler and more
 * predictable, less sensitive per person.
 */
var knownLimitations = [
  "Flat percentage bands do not adapt to individual variability.",
  "Average is more sensitive to outliers than median; a single anomalous day pulls the baseline.",
  "A 30-day window with a 14-day minimum tolerates gaps but not sparse history."
]

fun baselineRowFor(metricKey) =
  (baselineRows filter ($.metric_key == metricKey))[0] default null

/**
 * Returns no_data rather than a comparison when history is too thin.
 * dayCount comes from the `count` field of the Wearable summary response.
 * Never impute zero for a missing day — exclude it.
 */
fun hasSufficientHistory(metricKey, dayCount) = do {
  var row = baselineRowFor(metricKey)
  ---
  if (row == null) false else (dayCount default 0) >= row.min_data_days
}

/**
 * Resolves absolute bounds from a baseline value.
 * Lower-direction rows: bounds fall below baseline. Upper: above.
 */
fun resolveBaselineBounds(metricKey, baselineValue) = do {
  var row = baselineRowFor(metricKey)
  ---
  if (row == null or baselineValue == null) null
  else {
    metric_key: row.metric_key,
    unit: row.unit,
    direction: row.direction,
    baseline_value: baselineValue,
    baseline_statistic: row.baseline_statistic,
    baseline_window_days: row.baseline_window_days,
    soft_bound: (baselineValue * (1 + row.soft_bound)) as Number {format: "0.#"},
    hard_bound: (baselineValue * (1 + row.hard_bound)) as Number {format: "0.#"},
    confidence: row.confidence,
    source_body: null,
    notes: row.notes
  }
}
