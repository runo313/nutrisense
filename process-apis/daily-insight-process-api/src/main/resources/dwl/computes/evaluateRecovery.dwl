%dw 2.0
import * from dwl::thresholds::baseline_deviations

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

fun averageTodayValue(dailyPointMetrics, metricType) = do {
  var readings = dailyPointMetrics filter ($.metric_type == metricType)
  var values = readings map ($.value)
  ---
  if (isEmpty(values)) null
  else (values reduce ((v, acc = 0) -> acc + v)) / sizeOf(values)
}

fun evaluateRecovery(wearableStatus) = do {
  var baselineData = wearableStatus.baselineData
  var dailyPointMetrics = wearableStatus.dailyData.point_metrics

  var hrvData = baselineData.point_metrics filter ((item, index) -> item.metric_type == "hrv" ) 
  var hrData = baselineData.point_metrics filter ((item, index) -> item.metric_type == "resting_heart_rate" )
  
  var hrCount = hrData[0].count default 0
  var hrvCount = hrvData[0].count default 0
  
  var todayHr = averageTodayValue(dailyPointMetrics, "resting_heart_rate")
  var todayHrv = averageTodayValue(dailyPointMetrics, "hrv")

  var hrSufficient = hasSufficientHistory("resting_hr_bpm", hrCount)
  var hrvSufficient = hasSufficientHistory("hrv_sdnn", hrvCount)
  ---
  if (not wearableStatus.baselineAvailable or not hrSufficient or not hrvSufficient or todayHr == null or todayHrv == null)
    {
      status: "no_data",
      has_data: false,
      notes: "Insufficient baseline history on at least one of resting HR or HRV. Both metrics require at least 14 days of history"
    }
  else do {
    var hrBaseline = hrData[0].average
    var hrvBaseline = hrvData[0].average

    var hrBounds = resolveBaselineBounds("resting_hr_bpm", hrBaseline)
    var hrvBounds = resolveBaselineBounds("hrv_sdnn", hrvBaseline)

    var hrStatus =
      if (todayHr <= hrBounds.soft_bound) "on_track"
      else if (todayHr <= hrBounds.hard_bound) "soft_warning"
      else "hard_flag"

    var hrvStatus =
      if (todayHrv >= hrvBounds.soft_bound) "on_track"
      else if (todayHrv >= hrvBounds.hard_bound) "soft_warning"
      else "hard_flag"

    var overallStatus = severityToStatus[
      (max([statusSeverity[hrStatus], statusSeverity[hrvStatus]])) as String
    ]
    ---
    {
      resting_hr_bpm: todayHr as String {format: "0.00"} as Number,
      baseline_hr_bpm: hrBaseline,
      hrv_ms: todayHrv as String {format: "0.00"} as Number,
      baseline_hrv_ms: hrvBaseline,
      hr_status: hrStatus,
      hrv_status: hrvStatus,
      status: overallStatus,
      has_data: true
    }
  }
}