%dw 2.0

fun evaluateFiber(loggedFiberG, resolvedTarget) = do {
  var pctOfTarget = (loggedFiberG / resolvedTarget.target_g) * 100
  var status =
    if (loggedFiberG >= resolvedTarget.target_g) "on_track"
    else if (loggedFiberG >= resolvedTarget.hard_bound_g) "soft_warning"
    else "hard_flag"
  ---
  {
    metric_key: "fiber_g",
    logged_value_g: loggedFiberG as Number {format: "0.#"},
    target_g: resolvedTarget.target_g,
    hard_bound_g: resolvedTarget.hard_bound_g,
    pct_of_target: pctOfTarget as Number {format: "0.#"},
    status: status,
    direction_note: "Lower-bound only. No upper flag exists for fiber intake.",
    confidence: resolvedTarget.confidence,
    source_body: resolvedTarget.source_body,
    source_edition: resolvedTarget.source_edition,
    notes: resolvedTarget.notes
  }
}