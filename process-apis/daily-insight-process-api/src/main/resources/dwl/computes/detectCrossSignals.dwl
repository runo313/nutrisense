%dw 2.0
fun detectCrossSignals(nutritionDetail, biometricDetail, activeConditions, primaryGoal) = do {
	var isFlagged = (status) -> (status == "soft_warning") or (status == "hard_flag")
	var sleepFlagged = isFlagged(biometricDetail.sleep.status)
	var hrFlagged = isFlagged(biometricDetail.recovery.hr_status)
	var caloriesLowFlagged = isFlagged(nutritionDetail.calories.status) and nutritionDetail.calories.pct_of_target < 100
	var activeEnergyHighFlagged = isFlagged(biometricDetail.activity.deviation_status_high default "on_track")
	var proteinFlagged = isFlagged(nutritionDetail.macros.protein.status)
	var carbsHighFlagged = isFlagged(nutritionDetail.macros.carbohydrate.status) and nutritionDetail.macros.carbohydrate.amdr_status == "hard_flag"
	var sodiumFlagged = isFlagged(nutritionDetail.macros.sodium.status)
	var fiberLowFlagged = isFlagged(nutritionDetail.macros.fiber.status)
	var satFatFlagged = isFlagged(nutritionDetail.macros.saturatedFat.status)
	var hasDiabetes = activeConditions[0].conditionType == 'type2_diabetes' or activeConditions[0].conditionType == 'prediabetes' 
	var hasHypertension = activeConditions[0].conditionType == "hypertension"
	var hasHighCholesterol = activeConditions[0].conditionType == "high_cholesterol"
	---
	[if ( sleepFlagged and hrFlagged ) {
		key: "recovery_stress",
		signals_involved: ["sleep", "recovery"]
	} else null,
    if ( caloriesLowFlagged and activeEnergyHighFlagged ) {
		key: "energy_deficit",
		signals_involved: ["calories", "activity"]
	} else null,
    if ( proteinFlagged and activeEnergyHighFlagged and primaryGoal == "athletic_performance" ) {
		key: "recovery_risk",
		signals_involved: ["macros", "activity"]
	} else null,
    if ( carbsHighFlagged and sleepFlagged and hasDiabetes ) {
		key: "blood_sugar_disruption_risk",
		signals_involved: ["macros", "sleep"]
	} else null,
    if ( sodiumFlagged and hrFlagged and hasHypertension ) {
		key: "cardiovascular_stress",
		signals_involved: ["macros", "recovery"]
	} else null,
    if ( fiberLowFlagged and satFatFlagged and hasHighCholesterol ) {
		key: "lipid_profile_risk",
		signals_involved: ["macros"]
	} else null] filter ($ != null)
    
}
