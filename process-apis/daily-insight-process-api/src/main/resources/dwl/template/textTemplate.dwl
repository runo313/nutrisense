%dw 2.0

var conditionClauses = {
  anemia: " With anemia in the picture, iron gaps like this are worth paying closer attention to.",
  hypertension: " Given your hypertension, keeping this in check matters more than it might for someone without it.",
  high_cholesterol: " Since you're managing high cholesterol, this one's worth taking seriously.",
  type2_diabetes: " With type 2 diabetes as a factor, swings like this can have a bigger downstream effect.",
  prediabetes: " Since you're navigating prediabetes, it's a good moment to be a bit more mindful here.",
  vegetarian: " On a vegetarian diet, this is one to keep an eye on.",
  vegan: " Following a vegan diet, this is a common gap worth staying ahead of."
}

fun buildConditionClause(conditionContext) =
  if (conditionContext == null) ""
  else conditionClauses[conditionContext as String] default " This is relevant given your health profile."
  
 fun buildCaloriesText(caloriesResult) = do {
  var pct = caloriesResult.pct_of_target as String {format: "0"}
  var isUnder = caloriesResult.pct_of_target < 100
  ---
  if (isUnder)
    "You logged about " ++ pct ++ "% of your calorie target today."
  else
    "You logged about " ++ pct ++ "% of your calorie target today, above what you were aiming for."
}

fun buildSleepText(sleepResult, drivingMetric) = do {
  var status = sleepResult.status
  ---
  if (drivingMetric == "sleep_duration_hrs")
    "You slept about " ++ (sleepResult.duration_hrs as String {format: "0.#"}) ++ " hours last night, against a target of " ++ (sleepResult.target_hrs as String {format: "0.#"}) ++ " hours."
  else if (drivingMetric == "sleep_rem_pct")
    "REM sleep made up about " ++ (sleepResult.rem_pct as String {format: "0.#"}) ++ "% of your night."
  else
    "Deep sleep made up about " ++ (sleepResult.deep_pct as String {format: "0.#"}) ++ "% of your night."
}

fun buildProteinText(proteinResult) = 
  if (proteinResult.status == "hard_flag")
    "You logged about " ++ (proteinResult.logged_value_g as String {format: "0.#"}) ++ "g of protein today, below the general recommended minimum of " ++ (proteinResult.hard_bound_g as String {format: "0.#"}) ++ "g."
  else
    "You logged about " ++ (proteinResult.logged_value_g as String {format: "0.#"}) ++ "g of protein today, below your target of " ++ (proteinResult.target_g as String {format: "0.#"}) ++ "g."
    
fun buildRecoveryText(recoveryResult, drivingMetric) =
  if (drivingMetric == "resting_hr_bpm")
    "Your resting heart rate today was about " ++ (recoveryResult.resting_hr_bpm as String {format: "0.#"}) ++ " bpm, compared to your usual average of " ++ (recoveryResult.baseline_hr_bpm as String {format: "0.#"}) ++ " bpm."
  else
    "Your HRV today was about " ++ (recoveryResult.hrv_ms as String {format: "0.#"}) ++ " ms, compared to your usual average of " ++ (recoveryResult.baseline_hrv_ms as String {format: "0.#"}) ++ " ms."
    
fun buildCarbohydrateText(carbResult) = do {
  var isRangeIssue = carbResult.amdr_status != "on_track"
  var isBelow = carbResult.logged_value_g < carbResult.range_min_g
  ---
  if (isRangeIssue and isBelow)
    "You logged about " ++ (carbResult.logged_value_g as String {format: "0.#"}) ++ "g of carbohydrates today, below the general recommended range of " ++ (carbResult.range_min_g as String {format: "0.#"}) ++ "–" ++ (carbResult.range_max_g as String {format: "0.#"}) ++ "g."
  else if (isRangeIssue)
    "You logged about " ++ (carbResult.logged_value_g as String {format: "0.#"}) ++ "g of carbohydrates today, above the general recommended range of " ++ (carbResult.range_min_g as String {format: "0.#"}) ++ "–" ++ (carbResult.range_max_g as String {format: "0.#"}) ++ "g."
  else
    "You logged about " ++ (carbResult.logged_value_g as String {format: "0.#"}) ++ "g of carbohydrates today, below the minimum generally needed to support normal brain function."
}

fun buildFatText(fatResult) = do {
  var isBelow = fatResult.logged_value_g < fatResult.range_min_g
  ---
  if (isBelow)
    "You logged about " ++ (fatResult.logged_value_g as String {format: "0.#"}) ++ "g of fat today, below the general recommended range of " ++ (fatResult.range_min_g as String {format: "0.#"}) ++ "–" ++ (fatResult.range_max_g as String {format: "0.#"}) ++ "g."
  else
    "You logged about " ++ (fatResult.logged_value_g as String {format: "0.#"}) ++ "g of fat today, above the general recommended range of " ++ (fatResult.range_min_g as String {format: "0.#"}) ++ "–" ++ (fatResult.range_max_g as String {format: "0.#"}) ++ "g."
}

fun buildSaturatedFatText(satFatResult) = 
  "You logged about " ++ (satFatResult.logged_value_g as String {format: "0.#"}) ++ "g of saturated fat today, above today's limit of " ++ (satFatResult.cap_g as String {format: "0.#"}) ++ "g." ++ buildConditionClause(satFatResult.condition_context)

fun buildSodiumText(sodiumResult) = 
  "You logged about " ++ (sodiumResult.logged_value_mg as String {format: "0.#"}) ++ "mg of sodium today, above today's limit of " ++ (sodiumResult.cap_mg as String {format: "0.#"}) ++ "mg." ++ buildConditionClause(sodiumResult.condition_context)
  
fun buildFiberText(fiberResult) = 
  "You logged about " ++ (fiberResult.logged_value_g as String {format: "0.#"}) ++ "g of fiber today, below the general recommended target of " ++ (fiberResult.target_g as String {format: "0.#"}) ++ "g."
  
  var crossSignalTemplates = {
  recovery_stress: "Your sleep was shorter than usual and your resting heart rate was above your typical average today. Together, these can point to your body needing more recovery time.",
  energy_deficit: "You logged well under your calorie target today while also burning more active energy than usual. That combination can leave you running on empty.",
  recovery_risk: "Your protein intake was below target today alongside a higher-than-usual active energy burn. For performance goals, this combination can slow recovery.",
  blood_sugar_disruption_risk: "Your carbohydrate intake was on the higher side today and your sleep was also short. Together, these can affect blood sugar regulation.",
  cardiovascular_stress: "Your sodium intake was above today's limit and your resting heart rate was elevated. Together, these are worth keeping an eye on.",
  lipid_profile_risk: "Your fiber intake was low today while saturated fat was above the limit. Together, this combination is worth attention given your cholesterol management."
}

fun buildCrossSignalText(key) = crossSignalTemplates[key] default "A combination of today's signals is worth a closer look."