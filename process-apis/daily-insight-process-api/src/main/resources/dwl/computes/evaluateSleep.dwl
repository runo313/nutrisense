%dw 2.0
import * from dwl::thresholds::static_thresholds

var statusSeverity = {
  on_track: 0,
  soft_warning: 1,
  hard_flag: 2
}

var severityToStatus = {
  "0": "on_track",
  "1": "soft_warning",
  "2": "hard_flag"
}

fun evaluateSleepDuration(loggedDurationHrs, userTargetHrs, durationRow) = do {
  var effectiveTarget = max([userTargetHrs default 0, durationRow.base_value])
  var floorApplied = effectiveTarget != (userTargetHrs default 0)
  var softBound = effectiveTarget * durationRow.soft_bound
  var hardBound = effectiveTarget * durationRow.hard_bound
  var status =
    if (loggedDurationHrs >= effectiveTarget) "on_track"
    else if (loggedDurationHrs >= softBound) "soft_warning"
    else "hard_flag"
  ---
  { status: status, effectiveTarget: effectiveTarget, floorApplied: floorApplied }
}

fun evaluateSleepRange(loggedPct, rangeRow) =
  if (loggedPct >= rangeRow.range_min and loggedPct <= rangeRow.range_max) "on_track"
  else if (loggedPct >= rangeRow.hard_bound.below and loggedPct <= rangeRow.hard_bound.above) "soft_warning"
  else "hard_flag"

fun evaluateSleep(loggedDurationHrs, loggedDeepPct, loggedRemPct, userTargetHrs) = do {
  var durationRow = (rowsForMetric("sleep_duration_hrs"))[0]
  var deepRow = (rowsForMetric("sleep_deep_pct"))[0]
  var remRow = (rowsForMetric("sleep_rem_pct"))[0]

  var durationResult = evaluateSleepDuration(loggedDurationHrs, userTargetHrs, durationRow)
  var deepStatus = evaluateSleepRange(loggedDeepPct, deepRow)
  var remStatus = evaluateSleepRange(loggedRemPct, remRow)

  var overallStatus = severityToStatus[
    (max([statusSeverity[durationResult.status], statusSeverity[deepStatus], statusSeverity[remStatus]])) as String
  ]
  ---
  {
    duration_hrs: loggedDurationHrs,
    target_hrs: durationResult.effectiveTarget,
    floor_applied: durationResult.floorApplied,
    duration_status: durationResult.status,
    rem_pct: loggedRemPct,
    rem_status: remStatus,
    deep_pct: loggedDeepPct,
    deep_status: deepStatus,
    status: overallStatus
  }
}