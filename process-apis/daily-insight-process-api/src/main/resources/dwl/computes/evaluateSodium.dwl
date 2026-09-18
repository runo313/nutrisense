%dw 2.0 
import * from dwl::thresholds::static_thresholds

fun resolveSodiumTarget(activeConditions) = do {
  var rows = rowsForMetric("sodium_mg")
  var override = rows filter ((r) -> r.condition_override_of != null and (activeConditions contains r.surfacing_rule.conditions[0]))
  var selected = if (sizeOf(override) > 0) override[0] else (rows filter ($.condition_override_of == null))[0]
  ---
  {
    metric_key: "sodium_mg",
    unit: "mg",
    direction: "upper",
    cap_mg: selected.base_value,
    hard_bound_mg: selected.hard_bound,
    condition_context: if (sizeOf(override) > 0) selected.surfacing_rule.conditions[0] else null,
    source_body: selected.source_body,
    source_edition: selected.source_edition,
    confidence: selected.confidence,
    notes: selected.notes
  }
}

fun evaluateSodium(loggedSodiumMg, resolvedTarget) = if ( loggedSodiumMg == null ) {
	metric_key: "sodium_mg",
	status: "no_data",
	notes: "logged SodiumMG was not be computed"
}else do {
  var pctOfCap = (loggedSodiumMg / resolvedTarget.cap_mg) * 100
  var status =
    if (loggedSodiumMg <= resolvedTarget.cap_mg) "on_track"
    else if (loggedSodiumMg <= resolvedTarget.hard_bound_mg) "soft_warning"
    else "hard_flag"
  ---
  {
    metric_key: "sodium_mg",
    logged_value_mg: loggedSodiumMg as Number {format: "0.#"},
    cap_mg: resolvedTarget.cap_mg,
    hard_bound_mg: resolvedTarget.hard_bound_mg,
    pct_of_cap: pctOfCap as Number {format: "0.#"},
    status: status,
    condition_context: resolvedTarget.condition_context,
    direction_note: "Upper-bound only, mirrors saturated fat's shape.",
    confidence: resolvedTarget.confidence,
    source_body: resolvedTarget.source_body,
    source_edition: resolvedTarget.source_edition,
    notes: resolvedTarget.notes
  }
}