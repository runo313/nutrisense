%dw 2.0
import * from dwl::thresholds::baseline_deviations

fun evaluateActiveEnergy(todayActiveEnergyKcal, baselineData) = do {
  var baselineRow = (baselineData.point_metrics filter ($.metric_type == "active_energy_burned"))[0]
  var baselineCount = baselineRow.count default 0
  var sufficient = hasSufficientHistory("active_energy_kcal", baselineCount)
  ---
  if ((not sufficient) or (todayActiveEnergyKcal == null))
    { deviation_status_low: "no_data", deviation_status_high: "no_data" }
  else do {
    var bounds = resolveBaselineBounds("active_energy_kcal", baselineRow.average)
    var lowStatus =
      if (todayActiveEnergyKcal >= bounds.soft_bound_low) "on_track"
      else if (todayActiveEnergyKcal >= bounds.hard_bound_low) "soft_warning"
      else "hard_flag"
    var highStatus =
      if (todayActiveEnergyKcal <= bounds.soft_bound_high) "on_track"
      else if (todayActiveEnergyKcal <= bounds.hard_bound_high) "soft_warning"
      else "hard_flag"
    ---
    { deviation_status_low: lowStatus, deviation_status_high: highStatus, baseline_kcal: baselineRow.average }
  }
}