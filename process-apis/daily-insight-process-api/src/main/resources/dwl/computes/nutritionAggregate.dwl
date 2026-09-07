%dw 2.0
output application/java

var meal = vars.mealStatus

// findFood: given a foodId, look up its full nutrition record.
// meal.foods is an array of arrays (each food is wrapped in its own
// single-element array), so we flatten it first to get a flat list
// of food objects before filtering.
fun findFood(foodId) = flatten(meal.foods) filter ((food) -> food.foodId == foodId)

// scaleItem: for one logged meal entry ({foodId, quantityG}),
// look up its reference food data and scale every nutrient value
// from the "per servingSizeG" amount to the "per quantityG actually
// eaten" amount.

fun scaleItem(item) = do {
    // The matching food record (null if not found)
    var food = findFood(item.foodId)[0]
    var factor = if (food != null)
        (item.quantityG as Number) / (food.servingSizeG as Number)
    else 0
    ---
    {
        foodId: item.foodId,
        name: food.name default null,
        quantityG: item.quantityG,
        servingSizeG: food.servingSizeG default null,
        // Multiply every numeric nutrient by the scale factor.
        // null for missing nutrients pass through unchanged
        nutrients: if (food != null)
            food.nutrients mapObject ((value, key) ->
                { (key): if (value is Number) value * factor else value }
            )
        else {}
        }
    }
// scaleAllItems: apply scaleItem to every logged meal entry,
// dropping any entries where no matching food was found.
fun scaleAllItems() = (meal.meals map ((item) -> scaleItem(item)))
filter ($ != null)

// sumNutrients: add up each nutrient across every scaled meal to get daily totals. 
fun sumNutrients() = (scaleAllItems() map ((item) -> item.nutrients))
    reduce ((nutrients, acc = {}) ->
            nutrients mapObject ((value, key) ->
                { (key): (value default 0) + (acc[key] default 0) }
            )
        )

---
{
    scaledItems: scaleAllItems(),
    dailyTotals: sumNutrients()
}