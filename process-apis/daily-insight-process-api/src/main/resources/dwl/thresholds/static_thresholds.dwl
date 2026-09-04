%dw 2.0
import some from dwl::core::Arrays
/**
 * NutriSense Reference Thresholds (Opus 4.6)
 *  — Category 1: Static Lookup
 *
 * Rows whose base value is a fixed number, optionally keyed by demographic bucket
 * and optionally superseded by a condition override.
 *
 * Every base value here is sourced. Every soft/hard split is a heuristic derived
 * from the default bounding rule in threshold-metadata.dwl.
 */

// ---------------------------------------------------------------------------
// Demographic bucket resolution
// ---------------------------------------------------------------------------

/**
 * Resolves a demographic bucket key from profile fields.
 * anyone under 19, pregnant, or lactating is out of scope and must receive no_data.
 *
 * `prefer_not_to_say` returns "unspecified"; resolution for that bucket is handled
 * by the flagging-bias guard, not by a distinct row set.
 */
fun resolveDemographic(biologicalSex, age) =
  if (age < 19) "out_of_scope"
  else if (biologicalSex == "male") "male_19_plus"
  else if (biologicalSex == "female" and age < 51) "female_19_50"
  else if (biologicalSex == "female") "female_51_plus"
  else "unspecified"

// ---------------------------------------------------------------------------
// Micronutrients — NIH ODS
// ---------------------------------------------------------------------------

var micronutrients = [
  {
    threshold_id: "iron_rda_male_19_plus",
    dimension: "nutrition",
    metric_key: "iron_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 8,
    soft_bound: 6.4,
    hard_bound: 4.8,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "male_19_plus",
    surfacing_rule: { conditions: ["anemia"], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: { vegetarian: 1.8, vegan: 1.8 },
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Iron-HealthProfessional/",
    confidence: "guideline",
    notes: "Habitual intake reference. A single day below base is an intake gap, not a deficiency."
  },
  {
    threshold_id: "iron_rda_female_19_50",
    dimension: "nutrition",
    metric_key: "iron_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 18,
    soft_bound: 14.4,
    hard_bound: 10.8,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "female_19_50",
    surfacing_rule: { conditions: ["anemia"], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: { vegetarian: 1.8, vegan: 1.8 },
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Iron-HealthProfessional/",
    confidence: "guideline",
    notes: "Vegetarian multiplier resolves to 32.4 mg against a 45 mg UL (72%). Passes the UL guard."
  },
  {
    threshold_id: "iron_rda_female_51_plus",
    dimension: "nutrition",
    metric_key: "iron_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 8,
    soft_bound: 6.4,
    hard_bound: 4.8,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "female_51_plus",
    surfacing_rule: { conditions: ["anemia"], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: { vegetarian: 1.8, vegan: 1.8 },
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Iron-HealthProfessional/",
    confidence: "guideline",
    notes: null
  },
  {
    threshold_id: "iron_ul_adult",
    dimension: "nutrition",
    metric_key: "iron_mg",
    value_type: "UL",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: 45,
    soft_bound: 40.5,
    hard_bound: 45,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: ["anemia"], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: null,
    primary_evaluation: false,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Iron-HealthProfessional/",
    confidence: "guideline",
    notes: "Applies to intake from all sources. Rarely reached from food alone."
  },
  {
    threshold_id: "zinc_rda_male_19_plus",
    dimension: "nutrition",
    metric_key: "zinc_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 11,
    soft_bound: 8.8,
    hard_bound: 6.6,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "male_19_plus",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Zinc-HealthProfessional/",
    confidence: "guideline",
    notes: null
  },
  {
    threshold_id: "zinc_rda_female_19_plus",
    dimension: "nutrition",
    metric_key: "zinc_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 8,
    soft_bound: 6.4,
    hard_bound: 4.8,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "female_19_plus",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Zinc-HealthProfessional/",
    confidence: "guideline",
    notes: null
  },
  {
    threshold_id: "zinc_ul_adult",
    dimension: "nutrition",
    metric_key: "zinc_mg",
    value_type: "UL",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: 40,
    soft_bound: 36,
    hard_bound: 40,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: null,
    primary_evaluation: false,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Zinc-HealthProfessional/",
    confidence: "guideline",
    notes: null
  },
  {
    threshold_id: "calcium_rda_standard",
    dimension: "nutrition",
    metric_key: "calcium_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 1000,
    soft_bound: 800,
    hard_bound: 600,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "calcium_standard",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan", "dairy"] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Calcium-HealthProfessional/",
    confidence: "guideline",
    notes: "Applies to males 19-70 and females 19-50. Bucket is lossy: male 71+ is 1,200 mg."
  },
  {
    threshold_id: "calcium_rda_elevated",
    dimension: "nutrition",
    metric_key: "calcium_mg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 1200,
    soft_bound: 960,
    hard_bound: 720,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "female_51_plus",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan", "dairy"] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Calcium-HealthProfessional/",
    confidence: "guideline",
    notes: "Male 71+ also takes this value but is not represented by the current bucket set."
  },
  {
    threshold_id: "calcium_ul_19_50",
    dimension: "nutrition",
    metric_key: "calcium_mg",
    value_type: "UL",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: 2500,
    soft_bound: 2250,
    hard_bound: 2500,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "calcium_standard",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan", "dairy"] },
    dietary_multiplier: null,
    primary_evaluation: false,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Calcium-HealthProfessional/",
    confidence: "guideline",
    notes: null
  },
  {
    threshold_id: "calcium_ul_51_plus",
    dimension: "nutrition",
    metric_key: "calcium_mg",
    value_type: "UL",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: 2000,
    soft_bound: 1800,
    hard_bound: 2000,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "female_51_plus",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan", "dairy"] },
    dietary_multiplier: null,
    primary_evaluation: false,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Calcium-HealthProfessional/",
    confidence: "guideline",
    notes: null
  },
  {
    threshold_id: "vitamin_b12_rda_adult",
    dimension: "nutrition",
    metric_key: "vitamin_b12_mcg",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 2.4,
    soft_bound: 1.92,
    hard_bound: 1.44,
    bound_mode: "absolute",
    unit: "mcg",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: ["vegetarian", "vegan"] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/VitaminB12-HealthProfessional/",
    confidence: "guideline",
    notes: "No UL established. Excess is excreted rather than accumulated. Direction is lower only."
  },
  {
    threshold_id: "potassium_ai_male_19_plus",
    dimension: "nutrition",
    metric_key: "potassium_mg",
    value_type: "AI",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 3400,
    soft_bound: 2720,
    hard_bound: 2040,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "male_19_plus",
    surfacing_rule: { conditions: ["hypertension", "kidney_disease"], constraints: [] },
    dietary_multiplier: null,
    flips_direction_under: ["kidney_disease"],
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Potassium-HealthProfessional/",
    confidence: "guideline",
    notes: "AI, not RDA — evidence was insufficient for an RDA. ODS states the AI does not apply to individuals with impaired potassium excretion; direction inverts under kidney_disease."
  },
  {
    threshold_id: "potassium_ai_female_19_plus",
    dimension: "nutrition",
    metric_key: "potassium_mg",
    value_type: "AI",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 2600,
    soft_bound: 2080,
    hard_bound: 1560,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "female_19_plus",
    surfacing_rule: { conditions: ["hypertension", "kidney_disease"], constraints: [] },
    dietary_multiplier: null,
    flips_direction_under: ["kidney_disease"],
    primary_evaluation: true,
    source_body: "NIH ODS",
    source_edition: "2024",
    source_url: "https://ods.od.nih.gov/factsheets/Potassium-HealthProfessional/",
    confidence: "guideline",
    notes: "See male row note on direction inversion."
  }
]

// ---------------------------------------------------------------------------
// Sodium — DGA 2025-2030 general, AHA override
// ---------------------------------------------------------------------------

var sodium = [
  {
    threshold_id: "sodium_cdrr_general",
    dimension: "nutrition",
    metric_key: "sodium_mg",
    value_type: "CDRR",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: 2300,
    soft_bound: 2300,
    hard_bound: 2990,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: ["hypertension"], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "DGA",
    source_edition: "2025-2030",
    source_url: "https://cdn.realfood.gov/DGA.pdf",
    confidence: "guideline",
    notes: "DGA 2025-2030 states the general population aged 14+ should consume less than 2,300 mg/day. Sodium has no UL; the 2019 NASEM report replaced it with the CDRR, so text frames this as chronic disease risk reduction, not a safety ceiling. The document notes highly active individuals may benefit from increased sodium to offset sweat losses but attaches no number — recorded, not acted on."
  },
  {
    threshold_id: "sodium_hypertension_override",
    dimension: "nutrition",
    metric_key: "sodium_mg",
    value_type: "CDRR",
    evaluation_scope: "daily",
    direction: "upper",
    base_value: 1500,
    soft_bound: 1500,
    hard_bound: 1950,
    bound_mode: "absolute",
    unit: "mg",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: ["hypertension"], constraints: [] },
    condition_override_of: "sodium_cdrr_general",
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "AHA",
    source_edition: "current",
    source_url: "https://www.heart.org/en/healthy-living/healthy-eating/eat-smart/sodium/how-much-sodium-should-i-eat-per-day",
    confidence: "guideline",
    notes: "Supersedes the general row when hypertension is active."
  }
]

// ---------------------------------------------------------------------------
// Macros with fixed values (percentage- and weight-derived rows live in
// derived-targets.dwl)
// ---------------------------------------------------------------------------

var staticMacros = [
  {
    threshold_id: "carbohydrate_rda_floor",
    dimension: "nutrition",
    metric_key: "carbohydrate_g",
    value_type: "RDA",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 130,
    soft_bound: 104,
    hard_bound: 78,
    bound_mode: "absolute",
    unit: "g",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: false,
    source_body: "IOM",
    source_edition: "DRI 2005",
    source_url: "https://nap.nationalacademies.org/catalog/10490/",
    confidence: "guideline",
    notes: "Brain glucose requirement floor, not an intake target. Answers a different question from the AMDR range and fires separately and rarely. Not the primary evaluation for carbohydrate."
  },
  {
    threshold_id: "added_sugar_per_meal",
    dimension: "nutrition",
    metric_key: "added_sugar_g",
    value_type: "DGA_limit",
    evaluation_scope: "per_meal",
    direction: "upper",
    base_value: 10,
    soft_bound: 10,
    hard_bound: 13,
    bound_mode: "absolute",
    unit: "g",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "DGA",
    source_edition: "2025-2030",
    source_url: "https://cdn.realfood.gov/DGA.pdf",
    confidence: "guideline",
    notes: "The only per_meal row in the set. DGA 2025-2030 replaced the 2020-2025 daily <10% kcal figure with a per-meal quantitative limit; do not substitute the superseded figure. Nullable — added sugar is not reliably separable from total sugar outside labeled products. Treat unknown as null, never zero."
  }
]

// ---------------------------------------------------------------------------
// Sleep
// ---------------------------------------------------------------------------

var sleep = [
  {
    threshold_id: "sleep_duration_consensus",
    dimension: "sleep",
    metric_key: "sleep_duration_hrs",
    value_type: "consensus",
    evaluation_scope: "daily",
    direction: "lower",
    base_value: 7,
    soft_bound: 0.85,
    hard_bound: 0.70,
    bound_mode: "pct_of_base",
    unit: "hrs",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    user_target_field: "target_sleep_duration_hrs",
    user_target_rule: "max",
    source_body: "AASM / Sleep Research Society",
    source_edition: "2015 consensus",
    source_url: "https://aasm.org/resources/pdf/pressroom/adult-sleep-duration-consensus.pdf",
    confidence: "guideline",
    notes: "Effective target is max(user target, 7). A user cannot lower their target below the consensus floor to avoid flags. When the floor is applied, set floor_applied on the resolved threshold so the Experience layer can explain the mismatch. No upper bound — AASM states '7 or more hours' with no ceiling."
  },
  {
    threshold_id: "sleep_deep_pct_range",
    dimension: "sleep",
    metric_key: "sleep_deep_pct",
    value_type: "range",
    evaluation_scope: "daily",
    direction: "range",
    base_value: null,
    range_min: 10,
    range_max: 20,
    soft_bound: "outside_range",
    hard_bound: { below: 7.5, above: 25 },
    bound_mode: "absolute",
    unit: "pct",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "Sleep Foundation",
    source_edition: "current",
    source_url: "https://www.sleepfoundation.org/stages-of-sleep",
    confidence: "heuristic",
    notes: "Not a clinical guideline. Consumer-facing reference range."
  },
  {
    threshold_id: "sleep_rem_pct_range",
    dimension: "sleep",
    metric_key: "sleep_rem_pct",
    value_type: "range",
    evaluation_scope: "daily",
    direction: "range",
    base_value: null,
    range_min: 20,
    range_max: 25,
    soft_bound: "outside_range",
    hard_bound: { below: 15, above: 31.25 },
    bound_mode: "absolute",
    unit: "pct",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "Sleep Foundation",
    source_edition: "current",
    source_url: "https://www.sleepfoundation.org/stages-of-sleep",
    confidence: "heuristic",
    notes: "Not a clinical guideline. Consumer-facing reference range."
  }
]

// ---------------------------------------------------------------------------
// Activity — weekly scope. Consumed by Trend Analysis, NOT Daily Insight.
// ---------------------------------------------------------------------------

var activity = [
  {
    threshold_id: "moderate_activity_weekly",
    dimension: "activity",
    metric_key: "moderate_activity_min",
    value_type: "guideline",
    evaluation_scope: "weekly",
    direction: "lower",
    base_value: 150,
    soft_bound: 120,
    hard_bound: 90,
    bound_mode: "absolute",
    unit: "min",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "CDC / Physical Activity Guidelines for Americans",
    source_edition: "2nd edition",
    source_url: "https://www.cdc.gov/physical-activity-basics/guidelines/adults.html",
    confidence: "guideline",
    notes: "300 min/week is the greater-benefit band. No daily adult guideline exists — the '30 minutes, 5 days' framing is a way to distribute the weekly total, not a target. Daily Insight must not evaluate against this row."
  },
  {
    threshold_id: "vigorous_activity_weekly",
    dimension: "activity",
    metric_key: "vigorous_activity_min",
    value_type: "guideline",
    evaluation_scope: "weekly",
    direction: "lower",
    base_value: 75,
    soft_bound: 60,
    hard_bound: 45,
    bound_mode: "absolute",
    unit: "min",
    demographic_key: "all_19_plus",
    surfacing_rule: { conditions: [], constraints: [] },
    dietary_multiplier: null,
    primary_evaluation: true,
    source_body: "CDC / Physical Activity Guidelines for Americans",
    source_edition: "2nd edition",
    source_url: "https://www.cdc.gov/physical-activity-basics/guidelines/adults.html",
    confidence: "guideline",
    notes: "Interchangeable with moderate at roughly 2:1."
  }
]

// ---------------------------------------------------------------------------
// Combined set + lookup
// ---------------------------------------------------------------------------

var allStaticRows = micronutrients ++ sodium ++ staticMacros ++ sleep ++ activity

/**
 * Given one threshold row's demographic_key and the user's resolved bucket (from the function resolveDemographic(biologicalSex, age)), decide: does this row apply to this user?
 * Demographic keys that a given resolved bucket satisfies.
 * A row keyed "all_19_plus" matches every in-scope user.
 */
fun demographicMatches(rowKey, resolvedBucket) =
  rowKey == "all_19_plus"
    or rowKey == resolvedBucket
    or (rowKey == "female_19_plus" and (resolvedBucket == "female_19_50" or resolvedBucket == "female_51_plus"))
    or (rowKey == "calcium_standard" and (resolvedBucket == "male_19_plus" or resolvedBucket == "female_19_50"))

/**
 * given one threshold row and the user's actual active conditions/constraints, decide whether this row should be evaluated at all
 * Answers: does this apply to your health situation."
 */
fun isSurfaced(row, activeConditions, activeConstraints) = do {
  var ruleConditions = row.surfacing_rule.conditions default []
  var ruleConstraints = row.surfacing_rule.constraints default []
  ---
  if (isEmpty(ruleConditions) and isEmpty(ruleConstraints)) true
  else (activeConditions some ((c) -> ruleConditions contains c))
       or (activeConstraints some ((c) -> ruleConstraints contains c))
}

fun rowsForMetric(metricKey) =
  allStaticRows filter ($.metric_key == metricKey)

fun rowsForDimension(dimensionName, evaluationScope) =
  allStaticRows filter ($.dimension == dimensionName and $.evaluation_scope == evaluationScope)
