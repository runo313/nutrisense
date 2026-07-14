%dw 2.0
fun transformPayload(data)=
data map(item) -> {
    foodId: item.fdc_id,
    name: item.food_name,
    category: item.category,
    servingSizeG: item.serving_size_g,
    source: "USDA Foundation Foods",
    nutrients: {
        energyKcal: item.energy_kcal,
        proteinG: item.protein_g,
        carbohydratesG: item.carbohydrates_g,
        dietaryFiberG: item.dietary_fiber_g,
        sugarsG: item.sugars_g,
        totalFatG: item.total_fat_g,
        saturatedFatG: item.saturated_fat_g,
        sodiumMg: item.sodium_mg,
        potassiumMg: item.potassium_mg,
        calciumMg: item.calcium_mg,
        ironMg: item.iron_mg,
        vitaminCMg: item.vitamin_c_mg,
        vitaminDMcg: item.vitamin_d_mcg,
        vitaminB12Mcg: item.vitamin_b12_mcg,
        magnesiumMg: item.magnesium_mg,
        zincMg: item.zinc_mg
    } 
    ++ if (item.id != null) {servingSizes:{
        portionId: item.id,
        amount: item.amount,
        unit: item.measure_unit_description,
        gramWeight: item.gram_weight
    }} else {}
}
