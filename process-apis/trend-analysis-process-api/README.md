# Trend Analysis Process API

Builds one user's health picture across a window of 7, 14 or 30 days. It pulls the same four System APIs as the Daily Insight Process API, evaluates the window against the user's own context, works out which way each area is heading, ranks what matters, and returns a single response the Experience layer can render directly.

Daily Insight focuses on "how did today go?". This APIs role is determining the patterns that matter within a time period, including the checks a single day can't support: weekly activity, micronutrient adequacy and the direction of each trend.

Nothing is saved, so calling it again for the same window reruns the whole process against the current System API data.

## Endpoints

Base path: `https://localhost:8092/api/v1/trend-analysis-papi`

| Method | Path | Purpose |
|---|---|---|
| GET | `/insights/trend/{userId}?window=&date=` | Trend for the window ending on `date`. `window` is `7`, `14` or `30` and defaults to `14`. |
| POST | `/insights/trend/{userId}/regenerate?window=&date=` | Same response shape. Forces a fresh run. |
| GET | `/health` | Health check. |

## Window and timezone

The window is fixed in length and always **ends yesterday in the user's timezone** (from the profile), so the current partial day never enters the data. A window of N days, `window_end` is `window_start + N - 1`.

## Orchestration

1. **Profile.** Goals, conditions and preferences from the User Profile System API, fetched in parallel. Mandatory: if it fails, the request aborts with `503`.
2. **Wearable window.** The daily summaries for every day in the window.
3. **Wearable baseline.** A separate 30-day summary, used for recovery at windows 7 and 14. Skipped at window 30, where the window is the baseline.
4. **Meals and nutrition.** The window's meals from the Meal Log System API, then each unique food from the Nutrition System API in parallel.
5. **Evaluate.** Mule assembles one request document and hands it to the evaluation layer in a single call (below).
6. **Rank, write text.** assembles the response.

| Condition | Result |
|---|---|
| Profile call fails | `503`, request stopped |
| Meal Log or Nutrition fails | Calories, macros and micronutrients are `insufficient_data`; everything else continues |
| Wearable window fails | Sleep, activity and recovery are `insufficient_data` |
| Baseline missing at window 7 or 14 | Recovery reports a trend direction only, with no status |
| Fewer than 70% of the window's days have data | That area is `insufficient_data` |

## How evaluation works

Each area gets one of four results: `on_track`, `soft_warning`, `hard_flag` or `insufficient_data`. A missing value is never treated as zero.

The reference values, sources and warning bands are the same ones Daily Insight uses, documented in the [Daily Insight README](../daily-insight-process-api/README.md#how-evaluation-works) and the [root README](../../README.md#reference-thresholds). This section covers how the Java layer gets from raw System API data to those results over a window.

Evaluation runs in two stages. The first stage prepares everything the areas share: the user's context, daily aggregates, coverage and the half-window split. The second runs each area's evaluator on those prepared values.

### Stage 1
**1. Resolve the user in a category.** `DemographicResolver` maps biological sex and age to a category (`male_19_plus`, `female_19_50`, `female_51_plus` or `unspecified`). A missing age or an age under 19 resolves to `out_of_scope`, and the response is a placeholder with no evaluation. Active conditions and constraints are collected alongside the bucket, since they decide which thresholds apply and which nutrients are shown.

**2. Build daily aggregates.** The lists from the system APIs are summed into one value per day per metric:

| Source | Daily aggregate |
|---|---|
| Meals and foods | Each item's nutrients scaled by its logged quantity, summed per day: calories, the six macros and the micronutrients |
| Sleep sessions | Total sleep per night (Core, Deep, REM and unlabeled). Time awake and sessions missing a start or end are left out |
| Point metrics | Resting heart rate and HRV per day |
| Workouts | minutes per day |

A day only counts as covered if it has at least one value for that source. Window averages are taken over covered days, not the full window, so a missed day lowers coverage instead of dragging the average toward zero.

**3. Check coverage.** Each source's covered days are compared with 70% of the window. 5 of 7, 10 of 14, 21 of 30. Meal log coverage handles calories, macros and micronutrients. Wearable coverage handles sleep, activity and recovery. An area below its gate skips evaluation and returns `insufficient_data`, with its data fields null and its targets still filled in. The counts go into the `coverage` block for messages like "8 of 14 days logged".

**4. Split the window.** The days are divided into a first and second half (4 / 3 at window 7, 7 / 7 at 14, 15 / 15 at 30), and each metric's half averages are computed. Every evaluator uses these for `trend_direction`:

- More than 5% closer to the target in the second half: `improving`.
- More than 5% further away: `declining`.
- Within 5%: `stable`.

Direction is measured against the target, not up or down. Sodium falling under a cap is `improving`; protein falling under a minimum is `declining`.

### Stage 2

Every evaluator follows the same sequence:

1. Check the coverage gate.
2. Take the window value from the daily aggregates.
3. Resolve the threshold for this user.
4. Compare the value with the threshold to set the status.
5. Compare the half averages to set the direction.

Each area also reports an `evaluation_type`:

- `adequacy` for minimums to reach
- `exposure` for caps to stay under
- `guideline` or `personal_baseline` for a published target compared directly

#### Calories and macros

- **Requires:** meals logged on at least 70% of the window's days. Below that, calories return `insufficient_data` and the macros block returns `sufficient_data: false` with all six entries `insufficient_data`. Targets are still filled in.
- **Value:** daily totals averaged across the logged days.
- **Calorie target:** calculated from height, weight, age, sex and activity level with the Mifflin-St Jeor equation retrieved from the system api. On track between 80 and 120% of target, a soft warning between 60 and 140%, and a hard flag beyond that.
- **Carbohydrate and fat targets:** ranges set as a share of the calorie target's energy. Carbs should supply 45 to 65% of calories and fat 20 to 35%.
- **Protein target:** grams per kg of body weight, depending on the user's goal.
- **Fiber target:** 14 g for every 1,000 kcal of the calorie target.
- **Saturated fat and sodium:** caps that tighten for users with `high_cholesterol` or `hypertension`.
- **Status:** each macro gets its own status. The block has no overall status, so its most severe entry represents macros in the ranking.
- **Direction:** measures distance from target, so a calorie average drifting further below or further above target both read as `declining`. An average under target is never framed as success.

#### Micronutrients

- **Requires:** meals logged on at least 70% of the window's days.
- **Why they get a status:** reference intakes describe habitual intake, which is what a window average measures.
- **Which nutrients:** iron, zinc, calcium, B12 and potassium are evaluated only when a condition or constraint on the profile makes them relevant.
- **Threshold:** each nutrient's reference is narrowed to the user's demographic bucket, then adjusted by any dietary multiplier. The vegetarian and vegan iron multiplier of 1.8 is clamped to 80% of the upper limit.
- **Status:** the window average against the resolved reference. Each entry carries its `evaluation_type` and the condition that surfaced it.
- **Potassium with `kidney_disease`:** a minimum doesn't fit here, so the evaluation becomes an exposure check and the status is `insufficient_data`.
- **"Prefer not to say":** no sex-specific rows exist, so iron, zinc, calcium and potassium are left out.

#### Sleep

- **Requires:** wearable data on at least 70% of the window's days.
- **Night assignment:** each night belongs to the date the user woke up, so a night crossing midnight counts once.
- **Duration:** average nightly sleep against the user's sleep goal or 7 hours, whichever is higher.
- **Stage shares:** deep and REM sleep as a share of total sleep.
- **Consistency:** the coefficient of variation of nightly duration, meaning how much nights vary around the average. Above 0.15 is irregular and a soft warning; above 0.25 is a hard flag. These cutoffs are our own choice.
- **Status:** the worst of duration, stage shares and consistency.

#### Recovery

- **Requires:** wearable data on at least 70% of the window's days.
- **Value:** average resting heart rate and HRV across the window.
- **Windows 7 and 14:** both averages are compared with the user's 30-day baseline. Resting heart rate is a soft warning above 5% over baseline and a hard flag above 10%. HRV is a soft warning more than 15% below and a hard flag more than 25% below. With no usable baseline, recovery is `insufficient_data`, but the averages are still shown.
- **Window 30:** the window is the baseline period, so there's nothing separate to compare against. No status, only the direction between halves, and recovery doesn't enter the ranking.

#### Activity

- **Requires:** wearable data on at least 70% of the window's days.
- **Workout intensity:** classified in three tiers:
  1. Energy burned per kg per minute, with cutoffs at 0.035 and 0.0875.
  2. For walking and hiking, speed, with cutoffs at 2.5 and 5.0 mph.
  3. An activity-type table for anything still unclassified.
- **Duration:** workout duration is used, not end time.
- **Light workouts:** add no minutes but still count the day as covered.
- **Minutes:** converted to moderate-equivalent minutes, where one vigorous minute counts as two moderate.
- **Scaling:** the total is scaled from covered days up to the full window, so missed days aren't read as inactivity.
- **Status:** compared with the CDC guideline of 150 moderate or 75 vigorous minutes per week, scaled to the window's length. The hard bound is 60% of the moderate target.
- **Direction:** compares daily moderate-equivalent minutes between the two halves.

### Pattern signals

Four rules, checked against `dimension_trends`. An area that is `insufficient_data` or has no direction can't fire a rule, so no signal is ever built on missing data.

| Fires when | `signals_involved` |
|---|---|
| Sleep and recovery are both `declining` | sleep, recovery |
| A micronutrient is flagged while activity is `on_track` or `improving` | the nutrient, activity |
| Calories are flagged, below target and `declining`, while activity is `improving` or `stable` with minutes recorded | calories, activity |
| Sleep consistency is `irregular` | sleep |

The micronutrient rule produces one entry per qualifying nutrient. The recorded-minutes check on the calories rule stops a window with no workouts from reading as "activity held steady".

### Text generation

The mode comes from `insightGenerationMode` in the user's preferences and follows the same routing and fallbacks as Daily Insight.

## Example response

Trimmed to one entry per array. The values are illustrative.

```json
{
    "user_id": "runo313",
    "window_days": 14,
    "window_start": "2026-10-11",
    "window_end": "2026-10-24",
    "generated_at": "2026-11-15T11:00:35-05:00",
    "generation_mode": "template",
    "threshold_version": "0.1",
    "coverage": {
        "meal_log": { "days_covered": 11, "days_required": 10, "sufficient": true },
        "wearable": { "days_covered": 14, "days_required": 10, "sufficient": true }
    },
    "dimension_trends": {
        "sleep": {
            "sufficient_data": true,
            "avg_duration_hrs": 6.4,
            "target_hrs": 7,
            "consistency": "irregular",
            "consistency_cv": 0.21,
            "status": "soft_warning",
            "trend_direction": "declining"
        },
        "recovery": {
            "sufficient_data": true,
            "avg_resting_hr": 64,
            "baseline_resting_hr": 61,
            "avg_hrv_sdnn": 49,
            "baseline_hrv_sdnn": 55,
            "status": "soft_warning",
            "trend_direction": "declining"
        },
        "activity": {
            "sufficient_data": true,
            "moderate_min_total": 165,
            "vigorous_min_total": 40,
            "status": "soft_warning",
            "trend_direction": "stable"
        },
        "micronutrients": [
            {
                "nutrient": "iron_mg",
                "avg_intake": 9.2,
                "rda": 18,
                "condition_context": "anemia",
                "status": "hard_flag",
                "trend_direction": "stable"
            }
        ]
    },
    "recommendations": [
        {
            "rank": 1,
            "dimension": "micronutrients",
            "nutrient": "iron_mg",
            "severity": "hard_flag",
            "condition_context": "anemia",
            "text": "Over the past 14 days your average iron intake was 9.2 mg, below the 18 mg reference intake."
        }
    ],
    "pattern_signals": [
        {
            "present": true,
            "signals_involved": ["sleep", "recovery"],
            "driving_metric": null,
            "text": "Over the past 14 days your sleep and your recovery readings have both been moving in the wrong direction."
        }
    ],
    "metrics_shown": ["heart_rate", "steps", "protein", "iron"]
}
```

## Known limitations
- **Strict dietary constraints can't be checked** The Nutrition System API's allergen and diet label fields are empty.
- **The direction rule is simple.** Two half-averages and a 5% cutoff are easy to read but can miss a change in the middle of a window, and the 4 / 3 split at window 7 weights the halves unevenly.
- **Users under 19 are out of scope.** A missing age or an age under 19 returns the out-of-scope placeholder rather than an evaluation.
- **"Prefer not to say" drops sex-specific nutrients.** Iron, zinc, calcium and potassium don't appear for those users.
