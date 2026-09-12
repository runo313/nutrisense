%dw 2.0

fun stageDurationMin(stageSessions) = (stageSessions map ((item) ->
    ((item.end_time - item.start_time) as Number) / 60
  )) reduce ((item, acc = 0) -> item + acc)

fun computeSleepAggregate(sleepSessions) = do {
  var validSessions = sleepSessions filter ($.start_time != null and $.end_time != null)
  var grouped = validSessions groupBy ((sleep) -> sleep.stage)
  var deepMin = stageDurationMin(grouped.deep default [])
  var coreMin = stageDurationMin(grouped.core default [])
  var remMin = stageDurationMin(grouped.rem default [])
  var unspecifiedMin = stageDurationMin(grouped.unspecified default [])
  var totalMin = deepMin + coreMin + remMin + unspecifiedMin
  var totalHrs = totalMin / 60
  var deepPct = if (totalMin > 0) (deepMin / totalMin) * 100 else 0
  var remPct = if (totalMin > 0) (remMin / totalMin) * 100 else 0
  ---
  {
    durationHrs: totalHrs as String {format: "0.00"} as Number,
    deepPct: deepPct as String {format: "0.00"} as Number,
    remPct: remPct as String {format: "0.00"} as Number,
    deepMin: deepMin as String {format: "0.00"} as Number,
    coreMin: coreMin as String {format: "0.00"} as Number,
    remMin: remMin as String {format: "0.00"} as Number,
    unspecifiedMin: unspecifiedMin as String {format: "0.00"} as Number
  }
}