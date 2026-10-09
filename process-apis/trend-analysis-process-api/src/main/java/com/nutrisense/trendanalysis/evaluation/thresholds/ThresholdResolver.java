package com.nutrisense.trendanalysis.evaluation.thresholds;

import com.nutrisense.trendanalysis.evaluation.model.BaseThresholdRow;
import com.nutrisense.trendanalysis.evaluation.model.SodiumTarget;
import com.nutrisense.trendanalysis.evaluation.model.ThresholdRow;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves which threshold applies to a specific user for a given metric,
 * replicating what the original DataWeave resolution chain did: find candidate
 * rows, narrow by demographic bucket, check whether the row is surfaced for
 * this user's conditions/constraints, then apply any dietary multiplier and
 * clamp against the metric's UL if one exists.
 */
public class ThresholdResolver {
	private ThresholdRepository repository;
	private static final Logger logger = LoggerFactory.getLogger(ThresholdResolver.class);

	public ThresholdResolver(ThresholdRepository repository) {
		this.repository = repository;
	}

	/**
	 * Step 1 of resolve(): gathers every candidate row for a metric before any
	 * narrowing happens. resolve() takes this full list and filters it down by
	 * demographic bucket and UL exclusion in the following steps.
	 *
	 * @param metricKey e.g. "iron_mg", "sodium_mg"
	 * @return every static threshold row sharing this metric key, unfiltered
	 * @throws IOException if the underlying static thresholds have not yet been
	 *                     loaded and cannot be read
	 */
	public List<BaseThresholdRow> rowsForMetric(String metricKey) throws IOException {
		return this.repository.getAllStaticRows().stream().filter(row -> metricKey.equals(row.metricKey))
				.collect(Collectors.toList());
	}

	/**
	 * Step 2 of resolve(): used as the filter predicate that narrows
	 * rowsForMetric's full candidate list down to the one row matching this user's
	 * bucket. Handles three cases beyond a plain string match: "all_19_plus"
	 * matches every bucket, "female_19_plus" matches both female buckets, and
	 * "calcium_standard" matches male_19_plus and female_19_50 but not
	 * female_51_plus (calcium's RDA changes there).
	 *
	 * @param rowDemographicKey the row's own demographic_key field
	 * @param resolvedBucket    the user's bucket, from
	 *                          DemographicResolver.resolveBucket
	 * @return true if the row applies to this user's bucket
	 */
	public boolean matchesBucket(String rowDemographicKey, String resolvedBucket) {
		if (rowDemographicKey.equals("all_19_plus") || rowDemographicKey.equals(resolvedBucket)) {
			return true;
		}
		if (rowDemographicKey.equals("female_19_plus")
				&& (resolvedBucket.equals("female_19_50") || resolvedBucket.equals("female_51_plus"))) {
			return true;
		}
		if (rowDemographicKey.equals("calcium_standard")
				&& (resolvedBucket.equals("male_19_plus") || resolvedBucket.equals("female_19_50"))) {
			return true;
		}
		return false;
	}

	/**
	 * Checks whether a row should be shown to this user at all, independent of
	 * demographic match. A row with no surfacing rule (or an empty one) always
	 * surfaces. Otherwise it surfaces if the user has any condition or constraint
	 * listed in the row's surfacing_rule.
	 *
	 * Answers yes/no only — use findTriggeringContext to learn which specific
	 * condition or constraint caused a true result.
	 *
	 * @param row               the candidate row
	 * @param activeConditions  the user's active medical conditions
	 * @param activeConstraints the user's active dietary constraints
	 * @return true if this row should be surfaced for this user
	 */

	public boolean isSurfaced(ThresholdRow row, List<String> activeConditions, List<String> activeConstraints) {
		if (row.surfacingRule == null)
			return true;
		List<String> conditions = row.surfacingRule.conditions != null ? row.surfacingRule.conditions
				: Collections.emptyList();
		List<String> constraints = row.surfacingRule.constraints != null ? row.surfacingRule.constraints
				: Collections.emptyList();
		if (conditions.isEmpty() && constraints.isEmpty()) {
			return true;
		}

		boolean conditionMatch = conditions.stream().anyMatch(activeConditions::contains);
		boolean constraintMatch = constraints.stream().anyMatch(activeConstraints::contains);
		return conditionMatch || constraintMatch;
	}

	/**
	 * Step 6, the final step of resolve(): takes the row and whatever
	 * findTriggeringContext returned, and produces the actual number resolve()
	 * hands back to its caller. Applies a dietary multiplier if the triggering
	 * context has one defined on this row (e.g. iron's 1.8x for vegetarian/vegan,
	 * reflecting lower absorption of non-heme iron), then clamps the result so it
	 * can never exceed 80% of the metric's UL, if one exists — this guard exists
	 * because nothing else prevents a multiplier from pushing a resolved lower
	 * bound past an upper safety limit.
	 *
	 * @param row               the row isSurfaced confirmed, with
	 *                          findTriggeringContext's result for triggeringContext
	 * @param triggeringContext the specific condition/constraint that surfaced this
	 *                          row, or null if none applies
	 * @return the final resolved value: base value, multiplied if applicable,
	 *         clamped to 80% of UL if that would otherwise be exceeded
	 * @throws IOException if a UL lookup requires re-reading static thresholds
	 */
	public Double applyMultiplierAndClamp(ThresholdRow row, String triggeringContext) throws IOException {
		Double resolvedValue = row.baseValue;
		if (row.dietaryMultiplier != null) {
			Double multiplier = row.dietaryMultiplier.getOrDefault(triggeringContext, null);
			if (multiplier != null) {
				resolvedValue = row.baseValue * multiplier;
			}
		}
		List<BaseThresholdRow> ulRows = rowsForMetric(row.metricKey).stream().filter(r -> "UL".equals(r.valueType))
				.collect(Collectors.toList());

		if (!ulRows.isEmpty()) {
			ThresholdRow ulRow = (ThresholdRow) ulRows.get(0);
			Double ulCeiling = ulRow.baseValue * 0.80;
			if (resolvedValue > ulCeiling) {
				return ulCeiling;
			}
		}

		return resolvedValue;
	}

	/**
	 * Step 5 of resolve(): called only after isSurfaced has already confirmed a
	 * match, to identify which specific condition or constraint caused it. This is
	 * needed because dietary_multiplier is keyed by that specific value (e.g.
	 * "vegetarian"), not by whether surfacing happened at all — resolve() passes
	 * this method's result straight into applyMultiplierAndClamp as the lookup key.
	 * Conditions are checked before constraints, matching the same precedence
	 * isSurfaced applies.
	 *
	 * Returns null both when nothing matched and when the row has no surfacing rule
	 * — by this point in resolve() that only means there is no multiplier to apply,
	 * not that the row is unsurfaced (isSurfaced already confirmed that).
	 *
	 * @param row               the row isSurfaced already confirmed as a match
	 * @param activeConditions  the user's active medical conditions
	 * @param activeConstraints the user's active dietary constraints
	 * @return the single matched condition or constraint, or null
	 */
	public String findTriggeringContext(ThresholdRow row, List<String> activeConditions,
			List<String> activeConstraints) {
		if (row.surfacingRule == null)
			return null;
		List<String> conditions = row.surfacingRule.conditions != null ? row.surfacingRule.conditions
				: Collections.emptyList();
		List<String> constraints = row.surfacingRule.constraints != null ? row.surfacingRule.constraints
				: Collections.emptyList();
		if (conditions.isEmpty() && constraints.isEmpty()) {
			return null;
		}
		for (String condition : conditions) {
			if (activeConditions.contains(condition)) {
				return condition;
			}
		}

		for (String constraint : constraints) {
			if (activeConstraints.contains(constraint)) {
				return constraint;
			}
		}

		return null;
	}

	/**
	 * Full threshold resolution for one metric against one user's context. Runs the
	 * complete chain in order:
	 *
	 * 1. rowsForMetric — gather every row sharing this metric key 2. matchesBucket
	 * — filter to the row matching this user's bucket 3. (inline filter) — exclude
	 * UL rows, which only ever support another row's clamp and never stand alone 4.
	 * isSurfaced — confirm the surviving candidate should be shown to this user at
	 * all 5. findTriggeringContext — identify which condition/constraint caused
	 * that surfacing, if any 6. applyMultiplierAndClamp — apply that trigger's
	 * multiplier and enforce the UL safety clamp
	 *
	 * Returns null if step 2/3 leaves no candidate row at all, or if step 4
	 * determines the surviving candidate isn't surfaced for this user — both are
	 * legitimate "this metric doesn't apply here" outcomes, not errors.
	 *
	 * @param metricKey         e.g. "iron_mg"
	 * @param resolvedBucket    the user's bucket, from
	 *                          DemographicResolver.resolveBucket
	 * @param activeConditions  the user's active medical conditions
	 * @param activeConstraints the user's active dietary constraints
	 * @return the resolved threshold value for this user, or null if no row matches
	 *         this bucket or the matching row is not surfaced
	 * @throws IOException if the underlying static thresholds cannot be read
	 */

	public Double resolve(String metricKey, String resolvedBucket, List<String> activeConditions,
			List<String> activeConstraints) throws IOException {

		List<BaseThresholdRow> rows = rowsForMetric(metricKey).stream()
				.filter(row -> matchesBucket(row.demographicKey, resolvedBucket))
				.filter(row -> !"UL".equals(row.valueType)).collect(Collectors.toList());
		if (rows.isEmpty()) {
			logger.debug("No threshold row found for metric={} bucket={}", metricKey, resolvedBucket);
			return null;
		}

		ThresholdRow candidate = (ThresholdRow) rows.get(0);

		boolean surfaced = isSurfaced(candidate, activeConditions, activeConstraints);
		if (surfaced) {
			String triggerContext = findTriggeringContext(candidate, activeConditions, activeConstraints);
			Double resolvedValue = applyMultiplierAndClamp(candidate, triggerContext);
			logger.info("Resolved threshold: metric={} bucket={} trigger={} value={}", metricKey, resolvedBucket,
					triggerContext, resolvedValue);
			return resolvedValue;
		}
		logger.debug("Metric={} not surfaced for bucket={} conditions={} constraints={}", metricKey, resolvedBucket,
				activeConditions, activeConstraints);
		return null;

	}

	/**
	 * Resolves the daily sodium cap and hard bound in mg. Sodium has two rows that
	 * both apply to all adults: a general limit and a tighter hypertension
	 * override. The override is used when the user has hypertension, otherwise the
	 * general row. This does not go through resolve(), because resolve() takes the
	 * first surviving row and then checks surfacing, so a user without hypertension
	 * could be dropped before the general row is reached.
	 *
	 * Process: 1. Keep only ThresholdRow instances for sodium_mg. 2. Pick the first
	 * override row (conditionOverrideOf set) that isSurfaced confirms for this
	 * user. Sodium has no dietary constraints, so an empty constraint list is
	 * passed. 3. If none, pick the row with no override, the general row. 4. Take
	 * the cap and hard bound as stored. They are already absolute mg, so there is
	 * no caloric target or unit conversion. 5. For an override row,
	 * findTriggeringContext supplies the condition name.
	 *
	 * @param activeConditions the user's active conditions; null is treated as none
	 * @return a SodiumTarget (thresholdId, capMg, hardBoundMg, condition), where
	 *         condition is null for the general row. Null if no sodium rows load,
	 *         or the chosen row is missing its cap or hard bound.
	 * @throws IOException if static-thresholds.json cannot be read
	 */

	public SodiumTarget resolveSodiumTarget(List<String> activeConditions) throws IOException {

		List<String> conditions = activeConditions != null ? activeConditions : Collections.emptyList();
		List<String> constraints = Collections.emptyList();

		List<ThresholdRow> rows = rowsForMetric("sodium_mg").stream().filter(row -> row instanceof ThresholdRow)
				.map(row -> (ThresholdRow) row).collect(Collectors.toList());

		if (rows.isEmpty()) {
			logger.debug("No Sodium row found");
			return null;
		}

		ThresholdRow selected = rows.stream()
				.filter(r -> r.conditionOverrideOf != null && isSurfaced(r, conditions, constraints)).findFirst()
				.orElseGet(() -> rows.stream().filter(r -> r.conditionOverrideOf == null).findFirst().orElse(null));

		if (selected == null || selected.baseValue == null || selected.hardBound == null) {
			logger.warn("Sodium row missing or incomplete");
			return null;
		}

		SodiumTarget result = new SodiumTarget();
		result.thresholdId = selected.thresholdId;
		result.capMg = selected.baseValue;
		result.hardBoundMg = selected.hardBound;
		result.condition = selected.conditionOverrideOf != null
				? findTriggeringContext(selected, conditions, constraints)
				: null;

		logger.info("Resolved sodium target: row={} capMg={} hardBoundMg={} condition={}", result.thresholdId,
				result.capMg, result.hardBoundMg, result.condition);
		return result;

	}

	/**
	 * Returns the carbohydrate 130g RDA floor row from static-thresholds.json. The
	 * floor is a fixed brain-glucose minimum with no demographic or surfacing
	 * rules, so no user context is needed.
	 *
	 * @return the floor row, or null if it is not found
	 * @throws IOException if static-thresholds.json cannot be read
	 */
	public ThresholdRow resolveCarbFloorRow() throws IOException {
		return rowsForMetric("carbohydrate_g").stream()
				.filter(r -> r instanceof ThresholdRow && "RDA".equals(r.valueType)).map(r -> (ThresholdRow) r)
				.findFirst().orElse(null);
	}

}
