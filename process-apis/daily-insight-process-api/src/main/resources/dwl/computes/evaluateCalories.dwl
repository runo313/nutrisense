%dw 2.0
import * from dwl::thresholds::derived_targets
fun evaluateCalories(loggedKcal, caloricTarget)= if (caloricTarget == null) { 
	metric_key: "energy_kcal", 
	status: "no_data", 
	notes: "Caloric target was not be computed"
} else do {
	var pctOfTarget = loggedKcal / caloricTarget
	var status =
    if (pctOfTarget >= caloricDeviationBounds.on_track_min_pct 
        and pctOfTarget <= caloricDeviationBounds.on_track_max_pct) "on_track"
    else if (pctOfTarget >= caloricDeviationBounds.soft_min_pct 
        and pctOfTarget <= caloricDeviationBounds.soft_max_pct) "soft_warning"
    else "hard_flag"
    ---
    {
    metric_key: "energy_kcal",
    logged_value: loggedKcal,
    target: caloricTarget,
    pct_of_target: (pctOfTarget * 100) as Number {format: "0.#"},
    status: status,
    direction_note: "Being under target is never framed as success regardless of goal_direction.",
    confidence: "heuristic",
    source_body: null,
    notes: "No sourced clinical value exists for calorie deviation tolerance."
  }
}