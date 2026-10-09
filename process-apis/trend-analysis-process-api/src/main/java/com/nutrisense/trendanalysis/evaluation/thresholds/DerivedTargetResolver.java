package com.nutrisense.trendanalysis.evaluation.thresholds;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nutrisense.trendanalysis.evaluation.model.AmdrRange;
import com.nutrisense.trendanalysis.evaluation.model.AmdrTarget;
import com.nutrisense.trendanalysis.evaluation.model.Fiber;
import com.nutrisense.trendanalysis.evaluation.model.FiberTarget;
import com.nutrisense.trendanalysis.evaluation.model.ProteinCoefficient;
import com.nutrisense.trendanalysis.evaluation.model.ProteinTarget;
import com.nutrisense.trendanalysis.evaluation.model.SatFatTarget;
import com.nutrisense.trendanalysis.evaluation.model.SaturatedFatRow;

public class DerivedTargetResolver {

	private ThresholdRepository repository;
	private static final Logger logger = LoggerFactory.getLogger(ThresholdResolver.class);

	public DerivedTargetResolver(ThresholdRepository repository) {
		this.repository = repository;

	}

	public ProteinTarget resolveProteinTarget(Double weightKg, String goalType, String direction,
			String activityBaseline) throws IOException {

		if (weightKg == null) {
			logger.debug("Protein target not resolved: weightKg is null");
			return null;
		}

		List<ProteinCoefficient> candidates = repository.loadDerivedTargets().proteinCoefficients.stream()
				.filter(row -> matches(row, goalType, direction, activityBaseline))
				.sorted(Comparator.comparing(row -> row.precedence)).collect(Collectors.toList());

		if (candidates.isEmpty()) {
			logger.warn("No protein coefficient row matched: goalType={} direction={} activityBaseline={}", goalType,
					direction, activityBaseline);
			return null;
		}

		ProteinCoefficient selected = candidates.get(0);

		ProteinTarget result = new ProteinTarget();
		result.thresholdId = selected.thresholdId;
		result.targetG = selected.rangeMin * weightKg;
		result.hardBoundG = selected.hardBound * weightKg;

		logger.info("Resolved protein target: row={} weightKg={} targetG={} hardBoundG={}", result.thresholdId,
				weightKg, result.targetG, result.hardBoundG);

		return result;

	}

	public FiberTarget resolveFiberTarget(Double dailyCaloricTarget) throws IOException {

		if (dailyCaloricTarget == null || dailyCaloricTarget <= 0) {
			logger.debug("Fiber target not resolved: caloric target is= {}", dailyCaloricTarget);
			return null;
		}

		Fiber fiberRows = repository.loadDerivedTargets().fiber;

		if (fiberRows == null || fiberRows.gramsPer1000Kcal == null || fiberRows.hardPctOfBase == null) {
			logger.warn("Fiber row missing or incomplete in derived targets");
			return null;
		}

		FiberTarget result = new FiberTarget();
		result.thresholdId = fiberRows.thresholdId;
		result.targetG = dailyCaloricTarget / 1000 * fiberRows.gramsPer1000Kcal;
		result.hardBoundG = result.targetG * fiberRows.hardPctOfBase;

		logger.info("Resolved fiber target: row={} targetG={} hardBoundG={}", result.thresholdId, result.targetG,
				result.hardBoundG);
		return result;
	}

	/**
	 * Resolves the daily saturated fat cap and hard bound in grams. Selects the
	 * high-cholesterol row if the user has that condition, otherwise the general
	 * row. Both bounds are a percentage of the caloric target converted to grams at
	 * 9 kcal per gram of fat.
	 *
	 * @param dailyCaloricTarget the profile's target in kcal; null if it could not
	 *                           be computed
	 * @param activeConditions   the user's active conditions; null is treated as no
	 *                           conditions.
	 * 
	 * @return a SatFatTarget (thresholdId, capG, hardBoundG, condition), where
	 *         condition is null for the general row. Null if the caloric target is
	 *         missing or not above zero, or if no usable row is found.
	 * @throws IOException if derived-targets.json cannot be read
	 */

	public SatFatTarget resolveSaturatedFatTarget(Double dailyCaloricTarget, List<String> activeConditions)
			throws IOException {

		final double KCAL_PER_GRAM_FAT = 9.0; // not in the JSON; Atwater factor for fat

		if (dailyCaloricTarget == null || dailyCaloricTarget <= 0) {
			logger.debug("Saturated Fat target not resolved: caloric target is= {}", dailyCaloricTarget);
			return null;
		}

		List<SaturatedFatRow> candidates = repository.loadDerivedTargets().saturatedFat;

		if (candidates == null || candidates.isEmpty()) {
			logger.warn("Saturated fat rows missing in derived targets");
			return null;
		}

		SaturatedFatRow row = candidates.stream()
				.filter(r -> activeConditions != null && activeConditions.contains(r.condition)).findFirst()
				.orElseGet(() -> candidates.stream().filter(r -> r.condition == null).findFirst().orElse(null));

		if (row == null || row.pctCap == null || row.hardPct == null) {
			logger.warn("Saturated fat row missing or incomplete");
			return null;
		}

		SatFatTarget result = new SatFatTarget();
		result.thresholdId = row.thresholdId;
		result.capG = (dailyCaloricTarget * (row.pctCap / 100) / KCAL_PER_GRAM_FAT);
		result.hardBoundG = dailyCaloricTarget * (row.hardPct / 100) / KCAL_PER_GRAM_FAT;
		result.condition = row.condition;

		logger.info("Resolved Saturated Fat target: row={} capG={} hardBoundG={} condition= {}", result.thresholdId,
				result.capG, result.hardBoundG, result.condition);
		return result;
	}
	
	/**
	 * Resolves the daily gram range and hard bounds for one AMDR macro. Each
	 * bound is a percentage of the caloric target converted to grams with the
	 * row's own kcalPerGram (9 for fat, 4 for carbohydrates), so no constant is
	 * hardcoded here.
	 *
	 * @param metricKey "total_fat_g" or "carbohydrate_g"
	 * @param dailyCaloricTarget the profile's target in kcal; null if it could not be computed
	 * @return an AmdrTarget (thresholdId, rangeMinG, rangeMaxG, hardBelowG,
	 *         hardAboveG). Null if the caloric target is missing or not above
	 *         zero, or if no row matches the key or the row is incomplete.
	 * @throws IOException if derived-targets.json cannot be read
	 */
	public AmdrTarget resolveAmdrTarget(String metricKey, Double dailyCaloricTarget) throws IOException {

		if (dailyCaloricTarget == null || dailyCaloricTarget <= 0) {
			logger.debug("AMDR target not resolved: caloric target is= {} metric key={}", dailyCaloricTarget, metricKey);
			return null;
		}

		List<AmdrRange> candidates = repository.loadDerivedTargets().amdrRanges;

		if (candidates == null || candidates.isEmpty()) {
			logger.warn("Amdr rows missing in derived targets");
			return null;
		}

		AmdrRange row = candidates.stream().filter(r -> r.metricKey.equals(metricKey)).findFirst().orElse(null);

		if (row == null || row.kcalPerGram == null || row.hardPctBelow == null || row.hardPctAbove == null
				|| row.pctMax == null || row.pctMin == null) {
			logger.warn("Amdr range row missing or incomplete");
			return null;
		}

		AmdrTarget result = new AmdrTarget();
		result.thresholdId = row.thresholdId;
		result.rangeMinG = (dailyCaloricTarget * (row.pctMin / 100) / row.kcalPerGram);
		result.rangeMaxG = (dailyCaloricTarget * (row.pctMax / 100) / row.kcalPerGram);
		result.hardBelowG = (dailyCaloricTarget * (row.hardPctBelow / 100) / row.kcalPerGram);
		result.hardAboveG = (dailyCaloricTarget * (row.hardPctAbove / 100) / row.kcalPerGram);

		logger.info("Resolved Amdr target: metric key={} row={} rangeMinG={} rangeMaxG={} hardBelowG= {} hardAboveG= {}",
				metricKey, result.thresholdId, result.rangeMinG, result.rangeMaxG, result.hardBelowG, result.hardAboveG);
		return result;

	}

	private static boolean matches(ProteinCoefficient row, String goalType, String direction, String activityBaseline) {
		ProteinCoefficient.Match match = row.match;
		if (match == null)
			return true;
		if (match.goalType != null && !match.goalType.equals(goalType))
			return false;
		if (match.goalDirection != null && !match.goalDirection.equals(direction))
			return false;
		if (match.activityBaselineIn != null && !match.activityBaselineIn.contains(activityBaseline))
			return false;
		return true;
	}
}
