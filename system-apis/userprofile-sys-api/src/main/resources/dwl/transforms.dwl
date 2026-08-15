%dw 2.0
fun transformGoal (user_goal)=
	user_goal map (goal)->{
	goalId: goal.goalId,
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
