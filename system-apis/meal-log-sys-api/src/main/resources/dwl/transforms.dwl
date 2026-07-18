%dw 2.0
fun transformMeal(payload)= {
	"mealId": payload[0].meal_id,
	"userId": payload[0].user_id,
	"mealType": payload[0].meal_type,
	"consumedAt": payload[0].consumed_at,
	"loggedAt": payload[0].meal_logged_at,
	"items": payload map ((item) -> {
		"itemId": item.item_id,
		"foodId": item.food_id,
		"portionId": item.portion_id,
		"portionAmount": item.portion_amount,
		"quantityG": item.quantity_g,
		"loggedAt": item.item_logged_at
	})
}
