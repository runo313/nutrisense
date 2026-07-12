%dw 2.0
var dbValues = {
  energyKcal: "energy_kcal",
  proteinG: "protein_g",
  carbohydratesG: "carbohydrates_g",
  dietaryFiberG: "dietary_fiber_g",
  sugarsG: "sugars_g",
  totalFatG: "total_fat_g",
  saturatedFatG: "saturated_fat_g",
  sodiumMg: "sodium_mg",
  potassiumMg: "potassium_mg",
  calciumMg: "calcium_mg",
  ironMg: "iron_mg",
  vitaminCMg: "vitamin_c_mg",
  vitaminDMcg: "vitamin_d_mcg",
  vitaminB12Mcg: "vitamin_b12_mcg",
  magnesiumMg: "magnesium_mg",
  zincMg: "zinc_mg"
}

fun mapToDbValues(fields) =  fields map (field) -> dbValues[field as String]

fun mapToApiFields(dbColumns) = 
  do {
    var reversed = dbValues mapObject ((value, key) -> {(value): key as String})
    ---
    dbColumns map (col) -> reversed[col as String]
  }