%dw 2.0
fun transformPayload(data)=
data groupBy ((item) -> item.fdc_id) pluck ((value, key) ->{
	foodId: value[0].fdc_id,
	name: value[0].food_name,
	category: value[0].category,
	servingSizeG: value[0].serving_size_g,
	source: "USDA Foundation Foods",
	nutrients: {
		energyKcal: value[0].energy_kcal,
		proteinG: value[0].protein_g,
		carbohydratesG: value[0].carbohydrates_g,
		dietaryFiberG: value[0].dietary_fiber_g,
		sugarsG: value[0].sugars_g,
		totalFatG: value[0].total_fat_g,
		saturatedFatG: value[0].saturated_fat_g,
		sodiumMg: value[0].sodium_mg,
		potassiumMg: value[0].potassium_mg,
		calciumMg: value[0].calcium_mg,
		ironMg: value[0].iron_mg,
		vitaminCMg: value[0].vitamin_c_mg,
		vitaminDMcg: value[0].vitamin_d_mcg,
		vitaminB12Mcg: value[0].vitamin_b12_mcg,
		magnesiumMg: value[0].magnesium_mg,
		zincMg: value[0].zinc_mg
	},
	servingSizes: value filter ((item) -> item.id != null ) map ((value, index) -> {
		portionId: value.id,
		amount: value.amount,
		unit: value.measure_unit_description,
		gramWeight: value.gram_weight
	})
} )
