%dw 2.0
var activityMultipliers = {
	sedentary: 1.2,
	lightly_active: 1.375,
	moderately_active: 1.55,
	very_active: 1.725,
	athlete: 1.9
}
fun computeCaloricTarget(weightKg, heightCm, dateOfBirth, biologicalSex, activityBaseline)=
	do {
	var today = now() as Date
	var dob = dateOfBirth as Date
	var rawAge = today.year - dob.year
	var passedBirthday = (today.month > dob.month) or (today.month == dob.month and today.day >= dob.day)
	var age = if ( passedBirthday ) rawAge else rawAge - 1
	var bmr = 
		if ( biologicalSex == "male" ) (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5
		else if ( biologicalSex == "female" ) (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161
		else null
	var multiplier = activityMultipliers[activityBaseline as String]
	---
	if (bmr != null and multiplier != null)
      (bmr * multiplier) as Number {format: "0"}  as Number
    else null
}
