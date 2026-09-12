%dw 2.0
import * from dwl::thresholds::static_thresholds
// Sonnet 5. 

// The five candidate micronutrient metric keys this dimension ever surfaces.
var micronutrientMetricKeys = ["iron_mg", "zinc_mg", "calcium_mg", "vitamin_b12_mcg", "potassium_mg"]

/**
 * Determines condition_context for a surfaced row, preferring the clinical
 * condition over a dietary constraint when both gates fire simultaneously.
 */
fun resolveConditionContext(row, activeConditions, activeConstraints) = do {
  var ruleConditions = row.surfacing_rule.conditions default []
  var ruleConstraints = row.surfacing_rule.constraints default []
  var matchedCondition = (activeConditions filter (ruleConditions contains $))[0] default null
  var matchedConstraint = (activeConstraints filter (ruleConstraints contains $))[0] default null
  ---
  if (matchedCondition != null) matchedCondition
  else matchedConstraint
}

/**
 * Applies dietary_multiplier when the triggering constraint has one defined,
 * then clamps against 80% of the metric's UL if a UL row exists.
 * Returns the effective threshold to compare the logged value against.
 */
fun applyMultiplierAndClamp(baseRow, effectiveConditionContext, metricKey) = do {
  var multiplierMap = baseRow.dietary_multiplier default {}
  var multiplier = multiplierMap[effectiveConditionContext as String] default 1
  var rawThreshold = baseRow.base_value * multiplier

  var ulRow = (rowsForMetric(metricKey) filter ($.value_type == "UL"))[0] default null
  var ulCeiling = if (ulRow != null) (ulRow.base_value * 0.80) else null
  ---
  if (ulCeiling != null and rawThreshold > ulCeiling) ulCeiling
  else rawThreshold
}

/**
 * Reads the day's logged total for a given micronutrient metric key from the
 * 5a aggregation output. Nutrient field names on dailyTotals are camelCase
 * (ironMg, zincMg, etc.) while metric keys here are snake_case (iron_mg) —
 * this converts between the two.
 */
fun readLoggedValue(dailyTotals, metricKey) = do {
  var camelKey = metricKey splitBy "_" reduce ((word, acc = "") ->
    if (acc == "") word
    else acc ++ (upper(word[0]) ++ word[1 to -1])
  )
  ---
  dailyTotals[camelKey] default null
}

/**
 * Full micronutrient evaluation for one user on one day.
 * Returns only the metrics actually surfaced for this user — everything else
 * is simply absent from the result. status is always null: a single day
 * cannot establish habitual intake adequacy. See habitual_vs_daily in
 * threshold-metadata.dwl.
 */
fun evaluateMicronutrients(resolvedBucket, activeConditions, activeConstraints, dailyTotals) =
  micronutrientMetricKeys
    map ((metricKey) -> do {
      var candidateRows = rowsForMetric(metricKey)
        filter ($.value_type != "UL")
        filter (demographicMatches($.demographic_key, resolvedBucket))
      var row = candidateRows[0] default null
      ---
      if (row == null) null
      else if (not isSurfaced(row, activeConditions, activeConstraints)) null
      else do {
        var conditionContext = resolveConditionContext(row, activeConditions, activeConstraints)
        var threshold = applyMultiplierAndClamp(row, conditionContext, metricKey)
        var loggedValue = readLoggedValue(dailyTotals, metricKey)
        ---
        {
          nutrient: metricKey,
          value: loggedValue,
          threshold: threshold as Number {format: "0.#"},
          condition_context: conditionContext,
          status: null
        }
      }
    })
    filter ($ != null)