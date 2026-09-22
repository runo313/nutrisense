%dw 2.0

import * from dwl::template::textTemplate

fun provideRecommendationText(recommendation) = do {
  var dimension = recommendation.dimension
  var drivingMetric = recommendation.driving_metric
  var detail = recommendation.detail
  ---
  if (dimension == "calories")
    buildCaloriesText(detail)
  else if (dimension == "macros")
    if (drivingMetric == "protein_g") buildProteinText(detail)
    else if (drivingMetric == "carbohydrate_g") buildCarbohydrateText(detail)
    else if (drivingMetric == "total_fat_g") buildFatText(detail)
    else if (drivingMetric == "saturated_fat_g") buildSaturatedFatText(detail)
    else if (drivingMetric == "fiber_g") buildFiberText(detail)
    else if (drivingMetric == "sodium_mg") buildSodiumText(detail)
    else "No text available for this recommendation."
  else if (dimension == "sleep")
    buildSleepText(detail, drivingMetric)
  else if (dimension == "recovery")
    buildRecoveryText(detail, drivingMetric)
  else
    "No text available for this recommendation."
}