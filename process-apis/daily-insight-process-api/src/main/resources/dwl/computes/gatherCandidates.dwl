%dw 2.0
var statusSeverity = {
	on_track: 0,
	soft_warning: 1,
	hard_flag: 2,
	no_data: 3
}
var severityToStatus = {
	"0": "on_track",
	"1": "soft_warning",
	"2": "hard_flag",
	"3": "no_data"
}
fun gatherCandidates(nutritionDetail, biometricDetail) = do {
	var caloriesCandidate = 
    if ( nutritionDetail.calories.status == "on_track" ) null
    else {
		dimension: "calories",
		status: nutritionDetail.calories.status,
		condition_context: null,
		driving_metric: "energy_kcal",
		detail: nutritionDetail.calories
	}
	var macroSubResults = [nutritionDetail.macros.protein,
    nutritionDetail.macros.carbohydrate,
    nutritionDetail.macros.fat,
    nutritionDetail.macros.saturatedFat,
    nutritionDetail.macros.fiber]
	var worstMacro = (macroSubResults orderBy ((m) -> statusSeverity[m.status]))[0]
	var macrosCandidate =
    if ( worstMacro.status == "on_track" ) null
    else {
		dimension: "macros",
		status: worstMacro.status,
		condition_context: worstMacro.condition_context default null,
		driving_metric: worstMacro.metric_key,
		detail: worstMacro
	}
	var sleepCandidate =
    if ( biometricDetail.sleep.status == "on_track" or biometricDetail.sleep.status == "no_data" ) null
    else do {
		var sleepSubResults = [{
			metric_key: "sleep_duration_hrs",
			status: biometricDetail.sleep.duration_status
		},
        {
			metric_key: "sleep_deep_pct",
			status: biometricDetail.sleep.deep_status
		},
        {
			metric_key: "sleep_rem_pct",
			status: biometricDetail.sleep.rem_status
		}]
		var worstSleep = (sleepSubResults orderBy ((s) -> -statusSeverity[s.status]))[0]
		---
		{
			dimension: "sleep",
			status: biometricDetail.sleep.status,
			condition_context: null,
			driving_metric: worstSleep.metric_key,
			detail: biometricDetail.sleep
		}
	}
	var recoveryCandidate =
    if ( biometricDetail.recovery.status == "on_track" or biometricDetail.recovery.status == "no_data" ) null
    else do {
		var drivingMetric = if ( statusSeverity[biometricDetail.recovery.hr_status] >= statusSeverity[biometricDetail.recovery.hrv_status] ) "resting_hr_bpm" else "hrv_sdnn"
		---
		{
			dimension: "recovery",
			status: biometricDetail.recovery.status,
			condition_context: null,
			driving_metric: drivingMetric,
			detail: biometricDetail.recovery
		}
	}
	---
	[caloriesCandidate, macrosCandidate, sleepCandidate, recoveryCandidate] filter ($ != null)
}
fun rankCandidates(candidates) =
  candidates orderBy ((c) -> -(statusSeverity[c.status] * 10 + (if ( c.condition_context != null ) 1 else 0)))


fun buildRecommendations(candidates) = do {
  var ranked = rankCandidates(candidates)
  var topCount = min([3, sizeOf(ranked)])
  ---
  ranked[0 to (topCount - 1)] map ((c, index) -> {
    rank: index + 1,
    dimension: c.dimension,
    severity: c.status,
    condition_context: c.condition_context,
    text: null
  })
}
