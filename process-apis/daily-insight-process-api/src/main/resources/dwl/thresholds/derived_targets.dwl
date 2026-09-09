%dw 2.0

/**
 * NutriSense Reference Thresholds — Category 2: Computed Per User (Opus 4.6)
 *
 * Rows with no fixed value. Each is a named, versioned formula plus a coefficient
 * table, evaluated against the user's profile. Recompute on profile change.
 *
 * Every formula here has its own source. The coefficients are sourced; the
 * soft/hard split is heuristic except where a safety floor overrides it.
 */

// ---------------------------------------------------------------------------
// Energy conversion factors (Atwater)
// ---------------------------------------------------------------------------

var kcalPerGram = {
  protein: 4,
  carbohydrate: 4,
  fat: 9,
  saturated_fat: 9,
  alcohol: 7
}

// ---------------------------------------------------------------------------
// Protein — g/kg body weight, coefficient selected by profile state
// ---------------------------------------------------------------------------

/**
 * Hard floor across every protein row. The default 60%-of-base rule would place
 * the hard bound below the DRI RDA, which is not defensible. Enforced in the
 * resolver, restated here so the coefficient table is self-documenting.
 */
var proteinAbsoluteFloorGPerKg = 0.8

var proteinCoefficients = [
  {
    threshold_id: "protein_athletic",
    match: { goal_type: "athletic_performance" },
    range_min: 1.4,
    range_max: 2.0,
    soft_bound: 1.4,
    hard_bound: proteinAbsoluteFloorGPerKg,
    precedence: 1,
    source_body: "ISSN",
    source_edition: "2017 position stand",
    source_url: "https://jissn.biomedcentral.com/articles/10.1186/s12970-017-0177-8",
    confidence: "guideline",
    notes: "Building and maintaining muscle mass."
  },
  {
    threshold_id: "protein_deficit_active",
    match: { goal_type: "weight_management", goal_direction: "lose", activity_baseline_in: ["very_active", "athlete"] },
    range_min: 1.6,
    range_max: 2.4,
    soft_bound: 1.6,
    hard_bound: proteinAbsoluteFloorGPerKg,
    precedence: 2,
    source_body: "ISSN",
    source_edition: "2017 position stand",
    source_url: "https://jissn.biomedcentral.com/articles/10.1186/s12970-017-0177-8",
    confidence: "guideline",
    notes: "Energy restriction with lean mass preservation. 'Active' is resolved from the self-reported activity_baseline profile field, not from daily wearable data — no daily activity guideline exists to define it, and the protein target should not oscillate day to day."
  },
  {
    threshold_id: "protein_general",
    match: {},
    range_min: 1.2,
    range_max: 1.6,
    soft_bound: 1.2,
    hard_bound: proteinAbsoluteFloorGPerKg,
    precedence: 99,
    source_body: "DGA",
    source_edition: "2025-2030",
    source_url: "https://cdn.realfood.gov/DGA.pdf",
    confidence: "guideline",
    notes: "First DGA edition to give a specific g/kg target. Represents a 50-100% increase over the DRI RDA of 0.8 g/kg. The evidence base is a rapid systematic review of weight-management outcomes in adults with overweight or obesity; commentary in the Journal of Nutrition notes limitations in generalising the range to other groups. Sourced and current, but the evidence base is narrower than the population it is applied to."
  }
]

/**
 * Selects the protein coefficient row for a profile. Lowest precedence wins.
 * range_max is informational only — protein is evaluated against range_min.
 * There is no evidence base for flagging high protein intake in healthy adults.
 */
fun resolveProteinTarget(profile) = do {
  var goalType = profile.goals default [] filter ($.isPrimary == true) map ($.goalType)
  var primaryGoal = goalType[0] default null
  var primaryDirection = (profile.goals default [] filter ($.isPrimary == true) map ($.goalDirection))[0] default null
  var activityBaseline = profile.profile.activityBaseline default null

  var matched = proteinCoefficients filter ((row) ->
    (row.match.goal_type == null or row.match.goal_type == primaryGoal)
    and (row.match.goal_direction == null or row.match.goal_direction == primaryDirection)
    and (row.match.activity_baseline_in == null or (row.match.activity_baseline_in contains activityBaseline))
  ) orderBy ($.precedence)

  var selected = matched[0]
  ---
  {
    metric_key: "protein_g",
    unit: "g",
    direction: "lower",
    evaluation_scope: "daily",
    coefficient_id: selected.threshold_id,
    target_g: (profile.profile.weightKg * selected.range_min) as Number {format: "0.#"},
    range_max_g: (profile.profile.weightKg * selected.range_max) as Number {format: "0.#"},
    soft_bound_g: (profile.profile.weightKg * selected.soft_bound) as Number {format: "0.#"},
    hard_bound_g: (profile.profile.weightKg * selected.hard_bound) as Number {format: "0.#"},
    range_max_is_informational: true,
    source_body: selected.source_body,
    source_edition: selected.source_edition,
    confidence: selected.confidence,
    notes: selected.notes
  }
}

// ---------------------------------------------------------------------------
// AMDR ranges — percentage of total calories, both bounds meaningful
// ---------------------------------------------------------------------------

var amdrRanges = [
  {
    threshold_id: "carbohydrate_amdr",
    metric_key: "carbohydrate_g",
    pct_min: 45,
    pct_max: 65,
    hard_pct_below: 33.75,
    hard_pct_above: 81.25,
    kcal_per_gram: 4,
    primary_evaluation: true,
    source_body: "IOM",
    source_edition: "DRI 2005",
    source_url: "https://nap.nationalacademies.org/catalog/10490/",
    confidence: "guideline",
    notes: "Primary evaluation for carbohydrate. The 130 g RDA floor in static-thresholds.dwl answers a different question and fires separately. No condition override exists for diabetes — see exclusions."
  },
  {
    threshold_id: "total_fat_amdr",
    metric_key: "total_fat_g",
    pct_min: 20,
    pct_max: 35,
    hard_pct_below: 15,
    hard_pct_above: 43.75,
    kcal_per_gram: 9,
    primary_evaluation: true,
    source_body: "IOM",
    source_edition: "DRI 2005",
    source_url: "https://nap.nationalacademies.org/catalog/10490/",
    confidence: "guideline",
    notes: null
  }
]

fun resolveAmdrTarget(row, caloricTarget) = {
  metric_key: row.metric_key,
  unit: "g",
  direction: "range",
  evaluation_scope: "daily",
  coefficient_id: row.threshold_id,
  range_min_g: (caloricTarget * (row.pct_min / 100) / row.kcal_per_gram) as Number {format: "0.#"},
  range_max_g: (caloricTarget * (row.pct_max / 100) / row.kcal_per_gram) as Number {format: "0.#"},
  hard_below_g: (caloricTarget * (row.hard_pct_below / 100) / row.kcal_per_gram) as Number {format: "0.#"},
  hard_above_g: (caloricTarget * (row.hard_pct_above / 100) / row.kcal_per_gram) as Number {format: "0.#"},
  primary_evaluation: row.primary_evaluation,
  source_body: row.source_body,
  source_edition: row.source_edition,
  confidence: row.confidence,
  notes: row.notes
}

// ---------------------------------------------------------------------------
// Saturated fat — percentage cap, tightened under high_cholesterol
// ---------------------------------------------------------------------------

var saturatedFatRows = [
  {
    threshold_id: "saturated_fat_general",
    pct_cap: 10,
    soft_pct: 10,
    hard_pct: 13,
    condition: null,
    source_body: "DGA",
    source_edition: "2025-2030",
    source_url: "https://cdn.realfood.gov/DGA.pdf",
    confidence: "guideline",
    notes: "The 2025-2030 edition retains the longstanding 10% of total daily calories upper limit."
  },
  {
    threshold_id: "saturated_fat_high_cholesterol",
    pct_cap: 7,
    soft_pct: 7,
    hard_pct: 9.1,
    condition: "high_cholesterol",
    condition_override_of: "saturated_fat_general",
    source_body: "AHA",
    source_edition: "current",
    source_url: "https://www.heart.org/en/healthy-living/healthy-eating/eat-smart/fats/saturated-fats",
    confidence: "guideline",
    notes: "Supersedes the general row when high_cholesterol is active."
  }
]

fun resolveSaturatedFatTarget(caloricTarget, activeConditions) = do {
  var override = saturatedFatRows filter ((r) -> r.condition != null and (activeConditions contains r.condition))
  var selected = if (sizeOf(override) > 0) override[0] else saturatedFatRows[0]
  ---
  {
    metric_key: "saturated_fat_g",
    unit: "g",
    direction: "upper",
    evaluation_scope: "daily",
    coefficient_id: selected.threshold_id,
    cap_g: (caloricTarget * (selected.pct_cap / 100) / 9) as Number {format: "0.#"},
    soft_bound_g: (caloricTarget * (selected.soft_pct / 100) / 9) as Number {format: "0.#"},
    hard_bound_g: (caloricTarget * (selected.hard_pct / 100) / 9) as Number {format: "0.#"},
    condition_context: selected.condition,
    source_body: selected.source_body,
    source_edition: selected.source_edition,
    confidence: selected.confidence,
    notes: selected.notes
  }
}

// ---------------------------------------------------------------------------
// Fiber — rate per 1,000 kcal
// ---------------------------------------------------------------------------

var fiberRow = {
  threshold_id: "fiber_rate",
  metric_key: "fiber_g",
  grams_per_1000_kcal: 14,
  soft_pct_of_base: 0.80,
  hard_pct_of_base: 0.60,
  source_body: "IOM",
  source_edition: "DRI 2005",
  source_url: "https://nap.nationalacademies.org/catalog/10490/",
  confidence: "guideline",
  notes: "Stored as one coefficient, not as per-demographic gram values. The age and sex variation in published fiber tables is this rate applied to each bucket's estimated energy requirement."
}

fun resolveFiberTarget(caloricTarget) = do {
  var target = caloricTarget / 1000 * fiberRow.grams_per_1000_kcal
  ---
  {
    metric_key: "fiber_g",
    unit: "g",
    direction: "lower",
    evaluation_scope: "daily",
    coefficient_id: fiberRow.threshold_id,
    target_g: target as Number {format: "0.#"},
    soft_bound_g: (target * fiberRow.soft_pct_of_base) as Number {format: "0.#"},
    hard_bound_g: (target * fiberRow.hard_pct_of_base) as Number {format: "0.#"},
    source_body: fiberRow.source_body,
    source_edition: fiberRow.source_edition,
    confidence: fiberRow.confidence,
    notes: fiberRow.notes
  }
}

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
  notes: "Direction of concern depends on goal_direction, but both directions are flagged. Being under a computed target is never framed as success — see under_target_framing guard."
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
