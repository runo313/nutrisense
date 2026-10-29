%dw 2.0
// Sonnet 5.5
// Wording tables
var conditionClauses = {
  anemia: " With anemia in the picture, iron gaps like this are worth paying closer attention to.",
  hypertension: " Given your hypertension, keeping this in check matters more than it might for someone without it.",
  high_cholesterol: " Since you're managing high cholesterol, this one's worth taking seriously.",
  type2_diabetes: " With type 2 diabetes as a factor, swings like this can have a bigger downstream effect.",
  prediabetes: " Since you're navigating prediabetes, it's a good moment to be a bit more mindful here.",
  vegetarian: " On a vegetarian diet, this is one to keep an eye on.",
  vegan: " Following a vegan diet, this is a common gap worth staying ahead of."
}

// trend_direction is better/worse, not up/down, so the clauses say closer/further.
var trendText = {
  nutrition: {
    improving: " That moved closer to target in the second half of the window.",
    declining: " That moved further from target in the second half of the window.",
    stable: " That stayed about the same across the window."
  },
  sleep: {
    improving: " Your later nights were closer to your target.",
    declining: " Your later nights fell further from your target.",
    stable: " Your nights were about the same throughout."
  },
  activity: {
    improving: " Recorded activity was higher in the second half.",
    declining: " Recorded activity was lower in the second half.",
    stable: " Recorded activity was about even across the window."
  },
  recovery: {
    improving: " Readings were moving in a better direction in the second half.",
    declining: " At least one reading moved in the wrong direction over the window.",
    stable: " Readings were steady across the window."
  }
}

var nutrientInfo = {
  iron_mg: { name: "iron", unit: "mg" },
  zinc_mg: { name: "zinc", unit: "mg" },
  calcium_mg: { name: "calcium", unit: "mg" },
  vitamin_b12_mcg: { name: "vitamin B12", unit: "mcg" },
  potassium_mg: { name: "potassium", unit: "mg" }
}

var macroNames = {
  protein: "protein", carbohydrates: "carbohydrates", fat: "fat",
  saturated_fat: "saturated fat", fiber: "fiber", sodium: "sodium"
}

var macroUnits = {
  protein: "g", carbohydrates: "g", fat: "g", saturated_fat: "g", fiber: "g", sodium: "mg"
}

// Helpers (round is half-up, which avoids DecimalFormat's half-even surprises)
fun fmt0(n) = if (n == null) "" else (round(n)) as String {format: "0"}
fun fmt1(n) = if (n == null) "" else (round(n * 10) / 10) as String {format: "0.#"}
fun fmtMacro(name, n) = if (name == "sodium") fmt0(n) else fmt1(n)
fun isFlagged(s) = s == "soft_warning" or s == "hard_flag"

fun contextClause(ctx) =
  if (ctx == null) ""
  else (conditionClauses[ctx as String] default " This is relevant given your health profile.")

fun trendClause(family, direction) =
  if (direction == null) ""
  else (trendText[family][direction as String] default "")

// One observation per driver. Each returns { text, family, direction } or null
// when the block or a field it needs is missing.
fun caloriesObs(b, days) =
  if (b == null or b.avg_calories == null or b.caloric_target == null or b.caloric_target == 0) null
  else do {
    var pct = (b.avg_calories / b.caloric_target) * 100
    ---
    {
      text: "Over the past $(days) days you averaged about $(fmt0(b.avg_calories)) kcal a day on the days you logged, roughly $(fmt0(pct))% of your $(fmt0(b.caloric_target)) kcal target"
            ++ (if (pct > 100) ", above what you were aiming for." else "."),
      family: "nutrition",
      direction: b.trend_direction
    }
  }

fun sleepObs(b, days) =
  if (b == null or b.avg_duration_hrs == null) null
  else do {
    var durationFlagged = isFlagged(b.duration_status)
    var irregular = b.consistency == "irregular"
    var base = "You averaged about $(fmt1(b.avg_duration_hrs)) hours of sleep a night over the past $(days) days"
    ---
    {
      text:
        if (durationFlagged and b.target_hrs != null)
          base ++ ", against a target of $(fmt1(b.target_hrs)) hours"
               ++ (if (irregular) ", and the length of your nights varied noticeably." else ".")
        else if (irregular and b.consistency_cv != null)
          "Over the past $(days) days the length of your nights varied by about $(fmt0(b.consistency_cv * 100))% around your $(fmt1(b.avg_duration_hrs))-hour average."
        else base ++ ".",
      family: "sleep",
      direction: b.trend_direction
    }
  }

fun recoveryObs(b, days) =
  if (b == null or b.avg_resting_hr == null or b.baseline_resting_hr == null
      or b.avg_hrv_sdnn == null or b.baseline_hrv_sdnn == null) null
  else {
    text: "Over the past $(days) days your resting heart rate averaged $(fmt0(b.avg_resting_hr)) bpm against your 30-day average of $(fmt0(b.baseline_resting_hr)), and your HRV averaged $(fmt0(b.avg_hrv_sdnn)) ms against $(fmt0(b.baseline_hrv_sdnn)).",
    family: "recovery",
    direction: b.trend_direction
  }

fun activityObs(b, days) =
  if (b == null or b.moderate_min_total == null or b.vigorous_min_total == null
      or b.moderate_min_target == null or b.vigorous_min_target == null) null
  else {
    text: "Your recorded workouts added up to about $(fmt0(b.moderate_min_total)) moderate and $(fmt0(b.vigorous_min_total)) vigorous minutes over the past $(days) days, against a guideline of $(fmt0(b.moderate_min_target)) moderate or $(fmt0(b.vigorous_min_target)) vigorous for that period"
          ++ (if (days != 7) " (scaled from the weekly guideline)." else "."),
    family: "activity",
    direction: b.trend_direction
  }

fun macroObs(name, b, days) =
  if (name == null or b == null or b.avg_value == null) null
  else do {
    var label = macroNames[name] default name
    var unit = macroUnits[name] default "g"
    var lead = "Over the past $(days) days you averaged about $(fmtMacro(name, b.avg_value)) $(unit) of $(label) a day on the days you logged"
    var detail =
      if (b.range_min != null and b.range_max != null)
        (if (b.avg_value < b.range_min) ", below the general recommended range of "
         else if (b.avg_value > b.range_max) ", above the general recommended range of "
         else ", within the general recommended range of ")
        ++ "$(fmtMacro(name, b.range_min)) to $(fmtMacro(name, b.range_max)) $(unit)."
      else if (b.target != null and (name == "saturated_fat" or name == "sodium"))
        ", above the limit of $(fmtMacro(name, b.target)) $(unit)."
      else if (b.target != null and name == "protein")
        ", below your target of $(fmtMacro(name, b.target)) $(unit)."
      else if (b.target != null)
        ", below the general recommended target of $(fmtMacro(name, b.target)) $(unit)."
      else "."
    ---
    { text: lead ++ detail, family: "nutrition", direction: b.trend_direction }
  }

fun findMicro(list, nutrient) =
  if (list == null or nutrient == null) null
  else (list filter ((n) -> n.nutrient == nutrient))[0]

fun microObs(b, days) =
  if (b == null or b.avg_intake == null or b.rda == null) null
  else do {
    var info = nutrientInfo[b.nutrient] default { name: b.nutrient, unit: "" }
    ---
    {
      text: "Over the past $(days) days you averaged about $(fmt1(b.avg_intake)) $(info.unit) of $(info.name) a day on the days you logged, below the reference intake of $(fmt1(b.rda)) $(info.unit).",
      family: "nutrition",
      direction: b.trend_direction
    }
  }

// Dispatcher
fun observationFor(rec, trends, days) =
  rec.dimension match {
    case "calories" -> caloriesObs(trends.calories, days)
    case "sleep" -> sleepObs(trends.sleep, days)
    case "recovery" -> recoveryObs(trends.recovery, days)
    case "activity" -> activityObs(trends.activity, days)
    case "macros" -> macroObs(rec.nutrient, trends.macros[rec.nutrient default ""], days)
    case "micronutrients" -> microObs(findMicro(trends.micronutrients, rec.nutrient), days)
    else -> null
  }

fun textFor(rec, trends, days) = do {
  var obs = observationFor(rec, trends, days)
  ---
  if (obs == null) "No additional detail is available for this recommendation."
  else obs.text ++ trendClause(obs.family, obs.direction) ++ contextClause(rec.condition_context)
}

// Fills text on every recommendation. The "text: null" placeholder is removed first
// so the key is not duplicated.
fun addText(recommendations, trends, days) =
  (recommendations default []) map ((rec) ->
    (rec - "text") ++ { text: textFor(rec, trends, days as Number) })