%dw 2.0
import evaluateAmdrRange from dwl::computes::evaluateAmdrRange

fun evaluateCarbFloor(loggedCarbG, floorRow) =
  if (loggedCarbG >= floorRow.base_value[0]) "on_track"
  else if (loggedCarbG >= floorRow.hard_bound[0]) "soft_warning"
  else "hard_flag"
  
// Maps a status to a severity rank so two statuses can be compared without writing out every combination by hand.
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

// Combines the AMDR result (primary) with the floor result (used for escalation-only).
// Never returns a status less severe than either individual input.
fun combineCarbStatus(amdrStatus, floorStatus) = do {
  var worseRank = max([statusSeverity[amdrStatus], statusSeverity[floorStatus]])
  ---
  severityToStatus[worseRank as String]
}

fun evaluateCarbohydrate(loggedCarbG, resolvedAmdrRange, floorRow) = do {
  var amdrResult = evaluateAmdrRange(loggedCarbG, resolvedAmdrRange, "carbohydrate_g")
  var floorStatus = evaluateCarbFloor(loggedCarbG, floorRow)
  var finalStatus = combineCarbStatus(amdrResult.status, floorStatus)
  ---
  amdrResult ++ {
    status: finalStatus,
    amdr_status: amdrResult.status,
    floor_status: floorStatus,
    floor_base_g: floorRow.base_value[0],
    floor_hard_bound_g: floorRow.hard_bound[0],
    condition_context: null,
    excluded_override_note: "No diabetes/prediabetes carbohydrate override exists. ADA guidance is explicitly individualized; evaluation uses the general population AMDR for all users regardless of condition. See exclusions in threshold-metadata.dwl."
  }
}