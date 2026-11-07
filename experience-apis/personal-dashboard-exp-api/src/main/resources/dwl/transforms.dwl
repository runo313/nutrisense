%dw 2.0
fun transformMeal(payload)= {
	"mealId": payload.mealId,
	"mealType": payload.mealType,
	"consumedAt": payload.consumedAt,
	"loggedAt": payload.loggedAt,
	"items": payload.items
}


fun transformProfile(payload)={
	name: payload.name,
	email: payload.email,
	dateOfBirth: payload.dateOfBirth,
	biologicalSex: payload.biologicalSex,
	heightCm: payload.heightCm,
	weightKg: payload.weightKg,
	activityBaseline: payload.activityBaseline,
	dailyCaloricTarget: payload.dailyCaloricTarget,
	createdAt: payload.createdAt,
	updatedAt: payload.updatedAt,
	goals: payload.goals filter ((goal) -> goal.isActive) map ((item) -> item - "userId" ),
	conditions: payload.conditions filter ((condition) -> condition.isActive) map ((item) -> item - "userId" ) ,
	constraints: payload.constraints filter ((constraint) -> constraint.isActive) map ((item) -> item - "userId" ),
	preferences: payload.preferences map ((item) -> item - "userId" )
}