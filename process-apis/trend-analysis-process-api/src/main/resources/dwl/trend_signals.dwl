%dw 2.0
// Sonnet 5.5
import contextClause, fmt0, fmt1, nutrientInfo, isFlagged from dwl::trend_text

// Rule 1: sleep and recovery are both moving in the wrong direction.
fun rule1(t, days) =
  if (t.sleep.sufficient_data == true and t.recovery.sufficient_data == true
      and t.sleep.trend_direction == "declining"
      and t.recovery.trend_direction == "declining")
    [{
      present: true,
      signals_involved: ["sleep", "recovery"],
      driving_metric: null,
      text: "Over the past $(days) days your sleep and your recovery readings have both been moving in the wrong direction. Together that can point to your body needing more recovery time."
    }]
  else []

// Rule 2: a flagged micronutrient while activity is holding up or increasing.
// One entry per qualifying nutrient. Activity counts as holding up when its status
// is on_track or its trend is improving, and neither can be true with no recorded minutes.
fun activityHoldingUp(t) =
  t.activity.sufficient_data == true
  and (t.activity.status == "on_track" or t.activity.trend_direction == "improving")

fun rule2(t, days) =
  if (not activityHoldingUp(t)) []
  else ((t.micronutrients default []) filter ((n) ->
      n.sufficient_data == true
      and n.evaluation_type == "adequacy"
      and isFlagged(n.status))
    ) map ((n) -> do {
      var name = (nutrientInfo[n.nutrient].name) default n.nutrient
      ---
      {
        present: true,
        signals_involved: [n.nutrient, "activity"],
        driving_metric: n.nutrient,
        text: "Your $(name) intake averaged below the reference intake while your recorded activity has held up or increased over the past $(days) days, so your intake may not be keeping pace with how active you are."
              ++ contextClause(n.condition_context)
      }
    })

// Rule 3: calories flagged and below target, moving further below, while recorded
// activity held steady or increased. The recorded-minutes check stops a window with no
// workouts from reading as "activity held steady".
fun rule3(t, days) =
  if (t.calories.sufficient_data == true and t.activity.sufficient_data == true
      and isFlagged(t.calories.status)
      and t.calories.avg_calories < t.calories.caloric_target
      and t.calories.trend_direction == "declining"
      and (t.activity.trend_direction == "improving" or t.activity.trend_direction == "stable")
      and ((t.activity.moderate_min_total default 0) + (t.activity.vigorous_min_total default 0)) > 0)
    [{
      present: true,
      signals_involved: ["calories", "activity"],
      driving_metric: null,
      text: "Over the past $(days) days your calorie intake moved further below your target while your recorded activity held steady or increased, which can add up to a growing energy shortfall."
    }]
  else []

// Rule 4: irregular sleep on its own.
fun rule4(t, days) =
  if (t.sleep.sufficient_data == true and t.sleep.consistency == "irregular"
      and t.sleep.consistency_cv != null and t.sleep.avg_duration_hrs != null)
    [{
      present: true,
      signals_involved: ["sleep"],
      driving_metric: null,
      text: "The length of your nights varied by about $(fmt0(t.sleep.consistency_cv * 100))% around your $(fmt1(t.sleep.avg_duration_hrs))-hour average over the past $(days) days, which we count as irregular."
    }]
  else []

fun buildSignals(trends, days) = do {
  var t = trends default {}
  var d = days as Number
  ---
  rule1(t, d) ++ rule2(t, d) ++ rule3(t, d) ++ rule4(t, d)
}