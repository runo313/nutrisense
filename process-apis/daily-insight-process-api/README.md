# Daily Insight Process API

Builds one user's health picture for one day. It pulls the profile, meal log, nutrient data and wearable data from four System APIs, evaluates that day against the user's own context, ranks what matters, and returns a single response the Experience layer can render directly.

Nothing is saved, so calling it again for the same date reruns the whole process against the current System API data.

## Endpoints

Base path: `https://localhost:8091/api/v1/daily-insight-papi`

| Method | Path | Purpose |
|---|---|---|
| GET | `/insights/daily/{userId}?date=` | Daily insight. `date` defaults to today. |
| POST | `/insights/daily/{userId}/regenerate?date=` | Same response shape. Forces a fresh run. |
| GET | `/insights/daily/{userId}/preview?date=&mode=` | Generates the text in the given mode (`template` or `llm`) without changing the user's saved preference. |

Responses: `200`, `400` (invalid parameters), `503` (profile unavailable), `500` (anything unexpected).

## Orchestration

1. **Profile.** One call to the User Profile System API (`GET /profiles/{userId}`) returns identity, body measurements, goals, conditions, constraints and preferences. This step is required: if it fails, the whole request stops with `503`, because nothing can be personalized without it.
2. **Meals and nutrition**, in parallel with step 3. Fetch the day's meals from the Meal Log System API, collect the unique food IDs, and fetch each food from the Nutrition System API in parallel.
3. **Wearable**, in parallel with step 2. Two calls: the day's raw data, and a 30-day summary used as the user's personal baseline for recovery and active energy.
4. **Evaluate** (below): total up nutrients and sleep, evaluate each area, rank, assemble.
5. **Generate text** in the user's `insightGenerationMode`, falling back to the template engine if that fails.

| Condition | Result |
|---|---|
| Profile call fails | `503`, request stopped |
| No meals logged, Meal Log fails, or foods cannot be found | Calories, macros and micronutrients are `no_data`; everything else continues |
| No wearable data for the day | Sleep, activity and recovery are `no_data` |
| Baseline summary missing or too short | Recovery is `no_data`, even if the day's raw data exists |
| A System API returns an unexpected status (for example `401`) | `500` |

The Wearable and Meal Log calls treat both `200` and `404` as success, because a day with no data returns `404`.

## How evaluation works

Every metric is compared against a reference value and given one of four results:

| Result | Meaning |
|---|---|
| `on_track` | Within the healthy range |
| `soft_warning` | Somewhat off, worth noticing |
| `hard_flag` | Clearly off |
| `no_data` | Not enough data to judge |

Some metrics are shown without any result at all (`status: null`). That's deliberate, and each case is explained below.

Two different kinds of numbers go into every result, and it helps to keep them apart:

1. **The reference value** is the target or limit itself, such as 2,300 mg of sodium per day. These come from published health guidelines wherever one exists.
2. **The warning bands** decide how far from the reference counts as a soft warning or a hard flag. No guideline publishes these, so most of them are our own choice. Each section says which numbers are published and which are ours.

### What the reference types mean

Health guidelines use several kinds of reference values. They mean different things, so they're handled differently.

| Term | Full name | What it means | How we use it |
|---|---|---|---|
| RDA | Recommended Dietary Allowance | The daily amount that covers the needs of nearly all healthy people | A minimum to reach |
| AI | Adequate Intake | Used instead of an RDA when there isn't enough research to set one. Same role, less certainty | A minimum to reach |
| UL | Tolerable Upper Intake Level | The most you can take daily without likely harm | A ceiling. Currently used only to cap the iron adjustment for vegetarians and vegans |
| CDRR | Chronic Disease Risk Reduction intake | Used for sodium. Above this level, cutting back lowers long-term disease risk. It's a risk marker, not a safety limit | A cap |
| AMDR | Acceptable Macronutrient Distribution Range | The share of daily calories from carbs, fat or protein that meets your needs while keeping disease risk low | A range to stay inside |
| DGA limit | Dietary Guidelines for Americans | A cap set by the federal dietary guidelines | A cap |
| Personal baseline | The user's own 30-day average | Used where a population value makes no sense, such as heart rate variability | Compared against the user themselves |

### How far off counts as a warning

Metrics come in three shapes:

- **Minimums**, where less is worse (fiber, protein, sleep duration).
- **Caps**, where more is worse (sodium, saturated fat).
- **Ranges**, where both too little and too much are worse (carbs, total fat).

| Shape | On track | Soft warning | Hard flag |
|---|---|---|---|
| Minimum | At or above the target | Below the target, down to the hard cutoff | Below the hard cutoff |
| Cap | At or under the cap | Above the cap, up to the hard cutoff | Above the hard cutoff |
| Range | Inside the range | Outside the range, within the hard cutoffs | Beyond the hard cutoffs |

Where the hard cutoff sits, by reference type:

| Reference type | Shape | Hard cutoff |
|---|---|---|
| RDA / AI | Minimum | 60% of the reference |
| CDRR / DGA limit | Cap | 130% of the cap |
| AMDR | Range | 25% beyond either end of the range |
| Sleep duration | Minimum | 70% of the target |
| Personal baseline | Either | Set per metric (see Recovery and Activity) |

All of these cutoffs are our own choice. The guidelines tell you the target, not how far from it should count as a warning.

### Calories

Logged calories are divided by the user's daily calorie target. The target comes from the User Profile System API, which calculates it from height, weight, age, sex and activity level using the Mifflin-St Jeor equation.

| Share of target | Result |
|---|---|
| 80 to 120% | `on_track` |
| 60 to 80%, or 120 to 140% | `soft_warning` |
| Below 60%, or above 140% | `hard_flag` |

The same bands apply whether someone is over or under. Their goal (losing, gaining or maintaining weight) only changes how the insight text talks about it, not the result.

If the user chose "prefer not to say" for biological sex, no calorie target can be calculated, so calories return `no_data`.

These bands are our own choice. No guideline publishes how far off a calorie target is acceptable.

### Macros

Six metrics are checked, each with its own result in `nutrition_detail.macros`. The overall macros result in `dimension_status.macros` is the most serious of the six.

Several targets are worked out from the user's calorie target. The examples below assume a 2,000 kcal target.

**Protein** (minimum)

Target = body weight in kg × an amount per kg that depends on the user's goal:

| Goal | Amount per kg | Source |
|---|---|---|
| `athletic_performance` | 1.4 g | International Society of Sports Nutrition, 2017 |
| `weight_management` + `lose`, with a `very_active` or `athlete` activity level | 1.6 g | International Society of Sports Nutrition, 2017 |
| Everyone else | 1.2 g | Dietary Guidelines for Americans 2025-2030 |

A 70 kg user with no special goal has a target of 70 × 1.2 = 84 g. The hard cutoff is always 0.8 g per kg, the standard RDA. Eating more protein than the target is never flagged.

**Carbohydrates** (range)

45 to 65% of calories, from the AMDR (Institute of Medicine). At 2,000 kcal that's 225 to 325 g, since each gram of carbs is 4 kcal. Hard cutoffs sit at 33.75% and 81.25% of calories.

There's a second check: the RDA of 130 g, roughly the minimum the brain needs. Below 130 g is a soft warning, below 78 g is a hard flag. This check can only make the result more serious, never less.

There's deliberately no stricter carb limit for diabetes. The American Diabetes Association says carb targets should be set individually with a clinician and publishes no single number, so carbs are always checked against the general range.

**Total fat** (range)

20 to 35% of calories, from the AMDR (Institute of Medicine). At 2,000 kcal that's about 44 to 78 g, since each gram of fat is 9 kcal. Hard cutoffs at 15% and 43.75%.

**Saturated fat** (cap)

At most 10% of calories, from the Dietary Guidelines for Americans 2025-2030. At 2,000 kcal that's about 22 g. Hard cutoff at 13%. For users with `high_cholesterol`, the cap tightens to 7% (hard cutoff 9.1%), following the American Heart Association.

**Fiber** (minimum)

14 g per 1,000 kcal of the calorie target, from the Institute of Medicine. At 2,000 kcal that's 28 g. Hard cutoff at 60% of the target.

**Sodium** (cap)

At most 2,300 mg per day, the CDRR from the Dietary Guidelines for Americans 2025-2030. Hard cutoff at 2,990 mg. For users with `hypertension`, the cap tightens to 1,500 mg (hard cutoff 1,950 mg), following the American Heart Association.

Where a health condition changed a limit, `condition_context` names it, so the response shows why a stricter number was used.

### Micronutrients

Micronutrients are shown with their reference value next to them, but **the result is always `null`**.

The reason: these reference values describe what a person needs on average over weeks, not on any single day. Eating less iron than the RDA today is normal and says nothing about whether someone is getting enough overall. Judging one day against them would claim more than the data supports. Judging across multiple days belongs to the Trend Analysis Process API.

A nutrient only appears when the user's profile makes it relevant:

| Nutrient | Shown for | Reference, adults 19+ |
|---|---|---|
| Iron | `anemia`, `vegetarian`, `vegan` | 8 mg for men, 18 mg for women 19 to 50, 8 mg for women 51+. Multiplied by 1.8 for vegetarians and vegans, since iron from plants is absorbed less well. Never raised past the 45 mg UL |
| Zinc | `vegetarian`, `vegan` | 11 mg for men, 8 mg for women |
| Calcium | `vegetarian`, `vegan`, `dairy` | 1,000 mg, or 1,200 mg for women 51+ |
| Vitamin B12 | `vegetarian`, `vegan` | 2.4 mcg |
| Potassium | `hypertension`, `kidney_disease` | 3,400 mg for men, 2,600 mg for women. This is an AI, not an RDA |

All values are from the NIH Office of Dietary Supplements.

### Sleep

The day's sleep sessions are grouped by stage. Total sleep counts Core, Deep, REM and unlabeled sleep. Time awake is left out, and so is any session missing a start or end time.

Three checks run. Each returns its own result, and the overall sleep result is the most serious of the three.

| Check | Rule | Source |
|---|---|---|
| Duration | The target is the user's own sleep goal or 7 hours, whichever is higher. On track at or above it. Soft warning down to 70% of it (4.9 hours for a 7-hour target). Hard flag below that | American Academy of Sleep Medicine and Sleep Research Society, which recommend 7 or more hours for adults |
| Deep sleep share | On track at 10 to 20% of total sleep. Soft warning at 7.5 to 25%. Hard flag outside that | Sleep Foundation. Not a formal guideline, so treat it as a rough reference |
| REM share | On track at 20 to 25% of total sleep. Soft warning at 15 to 31.25%. Hard flag outside that | Sleep Foundation. Same caveat |

Users can't set a goal below 7 hours to avoid warnings. When their goal was lower and the 7-hour minimum was used instead, `floor_applied` is `true`.

The stage checks are less reliable than the duration check. Consumer wearables estimate sleep stages imperfectly, and the reference ranges come from a health information site rather than a formal guideline.

### Recovery

Recovery compares the day's resting heart rate and heart rate variability (HRV, measured as SDNN) against the user's own 30-day average from the Wearable summary.

No population range is used, because healthy values differ so much between people. Someone who normally sits at 52 bpm and wakes up at 68 bpm is clearly elevated for them, even though 68 is well inside the usual "normal" range of 60 to 100.

Recovery needs **at least 14 days of history for both metrics** (the summary `count`). If either has fewer, recovery returns `no_data`.

| Metric | On track | Soft warning | Hard flag |
|---|---|---|---|
| Resting heart rate | No more than 5% above average | 5 to 10% above | More than 10% above |
| HRV | No more than 15% below average | 15 to 25% below | More than 25% below |

Only the worrying direction is flagged: a low resting heart rate or a high HRV is never a warning. The overall result is the more serious of the two, and both `hr_status` and `hrv_status` are returned. Combined insights use `hr_status`.

These percentages are our own choice, since no guideline publishes them. They're also the same for everyone, so they don't account for how much a particular person's numbers naturally vary from day to day.

### Activity

**Activity gets no result.** Adult activity guidelines from the CDC (150 minutes of moderate or 75 minutes of vigorous activity) are weekly targets, so a single day can't be judged against them. That check belongs to the Trend Analysis Process API.

Daily Insight reports the day's total `steps` and `active_energy_kcal` and nothing more.

Behind the scenes, active energy is compared against the user's own 30-day average (again needing 14 days of history). This is used only to spot unusually active days for combined insights, for example a hard training day paired with low calorie intake:

- 50% above average counts as high, 75% above as very high.
- 40% and 60% below average are also calculated, for context only.

The "high" cutoffs are set loosely on purpose, since one hard training day is completely normal. The nutrition side of each combined insight does most of the filtering. These cutoffs are our own choice.

### Ranking

Four areas can produce a recommendation: calories, macros, sleep and recovery. Each contributes at most one candidate, based on its overall result. For macros that's the most serious of the six metrics, and the metric responsible is passed along so the text can name it. Only `soft_warning` and `hard_flag` qualify.

```
score = severity x 10 + (1 if condition_context is present)
        severity: hard_flag = 2, soft_warning = 1
```

Candidates are sorted by score, highest first. Ties keep the order they were gathered in: calories, macros, sleep, recovery. The top three become `recommendations`. If nothing is flagged, the array is empty.

This produces the intended order of importance:

1. Strict dietary constraint violations (not active yet, see Known limitations)
2. Hard flags where a health condition applies
3. Other hard flags
4. Soft warnings where a health condition applies
5. Other soft warnings

Combined insights are returned in their own array and are not part of the ranked list.

### Combined insights (cross-signals)

Six named combinations, checked after the individual areas. **Every condition in a rule must be true at the same time.** The point is the combination itself, since a single flagged area is already covered by the ranking.

| Key | Fires when |
|---|---|
| `recovery_stress` | Sleep is flagged and resting heart rate is flagged |
| `energy_deficit` | Calories are flagged and below target, and active energy is high |
| `recovery_risk` | Protein is flagged, active energy is high, and the main goal is `athletic_performance` |
| `blood_sugar_disruption_risk` | Carbohydrates are flagged high, sleep is flagged, and the user has `type2_diabetes` or `prediabetes` |
| `cardiovascular_stress` | Sodium is flagged, resting heart rate is flagged, and the user has `hypertension` |
| `lipid_profile_risk` | Fiber is flagged low, saturated fat is flagged, and the user has `high_cholesterol` |

Each rule that fires produces its own entry with its own text. If none fire, `cross_signal` is an empty array.

## Text generation

The mode comes from `insightGenerationMode` in the user's preferences (`template`, `llm`, `custom_model`), or from `mode` on the preview endpoint.

- **`template`:** fixed, rule-based text. One template per area, metric and severity, plus a shared clause for health conditions that varies by condition.
- **`llm`:** implemented. If it fails for any reason, the template engine is used instead.
- **`custom_model`:** uses the template engine until a model exists.
- **Invalid or missing value:** template.

`generation_mode` in the response is the mode that actually produced the text, so the client can see when a fallback happened.

Every mode follows the same rules for what the text is allowed to say:

- **Only claim what the reference supports.** Values backed by a published guideline can mention that guidance (for example, protein below the general recommended minimum). Values based on our own cutoffs, such as sleep stage shares and recovery changes, state the number and the user's baseline and make no claims about what it means for their body.
- **Describe the day, not the person.** No language about deficiency, adequacy or diagnosis.
- **Never present eating under the calorie target as a success.**

## Example response

```json
{
    "user_id": "runo313",
    "date": "2026-10-24",
    "generated_at": "2026-11-15T11:00:35.01444-05:00",
    "generation_mode": "llm",
    "data_completeness": {
        "meals_logged": true,
        "nutrition_resolved": true,
        "wearable_daily_data_present": true,
        "wearable_baseline_available": true,
        "profile_complete": true
    },
    "dimension_status": {
        "sleep": {
            "status": "soft_warning",
            "has_data": true
        },
        "activity": {
            "status": null,
            "has_data": true
        },
        "recovery": {
            "status": "no_data",
            "has_data": false
        },
        "calories": {
            "status": "hard_flag",
            "has_data": true
        },
        "macros": {
            "status": "hard_flag",
            "has_data": true
        },
        "micros": {
            "status": null,
            "has_data": true
        }
    },
    "nutrition_detail": {
        "total_calories": 765.6590995592429,
        "caloric_target": 2723,
        "macros": {
            "protein": {
                "value": 70.39156792844179,
                "target": 116,
                "status": "soft_warning"
            },
            "carbohydrates": {
                "value": 96.19827067669173,
                "range_min": 306.3375,
                "range_max": 442.4875,
                "status": "hard_flag"
            },
            "fat": {
                "value": 11.983592170080373,
                "range_min": 60.51111111111111,
                "range_max": 105.89444444444445,
                "status": "hard_flag"
            },
            "saturated_fat": {
                "value": 2.0707197303603837,
                "target": 30.255555555555556,
                "status": "on_track"
            },
            "fiber": {
                "value": 5.4,
                "target": 38.122,
                "status": "hard_flag"
            },
            "sodium": {
                "value": 1482.4029556650246,
                "target": 2300,
                "status": "on_track"
            }
        },
        "micronutrients": [
            {
                "nutrient": "iron_mg",
                "value": 2.430007778065854,
                "threshold": 8,
                "condition_context": "anemia",
                "status": null
            },
            {
                "nutrient": "potassium_mg",
                "value": 1208.5287529167747,
                "threshold": 3400,
                "condition_context": "hypertension",
                "status": null
            }
        ],
        "constraint_violations": [],
        "constraint_evaluation_available": false
    },
    "biometric_detail": {
        "sleep": {
            "duration_hrs": 7.03,
            "target_hrs": 7,
            "floor_applied": false,
            "duration_status": "on_track",
            "rem_pct": 25.18,
            "rem_status": "soft_warning",
            "deep_pct": 18.19,
            "deep_status": "on_track",
            "status": "soft_warning"
        },
        "activity": {
            "steps": 0,
            "active_energy_kcal": 0,
            "deviation_status_low": "no_data",
            "deviation_status_high": "no_data",
            "baseline_kcal": null
        },
        "recovery": {
            "status": "no_data",
            "has_data": false,
            "notes": "Insufficient baseline history on at least one of resting HR or HRV. Both metrics require at least 14 days of history"
        }
    },
    "recommendations": [
        {
            "rank": 1,
            "dimension": "calories",
            "severity": "hard_flag",
            "condition_context": null,
            "driving_metric": "energy_kcal",
            "detail": {
                "metric_key": "energy_kcal",
                "logged_value": 765.6590995592429,
                "target": 2723,
                "pct_of_target": 28.118218860052988,
                "status": "hard_flag",
                "direction_note": "Being under target is never framed as success regardless of goal_direction.",
                "confidence": "heuristic",
                "source_body": null,
                "notes": "No sourced clinical value exists for calorie deviation tolerance."
            },
            "text": "Your logged calorie intake today was 766 kcal, compared to your target of 2723 kcal."
        },
        {
            "rank": 2,
            "dimension": "sleep",
            "severity": "soft_warning",
            "condition_context": null,
            "driving_metric": "sleep_rem_pct",
            "detail": {
                "duration_hrs": 7.03,
                "target_hrs": 7,
                "floor_applied": false,
                "duration_status": "on_track",
                "rem_pct": 25.18,
                "rem_status": "soft_warning",
                "deep_pct": 18.19,
                "deep_status": "on_track",
                "status": "soft_warning"
            },
            "text": "Your REM sleep percentage today was 25.18%."
        }
    ],
    "cross_signal": [],
    "metrics_shown": [
        "heart_rate",
        "steps",
        "protein",
        "energy_kcal"
    ]
}
```

## Reference thresholds

All reference values, their sources and the rules for changing them are documented in the [root README](../README.md#reference-thresholds). This API reads the same modules as the Trend Analysis Process API.


## Known limitations

- **Strict dietary constraints can't be checked.** The allergen and diet label fields in the Nutrition System API are empty for every food. The ranking has a place for them, but it never fires, and `constraint_evaluation_available` is `false` so clients know the check was skipped, not passed.
- **A partial day is judged against a full-day target.** A request at 8 am will flag calories. The Experience API blocks same-day requests before 6 pm for this reason.
- **Recovery cutoffs are the same for everyone.** They don't adapt to how much a person's numbers normally vary, and since the baseline is an average, one unusual day shifts it.
- **Calories need a calculated target.** Users who chose "prefer not to say" for biological sex get `no_data` for calories.