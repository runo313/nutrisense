%dw 2.0

fun buildDataCompleteness(mealStatus, wearableStatus) = {
  meals_logged: mealStatus.mealsLogged,
  nutrition_resolved: mealStatus.nutritionResolved,
  wearable_daily_data_present: wearableStatus.dailyDataAvailable,
  wearable_baseline_available: wearableStatus.baselineAvailable,
  profile_complete: true
}

fun buildNutritionDetail(evaluatedNutrition, microsDetail) = {
  total_calories: evaluatedNutrition.calories.logged_value,
  caloric_target: evaluatedNutrition.calories.target,
  macros: {
    protein: { value: evaluatedNutrition.macros.protein.logged_value_g, target: evaluatedNutrition.macros.protein.target_g, status: evaluatedNutrition.macros.protein.status },
    carbohydrates: { value: evaluatedNutrition.macros.carbohydrate.logged_value_g, range_min: evaluatedNutrition.macros.carbohydrate.range_min_g, range_max: evaluatedNutrition.macros.carbohydrate.range_max_g, status: evaluatedNutrition.macros.carbohydrate.status },
    fat: { value: evaluatedNutrition.macros.fat.logged_value_g, range_min: evaluatedNutrition.macros.fat.range_min_g, range_max: evaluatedNutrition.macros.fat.range_max_g, status: evaluatedNutrition.macros.fat.status },
    saturated_fat: { value: evaluatedNutrition.macros.saturatedFat.logged_value_g, target: evaluatedNutrition.macros.saturatedFat.cap_g, status: evaluatedNutrition.macros.saturatedFat.status },
    fiber: { value: evaluatedNutrition.macros.fiber.logged_value_g, target: evaluatedNutrition.macros.fiber.target_g, status: evaluatedNutrition.macros.fiber.status },
    sodium: { value: evaluatedNutrition.macros.sodium.logged_value_mg, target: evaluatedNutrition.macros.sodium.cap_mg, status: evaluatedNutrition.macros.sodium.status }
  },
  micronutrients: microsDetail,
  constraint_violations: [],
  constraint_evaluation_available: false
}

fun buildBiometricDetail(sleepResult, activityResult, recoveryResult) = {
  sleep: sleepResult,
  activity: activityResult,
  recovery: recoveryResult
}

fun buildDimensionStatus(nutritionDetail, biometricDetail,microsDetail) = {
  sleep: { status: biometricDetail.sleep.status, has_data: biometricDetail.sleep.status != "no_data" },
  activity: { status: null, has_data: biometricDetail.activity.steps != null },
  recovery: { status: biometricDetail.recovery.status, has_data: biometricDetail.recovery.status != "no_data" },
  calories: { status: nutritionDetail.calories.status, has_data: nutritionDetail.calories.status != null },
  macros: { status: nutritionDetail.macros.status, has_data: true },
  micros: { status: null, has_data: sizeOf(microsDetail) > 0 }
}