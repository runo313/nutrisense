%dw 2.0

/**
 * NutriSense Reference Thresholds — Module Metadata (Opus 4.6)
 *
 * Version, scope, default bounding rule, explicit exclusions, resolution guards,
 * and framing constraints.
 *
 * The framing constraints bind ALL text generation modes (template, custom model,
 * LLM). They live here rather than in the Process API so no mode can bypass them.
 */

var moduleVersion = "0.3"
var effectiveDate = "2026-09-03"

var scope = {
  population: "adults 19+, non-pregnant, non-lactating",
  excluded_populations: ["under_19", "pregnancy", "lactation"],
  regional_basis: "US",
  note: "Anyone outside scope receives no_data, never a wrong threshold."
}

// ---------------------------------------------------------------------------
// Default bounding rule
//
// Base values are sourced. This split is not — it is the rule that produces the
// four dimension_status states from single-value sources. Every row inherits from
// its value_type unless it declares its own bounds.
// ---------------------------------------------------------------------------

var defaultBounds = {
  RDA:       { direction: "lower", soft: 0.80, hard: 0.60, meaning: "meets needs of 97–98% of healthy individuals"},
  AI:        { direction: "lower", soft: 0.80, hard: 0.60, meaning: "used where evidence is insufficient for an RDA" },
  UL:        { direction: "upper", soft: 0.90, hard: 1.00, meaning:"max daily intake unlikely to cause harm" },
  CDRR:      { direction: "upper", soft: 1.00, hard: 1.30, meaning:"intake below which chronic disease risk is reduced" },
  DGA_limit: { direction: "upper", soft: 1.00, hard: 1.30, meaning:"" },
  AMDR:      { direction: "range", soft: "outside_range", hard: 0.25, meaning:" acceptable distribution range, both bounds meaningful" },
  consensus: { direction: "lower", soft: 0.85, hard: 0.70, meaning:"" },
  personal_baseline_deviation: { direction: "per_row", soft: "per_row", hard: "per_row", meaning:"no population value exists" }
}

var boundingRuleConfidence = "heuristic"

var boundingRuleNote =
  "A lower-bound shortfall and an upper-bound breach are not equivalent. Exceeding a UL or CDRR is treated more tightly than falling under an RDA, because micronutrient references describe habitual intake and a single day below one is unremarkable."

// ---------------------------------------------------------------------------
// Explicit exclusions
//
// Recorded rather than left as silent gaps. Each is a requirement that was
// considered and deliberately not implemented, with the reason.
// ---------------------------------------------------------------------------

var exclusions = [
  {
    requirement: "Carbohydrate tightening under type2_diabetes / prediabetes",
    status: "excluded",
    rationale: "DGA 2025-2030 states that individuals with certain chronic diseases may experience improved outcomes on a lower carbohydrate diet and directs them to work with a health care professional to identify an appropriate diet. The guideline declines to publish a number and defers to a clinician. Any threshold here would be invented. Evaluation falls back to the general AMDR row; the condition still influences recommendation ranking, it just does not move the threshold.",
    source_body: "DGA",
    source_edition: "2025-2030"
  },
  {
    requirement: "strength_days (2 days/week muscle-strengthening)",
    status: "excluded",
    rationale: "Not measurable from wrist wearables. Candidate for a self-reported profile field. Silently ignoring half the CDC guideline would be worse than recording the gap.",
    source_body: "CDC / PAG",
    source_edition: "2nd edition"
  },
  {
    requirement: "Sleep duration upper bound",
    status: "excluded",
    rationale: "AASM states '7 or more hours' with no ceiling. Any oversleeping flag would be invented.",
    source_body: "AASM / SRS",
    source_edition: "2015 consensus"
  },
  {
    requirement: "Total carbohydrate ceiling for general users",
    status: "excluded",
    rationale: "No guideline ceiling exists. The AMDR range is the only sourced evaluation.",
    source_body: "IOM",
    source_edition: "DRI 2005"
  },
  {
    requirement: "FDA 'Healthy' claim added-sugar limits for snack foods",
    status: "excluded",
    rationale: "DGA 2025-2030 states grain snacks should not exceed 5 g added sugar per 3/4 oz whole-grain equivalent and dairy snacks 2.5 g per 2/3 cup equivalent. Not implementable without food-category classification and serving-equivalent math, neither of which the Nutrition System API provides.",
    source_body: "DGA",
    source_edition: "2025-2030"
  },
  {
    requirement: "Hydration",
    status: "excluded",
    rationale: "DGA 2025-2030 advises choosing water and unsweetened beverages but attaches no quantity. Not evaluable.",
    source_body: "DGA",
    source_edition: "2025-2030"
  },
  {
    requirement: "Alcohol",
    status: "excluded",
    rationale: "DGA 2025-2030 advises consuming less alcohol for better overall health without concrete limits. Not evaluable.",
    source_body: "DGA",
    source_edition: "2025-2030"
  },
  {
    requirement: "Sodium increase for highly active individuals",
    status: "excluded",
    rationale: "DGA 2025-2030 notes highly active individuals may benefit from increased sodium intake to offset sweat losses, but attaches no number. Recorded on the sodium row, not acted on.",
    source_body: "DGA",
    source_edition: "2025-2030"
  }
]

// ---------------------------------------------------------------------------
// Attribution guard
//
// The DGA 2025-2030 edition is a short consumer document with no appendix tables.
// It can only source what it actually states. These metrics MUST retain their
// IOM / NASEM / NIH ODS attribution and must not be re-cited to the DGA.
// ---------------------------------------------------------------------------

var dgaSourcedMetrics = [
  "protein_g_per_kg",
  "saturated_fat_pct_kcal",
  "added_sugar_g",
  "sodium_mg"
]

var nonDgaSourcedMetrics = [
  { metric_key: "carbohydrate_pct_kcal", source_body: "IOM" },
  { metric_key: "total_fat_pct_kcal",    source_body: "IOM" },
  { metric_key: "carbohydrate_g",        source_body: "IOM" },
  { metric_key: "fiber_g",               source_body: "IOM" },
  { metric_key: "iron_mg",               source_body: "NIH ODS" },
  { metric_key: "zinc_mg",               source_body: "NIH ODS" },
  { metric_key: "calcium_mg",            source_body: "NIH ODS" },
  { metric_key: "vitamin_b12_mcg",       source_body: "NIH ODS" },
  { metric_key: "potassium_mg",          source_body: "NIH ODS" }
]

// ---------------------------------------------------------------------------
// Resolution guards — enforced in the computation layer, not in the Process API,
// so all three text generation modes inherit them.
// ---------------------------------------------------------------------------

var guards = {

  multiplier_vs_ul: {
    rule: "A dietary_multiplier may not push a resolved lower bound above 80% of the applicable UL. Clamp to that ceiling and set multiplier_clamped on the resolved threshold.",
    worked_example: "Iron, vegetarian, female 19-50: 18 mg x 1.8 = 32.4 mg against a 45 mg UL (72%). Passes. The guard exists because nothing else prevents a multiplier from crossing an upper bound."
  },

  sex_unspecified_resolution: {
    rule: "Where biological_sex is prefer_not_to_say, resolve to the value most likely to flag: the higher base for lower-direction rows, the lower limit for upper-direction rows.",
    resolves_to: {
      iron_mg: 18,
      calcium_mg: 1200,
      potassium_mg: 3400,
      zinc_mg: 11
    },
    consequence: "Some users will be flagged against a threshold that does not apply to them. This is preferred to withholding the entire micronutrient dimension. Text must note the basis is sex-unspecified.",
    alternative_rejected: "Skipping evaluation entirely would remove the anemia gating case, which is the reason micronutrients are evaluated at all."
  },

  caloric_floor: {
    rule: "A computed caloric target may never fall below maintenance minus 500 kcal, regardless of goal_direction.",
    rationale: "Prevents an aggressive weight-loss goal from producing a target that endorses severe restriction."
  },

  protein_floor: {
    rule: "A computed protein target may never fall below 0.8 g/kg, regardless of caloric goal.",
    rationale: "The DRI RDA is the floor. The default 60%-of-base hard bound would place it below that."
  },

  sleep_target_floor: {
    rule: "Effective sleep target is max(user target_sleep_duration_hrs, 7). When the floor is applied, set floor_applied on the resolved threshold.",
    rationale: "A user cannot lower their own target below the AASM consensus figure to avoid flags. Same pattern as the caloric floor."
  },

  under_target_framing: {
    rule: "Being under a computed target is flagged in both directions and is never framed as success.",
    rationale: "A user below their caloric target has not 'done well'; direction of concern depends on goal_direction but neither direction is silent."
  },

  out_of_scope_population: {
    rule: "Users under 19, pregnant, or lactating receive no_data for every nutrition dimension rather than an adult threshold.",
    rationale: "See scope.note."
  }
}

// ---------------------------------------------------------------------------
// Framing constraints — bind all three text generation modes
// ---------------------------------------------------------------------------

var framingConstraints = {

  diagnosis:
    "Thresholds produce flags, not diagnoses. Generated text is limited to informational and behavioural framing across all three generation modes. Never instruct a user to take medication, alter a prescription, or interpret a clinical result.",

  habitual_vs_daily:
    "Every micronutrient and macronutrient reference in this module describes habitual intake adequacy averaged over time, not a single-day requirement. Daily Insight evaluates one day. Text must frame findings as observations about that day ('your iron intake was low today') and must never imply deficiency, adequacy, or clinical status from a single day's data. Sustained-pattern language belongs to Trend Analysis, which evaluates across days.",

  sex_unspecified:
    "Where demographic resolution used the prefer_not_to_say fallback, text notes that the assessment is based on a sex-unspecified threshold.",

  heuristic_disclosure:
    "Where a fired flag rests on a row with confidence 'heuristic' rather than 'guideline', text must not present it with the same certainty as a sourced threshold.",

  evidence_breadth:
    "The protein target is sourced and current, but its evidence base is a review of weight-management outcomes in adults with overweight or obesity. Text should not present it as established for all populations."
}

// ---------------------------------------------------------------------------
// Known dormant capability
// ---------------------------------------------------------------------------

var dormantCapabilities = [
  {
    capability: "Strict dietary constraint violation detection",
    ranking_tier: 1,
    status: "dormant",
    reason: "The allergens and diet_labels columns in the Nutrition System API foods table are null for every row. No violation can be detected.",
    required_behaviour: "Implement the tier so it returns an empty set. Tier 2 (hard flags with condition context) must behave correctly as the effective top tier. Set constraint_evaluation_available: false in data_completeness so the Experience layer knows the check was skipped rather than passed. Do not build anything that assumes tier 1 populates."
  }
]

// ---------------------------------------------------------------------------
// Open question carried forward
// ---------------------------------------------------------------------------

var openQuestions = [
  {
    question: "Do micronutrients belong in Daily Insight at all?",
    context: "The habitual_vs_daily framing constraint is the strongest argument that micronutrient adequacy is a Trend Analysis dimension rather than a Daily Insight one. A single day below an RDA carries little signal. Deferred until the Trend Analysis Process API story.",
    affects: "Which dimensions Daily Insight evaluates. Settle before the evaluation layer is built, not during."
  },
  {
    question: "Does the protein target apply to current or goal body weight in a weight-loss context?",
    context: "At least one secondary source states the 1.2-1.6 g/kg range applies to goal body weight rather than current weight for users pursuing weight loss. This materially changes the computed target. Not acted on without confirmation from the primary document.",
    affects: "resolveProteinTarget in derived-targets.dwl"
  }
]
