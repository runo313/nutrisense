# Reference Thresholds

The Daily Insight and Trend Analysis Process APIs evaluate nutrition, sleep, activity and recovery against a common set of reference values and evaluation rules.

These definitions are maintained in DataWeave/JSON modules.

The modules distinguish published reference values from project-defined warning bands. A recommended intake or clinical reference does not automatically establish how far from that value should count as a warning.

## Threshold modules

| Module | Responsibility |
|---|---|
| `static-thresholds.dwl` | Fixed references for micronutrients, sodium, carbohydrate floor, sleep and weekly activity |
| `derived-targets.dwl` | User-specific protein targets, macronutrient ranges, saturated fat, fiber and calorie-related targets |
| `baseline-deviations.dwl` | Resting heart rate, HRV and active energy comparisons against personal baselines |
| `threshold-metadata.dwl` | Warning-band definitions, exclusions, guards and text-generation constraints |

The User Profile System API calculates the user's daily calorie target using the Mifflin-St Jeor equation. Process APIs consume that target rather than independently recalculating it.

## Reference sources

| Source | Used for |
|---|---|
| NIH Office of Dietary Supplements | Iron, zinc, calcium, vitamin B12 and potassium |
| Institute of Medicine, Dietary Reference Intakes (2005) | Carbohydrate and fat AMDR ranges, carbohydrate minimum and fiber |
| Dietary Guidelines for Americans 2025–2030 | General protein target, saturated fat guidance, sodium guidance and added sugars |
| National Academies (NASEM), 2019 | Sodium CDRR and potassium reference values |
| American Heart Association | Stricter sodium guidance and cardiovascular dietary considerations |
| International Society of Sports Nutrition, 2017 | Protein targets for athletic performance and physically active weight management |
| American Academy of Sleep Medicine / Sleep Research Society | Minimum recommended adult sleep duration |
| CDC Physical Activity Guidelines | Weekly moderate and vigorous activity targets, evaluated by Trend Analysis |
| Sleep Foundation | Approximate deep and REM sleep proportions |
| Mifflin-St Jeor equation | Estimated resting energy expenditure used by the User Profile System API |

These sources serve different purposes. Dietary Reference Intakes describe nutrient requirements or limits; physical activity guidance describes weekly targets; personal baselines describe changes relative to an individual's historical measurements.

They should not be treated as interchangeable reference types.

## Published values versus heuristic cutoffs

Published guidance supplies reference values wherever an appropriate reference exists. The classification bands around those references are separate decisions made for NutriSense.

Examples of project-defined heuristics include:

- Calorie deviation bands around the user's calculated target.
- Soft-warning and hard-flag cutoffs around nutrient targets.
- Resting heart rate and HRV deviations from personal baselines.
- Active energy deviations used for combined insights.
- Severity thresholds applied to estimated sleep-stage percentages.

These values are marked `heuristic` in the implementation.

A heuristic flag indicates that a recorded value crossed a project-defined boundary. It is not a medical diagnosis or evidence of a nutrient deficiency.

Daily Insight also deliberately avoids evaluating some metrics against daily targets when the underlying guidance applies over a longer period. For example, micronutrient references may be displayed without assigning a daily status, while weekly activity guidance belongs to Trend Analysis.

## Source-specific considerations

**Dietary Guidelines for Americans 2025–2030.** The current guidelines recommend 1.2–1.6 g of protein per kg of body weight per day and generally limiting saturated fat to 10% of daily calories. They also address added sugars at the meal level. These references should not be confused with older guideline editions or with the Institute of Medicine's AMDR definitions.

**American Heart Association.** The AHA recommends moving toward 1,500 mg of sodium daily for most adults, especially those with hypertension. Its current saturated fat recommendation is less than 6% of daily calories. Any different percentage implemented by NutriSense must be identified as a project-specific choice.

**Sleep stages.** Sleep Foundation reference proportions are approximate and are not formal clinical diagnostic thresholds. Consumer wearable devices also estimate sleep stages imperfectly, so these results are interpreted conservatively.

**Personal baselines.** Recovery and active energy use the user's historical measurements rather than population-level values. The deviation percentages themselves are project-defined.

## Deliberate exclusions

**No fixed diabetes-specific carbohydrate target.** The American Diabetes Association recommends individualized carbohydrate planning based on eating patterns, preferences and metabolic goals. NutriSense therefore does not apply a universal stricter carbohydrate threshold solely because a user has diabetes or prediabetes.

**No unsupported daily micronutrient judgments.** Falling below a reference intake on one day does not establish a deficiency. Micronutrients are displayed for context in Daily Insight, with longer-term evaluation reserved for Trend Analysis.

**No automatic pregnancy or breastfeeding adjustments.** The current profile model lacks those fields. The threshold definitions are therefore not designed for those populations.

## Maintaining the thresholds

Changes to published values, evaluation rules or warning bands should follow these rules:

1. **Keep units consistent.** Milligrams and micrograms must never be compared without conversion. The same applies to calories, kilocalories, grams and percentages.
2. **Preserve the original source.** Metrics listed under `nonDgaSourcedMetrics` retain their Institute of Medicine, NASEM or NIH attribution rather than being reassigned to newer dietary guidelines that do not publish the same detailed reference tables.
3. **Separate source updates from heuristic changes.** Changing a published reference and changing NutriSense's warning band are different decisions.
4. **Update both Process APIs consistently.** Shared threshold definitions should not silently diverge between daily and historical evaluations.
5. **Bump the threshold version.** Any change affecting evaluation behavior must increment the module version and preserve the version returned by insight responses.

## References

1. NIH Office of Dietary Supplements. [Vitamin and Mineral Supplement Fact Sheets](https://ods.od.nih.gov/factsheets/list-VitaminsMinerals/). Health Professional references for iron, zinc, calcium, vitamin B12 and potassium.
2. Institute of Medicine. *Dietary Reference Intakes for Energy, Carbohydrate, Fiber, Fat, Fatty Acids, Cholesterol, Protein, and Amino Acids.* National Academies Press; 2005. [Publication](https://www.nationalacademies.org/publications/10490).
3. U.S. Department of Health and Human Services and U.S. Department of Agriculture. *Dietary Guidelines for Americans, 2025–2030.* January 2026. [Official publication](https://www.realfood.gov/documents/DGA.pdf).
4. National Academies of Sciences, Engineering, and Medicine. *Dietary Reference Intakes for Sodium and Potassium.* National Academies Press; 2019. [Publication](https://www.ncbi.nlm.nih.gov/books/NBK538102/).
5. Jäger R, Kerksick CM, Campbell BI, et al. International Society of Sports Nutrition Position Stand: protein and exercise. *Journal of the International Society of Sports Nutrition.* 2017;14:20. [DOI](https://doi.org/10.1186/s12970-017-0177-8).
6. Watson NF, Badr MS, Belenky G, et al. Recommended Amount of Sleep for a Healthy Adult: A Joint Consensus Statement of the American Academy of Sleep Medicine and Sleep Research Society. *Sleep.* 2015;38(6):843–844. [Publication](https://doi.org/10.5665/sleep.4716).
7. Centers for Disease Control and Prevention. [Adult Activity: An Overview](https://www.cdc.gov/physical-activity-basics/guidelines/adults.html).
8. Mifflin MD, St Jeor ST, Hill LA, et al. A new predictive equation for resting energy expenditure in healthy individuals. *American Journal of Clinical Nutrition.* 1990;51(2):241–247. [PubMed](https://pubmed.ncbi.nlm.nih.gov/2305711/).
9. American Heart Association. [How Much Sodium Should I Eat Per Day?](https://www.heart.org/en/healthy-living/healthy-eating/eat-smart/sodium/how-much-sodium-should-i-eat-per-day) and [Saturated Fats](https://www.heart.org/en/healthy-living/healthy-eating/eat-smart/fats/saturated-fats).
10. Sleep Foundation. [How Much Deep Sleep Do You Need?](https://www.sleepfoundation.org/stages-of-sleep/deep-sleep).
11. American Diabetes Association. *Standards of Care in Diabetes—2026*, Section 5: Facilitating Positive Health Behaviors and Well-being to Improve Health Outcomes. [DOI](https://doi.org/10.2337/dc26-S005).

## Related documentation

- [Daily Insight Process API](../process-apis/daily-insight-process-api/README.md)
- [Trend Analysis Process API](../process-apis/trend-analysis-process-api/README.md)
- [NutriSense architecture](../README.md)