%dw 2.0
fun transformMeal(payload)= {
	"mealId": payload.mealId,
	"mealType": payload.mealType,
	"consumedAt": payload.consumedAt,
	"loggedAt": payload.loggedAt,
	"items": payload.items
}