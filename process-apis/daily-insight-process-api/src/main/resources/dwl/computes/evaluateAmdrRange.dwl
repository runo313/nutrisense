%dw 2.0
// a shared function parameterized by metric_key. Used for total fat and carbohydrates. 

fun evaluateAmdrRange(loggedValueG, resolvedRange, metricKey) = do {
  var status =
    if (loggedValueG >= resolvedRange.range_min_g and loggedValueG <= resolvedRange.range_max_g) "on_track"
    else if (loggedValueG >= resolvedRange.hard_below_g and loggedValueG <= resolvedRange.hard_above_g) "soft_warning"
    else "hard_flag"
  ---
  {
    metric_key: metricKey,
    logged_value_g: loggedValueG as Number {format: "0.#"},
    range_min_g: resolvedRange.range_min_g,
    range_max_g: resolvedRange.range_max_g,
    hard_below_g: resolvedRange.hard_below_g,
    hard_above_g: resolvedRange.hard_above_g,
    status: status,
    direction_note: "Bidirectional. Both too little and too much are flagged relative to the AMDR range.",
    confidence: resolvedRange.confidence,
    source_body: resolvedRange.source_body,
    source_edition: resolvedRange.source_edition,
    notes: resolvedRange.notes
  }
}