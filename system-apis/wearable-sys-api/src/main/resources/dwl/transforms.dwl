%dw 2.0
fun constructInputParamsWearables(userId,queryParams)= {
	user_id: userId as String,
	limit: queryParams.limit,
	offset: queryParams.offset
} ++ (if ( queryParams.startDate != null and queryParams.endDate != null ) {
	startDate: queryParams.startDate,
	endDate: queryParams.endDate
}else {
})
fun sanitizedTypes(item)= if (item != null ) item map ((value) -> value replace /[^a-zA-Z0-9_]/ with "") else null
fun constructInputParamsWearablesSummary(userId,startDate,endDate)= {
	user_id: userId as String,
	resolvedStartDate: startDate,
	resolvedEndDate: endDate
}
fun constructDateClause0(startDate,endDate)= if ( startDate != null and endDate != null ) " AND recorded_at BETWEEN :startDate AND :endDate"
  else ""
fun constructDateClause1(startDate,endDate)= if ( startDate != null and endDate != null ) " AND start_time BETWEEN :startDate AND :endDate"
  else ""

 fun constructDateClause2(startDate,endDate)= if ( startDate != null and endDate != null ) " AND w.start_time BETWEEN :startDate AND :endDate"
  else "" 
  
fun noDataResponse(correlationId)={
  code: "WEARABLE-404",
  message: "No data found for the provided user and filters",
  correlationId: correlationId,
  timestamp: now() as String {format: "yyyy-MM-dd'T'HH:mm:ssXXX"},
  originLayer: "system"
}

fun transformWorkOuts(workOutsResult)= workOutsResult groupBy ((item, index) -> item.id)
pluck ((rows) ->{
  user_id: rows[0].user_id,
  activity_type: rows[0].activity_type,
  start_time: rows[0].start_time,
  end_time: rows[0].end_time,
  source_device: rows[0].source_device,
  duration: rows[0].duration,
  duration_unit: rows[0].duration_unit,
  energy_burned: rows[0].energy_burned,
  statistics: rows filter ((row) -> row.stat_metric_type !=null )
  map ((row) ->{
      metric_type: row.stat_metric_type,
      unit: row.stat_unit,
      sum_value: row.sum_value,
      average_value: row.average_value,
      minimum_value: row.minimum_value,
      maximum_value: row.maximum_value
  } )
} )

fun transformPointMetricsSummary(startDate,endDate,pointMetricsResult)= pointMetricsResult map (item)->{
	metric_type: item.metric_type,
	startDate: startDate,
	endDate: endDate,
	average: item.average,
	min: item.min,
	max: item.max,
	count: item.count,
	unit: item.unit
}

fun transformSleepSummary(startDate,endDate,sleepSessionsResult)={
    start_date: startDate,
    end_date: endDate,
    total_duration: sleepSessionsResult.stage_duration reduce ((item, accumulator) -> item + accumulator),
    stages: sleepSessionsResult map ((item, index) ->{
        stage: item.stage,
        stage_duration: item.stage_duration
    } ) }
    
fun transformWorkoutsSummary(startDate,endDate,workOutsResult1,workOutsResult2)={
  start_date: startDate,
  end_date: endDate,
  total_workouts: workOutsResult1[0].total_workouts,
  total_duration: workOutsResult1[0].total_duration,
  duration_unit: workOutsResult1[0].duration_unit,
  total_energy_burned: workOutsResult1[0].total_energy_burned,
  by_activity_type: workOutsResult2
  groupBy ((row) -> row.activity_type)
  pluck ((rows, activityType) -> 
      do {
        var heartRate = rows filter ($.metric_type == "heart_rate")
        var activeEnergy = rows filter ($.metric_type == "active_energy")
        var basalEnergy = rows filter ($.metric_type == "basal_energy")
        var distance = rows filter ($.metric_type == "distance")
        var steps = rows filter ($.metric_type == "steps")
        var durationRow = workOutsResult1 filter ($.activity_type == activityType)
        ---
        {
          activity_type: activityType,
          count: rows[0].count,
          total_duration: durationRow[0].total_duration default null,
          (total_active_energy: activeEnergy[0].sum_value) if (sizeOf(activeEnergy) > 0),
          (total_basal_energy: basalEnergy[0].sum_value) if (sizeOf(basalEnergy) > 0),
          (total_energy_burned: (activeEnergy[0].sum_value default 0) + (basalEnergy[0].sum_value default 0)) if (sizeOf(activeEnergy) > 0 or sizeOf(basalEnergy) > 0),
          (total_distance: distance[0].sum_value) if (sizeOf(distance) > 0),
          (distance_unit: distance[0].unit) if (sizeOf(distance) > 0),
          (total_steps: steps[0].sum_value) if (sizeOf(steps) > 0),
          (avg_heart_rate: heartRate[0].avg_value) if (sizeOf(heartRate) > 0),
          (min_heart_rate: heartRate[0].min_value) if (sizeOf(heartRate) > 0),
          (max_heart_rate: heartRate[0].max_value) if (sizeOf(heartRate) > 0)
        }
      }
    )
}
