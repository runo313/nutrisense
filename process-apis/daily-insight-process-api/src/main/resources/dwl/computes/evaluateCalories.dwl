%dw 2.0
import * from dwl::thresholds::derived_targets


// ---------------------------------------------------------------------------
// Caloric target — Mifflin-St Jeor
// ---------------------------------------------------------------------------

var activityMultipliers = {
  sedentary: 1.2,
  lightly_active: 1.375,
  moderately_active: 1.55,
  very_active: 1.725,
  athlete: 1.9
}

/**
 * Returns null for biological_sex values the equation does not cover.
 * The formula cannot produce a meaningful result without sex; assuming one
 * would produce a confident wrong number rather than an honest absence.
 */
fun computeCaloricTarget(weightKg, heightCm, age, biologicalSex, activityBaseline) = do {
  var bmr =
    if (biologicalSex == "male")   (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
    else if (biologicalSex == "female") (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
    else null
  var multiplier = activityMultipliers[activityBaseline as String]
  ---
  if (bmr != null and multiplier != null) (bmr * multiplier) as Number {format: "0"} as Number
  else null
}


var caloricTargetMeta = {
  metric_key: "energy_kcal",
  unit: "kcal",
  direction: "bidirectional",
  evaluation_scope: "daily",
  source_body: "Mifflin-St Jeor",
  source_edition: "Mifflin et al. 1990",
  source_url: "https://pubmed.ncbi.nlm.nih.gov/2305711/",
  confidence: "guideline",
  notes: "Direction of concern depends on goal_direction, but both directions are flagged. Being under a computed target is never framed as success. See under_target_framing guard."
}

/**
 * From the profile API, vars.userProfile is created storing daily_caloric_target computed by Mifflin-St Jeor e.g 2100
 * vars.nutrientAggregation stores dailyTotals.energyKcal e.g 1028
 * Being under a computed target is never framed as success.
 * A user eating 1028 kcal against a 2100 target isn't "doing great at losing weight," they're under-fueling by more than half.
 * So the actual evaluation id derived by how large is the deviation using a percentage-deviation threshold
 */
var caloricDeviationBounds = {
  on_track_min_pct: 0.80,
  on_track_max_pct: 1.20,
  soft_min_pct: 0.60,
  soft_max_pct: 1.40
}


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