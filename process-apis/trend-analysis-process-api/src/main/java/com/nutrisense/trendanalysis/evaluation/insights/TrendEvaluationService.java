package com.nutrisense.trendanalysis.evaluation.insights;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.nutrisense.trendanalysis.evaluation.evaluators.ActivityEvaluator;
import com.nutrisense.trendanalysis.evaluation.evaluators.CaloriesEvaluator;
import com.nutrisense.trendanalysis.evaluation.evaluators.CoverageGate;
import com.nutrisense.trendanalysis.evaluation.evaluators.DailyNutrientAggregator;
import com.nutrisense.trendanalysis.evaluation.evaluators.MacroEvaluator;
import com.nutrisense.trendanalysis.evaluation.evaluators.MicronutrientEvaluator;
import com.nutrisense.trendanalysis.evaluation.evaluators.PointMetricAggregator;
import com.nutrisense.trendanalysis.evaluation.evaluators.RecoveryEvaluator;
import com.nutrisense.trendanalysis.evaluation.evaluators.SleepAggregator;
import com.nutrisense.trendanalysis.evaluation.evaluators.SleepEvaluator;
import com.nutrisense.trendanalysis.evaluation.evaluators.WorkoutMinutesAggregator;
import com.nutrisense.trendanalysis.evaluation.model.ActivityTrend;
import com.nutrisense.trendanalysis.evaluation.model.CaloriesTrend;
import com.nutrisense.trendanalysis.evaluation.model.CoverageEntry;
import com.nutrisense.trendanalysis.evaluation.model.DerivedTargets;
import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
import com.nutrisense.trendanalysis.evaluation.model.EvaluationType;
import com.nutrisense.trendanalysis.evaluation.model.Food;
import com.nutrisense.trendanalysis.evaluation.model.MacroTrendEntry;
import com.nutrisense.trendanalysis.evaluation.model.MacrosTrend;
import com.nutrisense.trendanalysis.evaluation.model.Meal;
import com.nutrisense.trendanalysis.evaluation.model.PointMetric;
import com.nutrisense.trendanalysis.evaluation.model.RecoveryTrend;
import com.nutrisense.trendanalysis.evaluation.model.SleepSession;
import com.nutrisense.trendanalysis.evaluation.model.SleepTrend;
import com.nutrisense.trendanalysis.evaluation.model.TrendEvaluation;
import com.nutrisense.trendanalysis.evaluation.model.TrendRequest;
import com.nutrisense.trendanalysis.evaluation.model.Workout;
import com.nutrisense.trendanalysis.evaluation.thresholds.DemographicResolver;
import com.nutrisense.trendanalysis.evaluation.thresholds.DerivedTargetResolver;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdRepository;
import com.nutrisense.trendanalysis.evaluation.thresholds.ThresholdResolver;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class TrendEvaluationService {
	private static final Logger logger = LoggerFactory.getLogger(TrendEvaluationService.class);
	private static final MacroEvaluator MACRO_EVALUATOR;
    private static final MicronutrientEvaluator MICRONUTRIENT_EVALUATOR;
    private static final SleepEvaluator SLEEP_EVALUATOR;
    private static final RecoveryEvaluator RECOVERY_EVALUATOR;
    private static final ActivityEvaluator ACTIVITY_EVALUATOR;
    private static final DerivedTargets DERIVED_TARGETS;
    
    
    private static final TypeAdapter<Double> ROUNDED_DOUBLE = new TypeAdapter<Double>() {
        @Override
        public void write(JsonWriter out, Double value) throws IOException {
            if (value.isNaN() || value.isInfinite()) {
                out.nullValue();
            } else {
                out.value(BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue());
            }
        }

        @Override
        public Double read(JsonReader in) throws IOException {
            return in.nextDouble();
        }
    }.nullSafe();

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Double.class, ROUNDED_DOUBLE)
            .registerTypeAdapter(double.class, ROUNDED_DOUBLE)
            .create();
	
	static {
		try {
			ThresholdRepository repository = new ThresholdRepository();
			repository.loadStaticThresholds();
			DERIVED_TARGETS =repository.loadDerivedTargets();
			repository.loadBaselineDeviations();
			
			ThresholdResolver thresholdResolver = new ThresholdResolver(repository);
            DerivedTargetResolver derivedTargetResolver = new DerivedTargetResolver(repository);
            
            MACRO_EVALUATOR = new MacroEvaluator(derivedTargetResolver, thresholdResolver);
            MICRONUTRIENT_EVALUATOR = new MicronutrientEvaluator(thresholdResolver);
            SLEEP_EVALUATOR = new SleepEvaluator(thresholdResolver);
            RECOVERY_EVALUATOR = new RecoveryEvaluator(thresholdResolver);
            ACTIVITY_EVALUATOR = new ActivityEvaluator(thresholdResolver);
            logger.info("TrendEvaluationService initialized: threshold files loaded");
		} catch (IOException e) {
			throw new IllegalStateException("Threshold files could not be loaded: " + e.getMessage(), e);
		}
	}
	
	/**
     * Parses a request document, evaluates it, and serializes the result. This is
     * the method Mule invokes. Nulls are kept in the output, so a dimension that
     * could not be evaluated still shows its fields.
     *
     * @param requestJson the TrendRequest as JSON
     * @return the TrendEvaluation as JSON, with the RAML's snake_case keys
     * @throws IllegalArgumentException if the string is empty, is not valid JSON,
     *         or breaks the request contract
     */
    public static String evaluateJson(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("Request JSON is empty");
        }
        TrendRequest request;
        try {
            request = GSON.fromJson(requestJson, TrendRequest.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Request is not valid JSON: " + e.getMessage(), e);
        }
        return GSON.toJson(evaluate(request));
    }
	
	/**
     * Evaluates one window. Anything outside the supported population gets
     * INSUFFICIENT_DATA on every dimension and no evaluator runs.
     * 1. Validate the window and parse the start date.
     * 2. Replace null lists with empty ones. baselineSummaries stays nullable,
     *    since it is null at window 30.
     * 3. Resolve the demographic bucket. A missing age counts as out of scope.
     * 4. Run calories, macros, micronutrients, sleep, recovery and activity.
     * 5. Assemble the result.
     *
     * @param request the parsed request
     * @return the evaluation, never null, with none of the six dimensions null
     * @throws IllegalArgumentException if the window is not 7, 14 or 30, the
     *         start date is missing or unparseable, or the window or profile is missing
     */
	public static TrendEvaluation evaluate(TrendRequest request) {
		LocalDate windowStart = validate(request);
		int windowDays = request.window.days;
        TrendRequest.Profile profile = request.profile;
        
        List<Meal> meals = orEmpty(request.meals);
        List<Food> foods = orEmpty(request.foods);
        List<SleepSession> sleepSessions = orEmpty(request.sleepSessions);
        List<PointMetric> pointMetrics = orEmpty(request.pointMetrics);
        List<Workout> workouts = orEmpty(request.workouts);
        List<String> conditions = orEmpty(profile.conditions);
        List<String> constraints = orEmpty(profile.constraints);
        
        logger.info("Trend evaluation started: windowDays={} windowStart={} meals={} foods={} sleepSessions={} pointMetrics={} workouts={} baselineSummaries={}",
                windowDays, windowStart, meals.size(), foods.size(), sleepSessions.size(),
                pointMetrics.size(), workouts.size(),
                request.baselineSummaries == null ? "none" : request.baselineSummaries.size());
        
        logger.info("Trend input detail: mealsWithDate={} mealsWithItems={} pointMetricsByType={} earliestSleepStart={}",
                countMealsWithDate(meals), countMealsWithItems(meals), countByMetricType(pointMetrics),
                earliestSleepStart(sleepSessions));
        
        String bucket = bucketOf(profile);
        if ("out_of_scope".equals(bucket)) {
            logger.warn("User out of scope (age={}): returning INSUFFICIENT_DATA on every dimension", profile.age);
            return outOfScope(windowDays);
        }
        
        try {
            TrendEvaluation.DimensionTrends trends = new TrendEvaluation.DimensionTrends();

            trends.calories = CaloriesEvaluator.evaluate(meals, foods, profile.dailyCaloricTarget,
                    windowStart, windowDays, DERIVED_TARGETS);
            trends.macros = MACRO_EVALUATOR.evaluate(meals, foods, profile.weightKg, profile.goalType,
                    profile.goalDirection, profile.activityBaseline, windowStart, windowDays,
                    profile.dailyCaloricTarget, conditions);
            trends.micronutrients = MICRONUTRIENT_EVALUATOR.evaluate(meals, foods, bucket, conditions,
                    constraints, windowStart, windowDays);
            trends.sleep = SLEEP_EVALUATOR.evaluate(sleepSessions, profile.targetSleepDurationHrs,
                    windowStart, windowDays);
            trends.recovery = RECOVERY_EVALUATOR.evaluate(pointMetrics, request.baselineSummaries,
                    windowStart, windowDays);
            trends.activity = ACTIVITY_EVALUATOR.evaluate(workouts, pointMetrics, profile.weightKg,
                    DERIVED_TARGETS, windowStart, windowDays);
            if (trends.micronutrients == null) {
                trends.micronutrients = new ArrayList<>();
            }

            TrendEvaluation result = new TrendEvaluation();
            result.coverage = buildCoverage(meals, foods, sleepSessions, pointMetrics, workouts,
            		profile.weightKg, windowStart, windowDays);
            result.dimensionTrends = trends;

            logger.info("Trend evaluation complete: calories={} macros={} micronutrients={} sleep={} recovery={} activity={}",
                    trends.calories.status, trends.macros.sufficientData, trends.micronutrients.size(),
                    trends.sleep.status, trends.recovery.status, trends.activity.status);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Threshold data could not be read: " + e.getMessage(), e);
        }
	}
	/**
     * Checks the request, window and profile can't be null. These are
     * window.days must be 7, 14 or 30, and window.start must parse as yyyy-MM-dd
     * @return the parsed window start date
     */
	private static LocalDate validate(TrendRequest request) {
        if (request == null || request.window == null || request.profile == null) {
            throw new IllegalArgumentException("Request must include a window and a profile");
        }
        int days = request.window.days;
        if (days != 7 && days != 14 && days != 30) {
            throw new IllegalArgumentException("window.days must be 7, 14 or 30 but was " + days);
        }
        if (request.window.start == null) {
            throw new IllegalArgumentException("window.start is required");
        }
        try {
            return LocalDate.parse(request.window.start);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("window.start must be yyyy-MM-dd but was " + request.window.start, e);
        }
    }
	
   private static String bucketOf(TrendRequest.Profile profile) {
       if (profile.age == null) {
           logger.warn("Profile age is missing, treating the user as out of scope");
           return "out_of_scope";
       }
       String sex = profile.biologicalSex == null ? "" : profile.biologicalSex;
       return DemographicResolver.resolveBucket(sex, profile.age);
   }

   private static <T> List<T> orEmpty(List<T> list) {
       return list == null ? new ArrayList<>() : list;
   }
   
   /**
    * The result for a user outside the supported population: every dimension is
    * INSUFFICIENT_DATA with its data fields and targets null, and micronutrients
    * is an empty list. 
    */
   
   private static TrendEvaluation outOfScope(int windowDays) {
	   TrendEvaluation.DimensionTrends trends = new TrendEvaluation.DimensionTrends();

       trends.calories = new CaloriesTrend();
       trends.calories.evaluationType = EvaluationType.GUIDELINE;
       trends.calories.status = DimensionStatus.INSUFFICIENT_DATA;

       trends.activity = new ActivityTrend();
       trends.activity.evaluationType = EvaluationType.GUIDELINE;
       trends.activity.status = DimensionStatus.INSUFFICIENT_DATA;

       trends.sleep = new SleepTrend();
       trends.sleep.evaluationType = EvaluationType.ADEQUACY;
       trends.sleep.status = DimensionStatus.INSUFFICIENT_DATA;
       trends.sleep.durationStatus = DimensionStatus.INSUFFICIENT_DATA;
       
       trends.recovery = new RecoveryTrend();
       trends.recovery.evaluationType = (windowDays == 30) ? EvaluationType.PERSONAL_BASELINE
               : EvaluationType.ADEQUACY;
       trends.recovery.status = DimensionStatus.INSUFFICIENT_DATA;
       
       trends.macros = new MacrosTrend();
       trends.macros.evaluationType = EvaluationType.GUIDELINE;
       trends.macros.protein = insufficientMacro();
       trends.macros.carbohydrates = insufficientMacro();
       trends.macros.fat = insufficientMacro();
       trends.macros.saturatedFat = insufficientMacro();
       trends.macros.fiber = insufficientMacro();
       trends.macros.sodium = insufficientMacro();

       trends.micronutrients = new ArrayList<>();

       TrendEvaluation result = new TrendEvaluation();
       result.coverage = outOfScopeCoverage(windowDays);
       result.dimensionTrends = trends;
       return result;
   }
   
   private static MacroTrendEntry insufficientMacro() {
       MacroTrendEntry entry = new MacroTrendEntry();
       entry.status = DimensionStatus.INSUFFICIENT_DATA;
       return entry;
   }
   
   private static long countMealsWithDate(List<Meal> meals) {
	    return meals.stream()
	            .filter(m -> m != null && m.consumedAt != null && m.consumedAt.length() >= 10)
	            .count();
	}

	private static long countMealsWithItems(List<Meal> meals) {
	    return meals.stream()
	            .filter(m -> m != null && m.items != null && !m.items.isEmpty())
	            .count();
	}
	
	private static Map<String, Long> countByMetricType(List<PointMetric> points) {
	    return points.stream()
	            .filter(p -> p != null && p.metricType != null)
	            .collect(Collectors.groupingBy(p -> p.metricType, TreeMap::new, Collectors.counting()));
	}

	private static String earliestSleepStart(List<SleepSession> sessions) {
	    return sessions.stream()
	            .filter(s -> s != null && s.startTime != null)
	            .map(s -> s.startTime)
	            .min(String::compareTo)
	            .orElse("none");
	}
	
	/**
	 * Builds the source-level coverage block by recounting days with the same aggregators the
	 * evaluators use, so the counts always agree with them.
	 * Meal log: days with a usable energy total. A day whose foods all failed to resolve does
	 * not count.
	 * Wearable: days with any wearable data, the union of nights with sleep sessions, days with
	 * resting heart rate or HRV readings, and the days WorkoutMinutesAggregator marks covered
	 * (days with step readings, plus days with a classified workout, light ones included).
	 * Sleep is keyed by wake date, so a night counts toward the day you woke up.
	 */
	private static TrendEvaluation.Coverage buildCoverage(List<Meal> meals, List<Food> foods,
	        List<SleepSession> sleepSessions, List<PointMetric> pointMetrics, List<Workout> workouts,
	        Double weightKg, LocalDate windowStart, int windowDays){
			
		Set<LocalDate> mealDays = DailyNutrientAggregator.aggregateByDay(meals, foods, "energyKcal", windowStart, windowDays).keySet();
		
		Set<LocalDate> wearableDays = new HashSet<>();
	    wearableDays.addAll(SleepAggregator.aggregateNightly(sleepSessions, windowStart, windowDays).keySet());
	    wearableDays.addAll(PointMetricAggregator
	            .averageByDay(pointMetrics, "resting_heart_rate", windowStart, windowDays).keySet());
	    wearableDays.addAll(PointMetricAggregator
	            .averageByDay(pointMetrics, "hrv", windowStart, windowDays).keySet());
	    wearableDays.addAll(WorkoutMinutesAggregator
	            .aggregateDaily(workouts, pointMetrics, weightKg, DERIVED_TARGETS, windowStart, windowDays).keySet());

	    TrendEvaluation.Coverage coverage = new TrendEvaluation.Coverage();
	    coverage.mealLog = coverageEntry(mealDays.size(), windowDays);
	    coverage.wearable = coverageEntry(wearableDays.size(), windowDays);

	    logger.info("Coverage: mealLogDays={} wearableDays={} required={}",
	            coverage.mealLog.daysCovered, coverage.wearable.daysCovered, coverage.mealLog.daysRequired);
	    return coverage;
	}
	
	private static CoverageEntry coverageEntry(int daysCovered, int windowDays) {
	    CoverageEntry entry = new CoverageEntry();
	    entry.daysCovered = daysCovered;
	    entry.daysRequired = CoverageGate.requiredDays(windowDays);
	    entry.sufficient = CoverageGate.isSufficient(daysCovered, windowDays);
	    return entry;
	}
	
	// Out-of-scope users get 0 days covered on both sources, with the requirement still filled in. 
	private static TrendEvaluation.Coverage outOfScopeCoverage(int windowDays) {
	    TrendEvaluation.Coverage coverage = new TrendEvaluation.Coverage();
	    coverage.mealLog = coverageEntry(0, windowDays);
	    coverage.wearable = coverageEntry(0, windowDays);
	    return coverage;
	}
   
}
