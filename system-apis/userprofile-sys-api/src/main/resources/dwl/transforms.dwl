%dw 2.0
fun transformGoal (user_goal)=
	user_goal map (goal)->{
	goalId: goal.goal_id,
	userId: goal.user_id,
	goalType: goal.goal_type,
	goalDirection: goal.goal_direction,
	targetValue: goal.target_value,
	targetUnit: goal.target_unit,
	isPrimary: goal.is_primary,
	isActive: goal.is_active,
	setAt: goal.set_at,
	achievedAt: goal.achieved_at
}
fun transformConditions (user_conditions) = 
user_conditions map (condition) ->{
	conditionId: condition.condition_id,
	userId: condition.user_id,
	conditionType: condition.condition_type,
	diagnosedAt: condition.diagnosed_at,
	notes: condition.notes,
	managementStatus: condition.management_status,
	isActive: condition.is_active,
	createdAt: condition.created_at
}
fun transformConstrains (dietary_constraints)= 
dietary_constraints map (row)->{
	constraintId: row.constraint_id,
	userId: row.user_id,
	constraintType: row.constraint_type,
	constraintValue: row.constraint_value,
	severity: row.severity,
	isActive: row.is_active,
	createdAt: row.created_at
}
fun transformPreferences (user_preference)=
 if ( sizeOf(user_preference) > 0 ) {
	preferenceId: user_preference.preference_id,
	userId: user_preference.user_id,
	insightFrequency: user_preference.insight_frequency,
	preferredWearableMetrics: user_preference.preferred_wearable_metrics,
	preferredNutritionMetrics: user_preference.preferred_nutrition_metrics,
	typicalBreakfastTime: user_preference.typical_breakfast_time,
	typicalLunchTime: user_preference.typical_lunch_time,
	typicalDinnerTime: user_preference.typical_dinner_time,
	typicalSleepTime: user_preference.typical_sleep_time,
	targetSleepDurationHrs: user_preference.target_sleep_duration_hrs,
	unitsSystem: user_preference.units_system,
	createdAt: user_preference.created_at,
	updatedAt: user_preference.updated_at
}else null
fun returnGoal (payload)=
{
	goalId: payload[0].goal_id,
	userId: payload[0].user_id,
	goalType: payload[0].goal_type,
	goalDirection: payload[0].goal_direction,
	targetValue: payload[0].target_value,
	targetUnit: payload[0].target_unit,
	isPrimary: payload[0].is_primary,
	isActive: payload[0].is_active,
	setAt: payload[0].set_at,
	achievedAt: payload[0].achieved_at
}
fun returnCondition (payload)= 
{
	"conditionId": payload[0].condition_id,
	"userId": payload[0].user_id,
	"conditionType": payload[0].condition_type,
	"managementStatus": payload[0].management_status,
	"diagnosedAt": payload[0].diagnosed_at,
	"notes": payload[0].notes,
	"isActive": payload[0].is_active,
	"createdAt": payload[0].created_at,
	"updatedAt": payload[0].updated_at
}

fun returnPreference (payload)=
{
  preferenceId: payload[0].preference_id,
  userId: payload[0].user_id,
  insightFrequency: payload[0].insight_frequency,
  preferredWearableMetrics: payload[0].preferred_wearable_metrics,
  preferredNutritionMetrics: payload[0].preferred_nutrition_metrics,
  typicalBreakfastTime: if(payload[0].typical_breakfast_time != null) payload[0].typical_breakfast_time != null as String {format: "HH:mm:ss"} else null,
  typicalLunchTime: if(payload[0].typical_lunch_time != null) payload[0].typical_lunch_time != null as String {format: "HH:mm:ss"} else null,
  typicalDinnerTime: if(payload[0].typical_dinner_time != null)payload[0].typical_dinner_time as String {format: "HH:mm:ss"} else null,
  typicalSleepTime: if (payload[0].typical_sleep_time != null) payload[0].typical_sleep_time as String {format: "HH:mm:ss"} else null,
  targetSleepDurationHrs: payload[0].target_sleep_duration_hrs,
  unitsSystem: payload[0].units_system,
  createdAt: payload[0].created_at,
  updatedAt: payload[0].updated_at
} 

fun returnConstraint(payload) = 
{
	"constraintId": payload[0].constraint_id,
	"userId": payload[0].user_id,
	"constraintType": payload[0].constraint_type,
	"constraintValue": payload[0].constraint_value,
	"severity": payload[0].severity,
	"isActive": payload[0].is_active,
	"createdAt": payload[0].created_at
}
