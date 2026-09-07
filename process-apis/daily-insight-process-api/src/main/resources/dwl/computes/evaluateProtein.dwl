%dw 2.0

fun evaluateProtein(loggedProteinG, resolvedTarget) = do {
  var pctOfTarget = (loggedProteinG / resolvedTarget.target_g) * 100
  var status =
    if (loggedProteinG >= resolvedTarget.target_g) "on_track"
    else if (loggedProteinG >= resolvedTarget.hard_bound_g) "soft_warning"
    else "hard_flag"
  ---
  {
    metric_key: "protein_g",
    logged_value_g: loggedProteinG as Number {format: "0.#"},
    target_g: resolvedTarget.target_g,
    hard_bound_g: resolvedTarget.hard_bound_g,
    pct_of_target: pctOfTarget as Number {format: "0.#"},
    status: status,
    direction_note: "Lower-bound only. Exceeding range_max_g is not flagged. no evidence base for penalizing high protein intake in healthy adults.",
    confidence: resolvedTarget.confidence,
    source_body: resolvedTarget.source_body,
    source_edition: resolvedTarget.source_edition,
    notes: resolvedTarget.notes
  }
}