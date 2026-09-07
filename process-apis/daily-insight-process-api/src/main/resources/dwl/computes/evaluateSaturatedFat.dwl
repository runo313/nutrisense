%dw 2.0

fun evaluateSaturatedFat(loggedSatFatG, resolvedTarget) = do {
  var pctOfCap = (loggedSatFatG / resolvedTarget.cap_g) * 100
  var status =
    if (loggedSatFatG <= resolvedTarget.cap_g) "on_track"
    else if (loggedSatFatG <= resolvedTarget.hard_bound_g) "soft_warning"
    else "hard_flag"
  ---
  {
    metric_key: "saturated_fat_g",
    logged_value_g: loggedSatFatG as Number {format: "0.#"},
    cap_g: resolvedTarget.cap_g,
    hard_bound_g: resolvedTarget.hard_bound_g,
    pct_of_cap: pctOfCap as Number {format: "0.#"},
    status: status,
    condition_context: resolvedTarget.condition_context,
    direction_note: "Upper-bound only. No floor exists — low saturated fat intake is never flagged.",
    confidence: resolvedTarget.confidence,
    source_body: resolvedTarget.source_body,
    source_edition: resolvedTarget.source_edition,
    notes: resolvedTarget.notes
  }
}