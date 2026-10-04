package com.nutrisense.trendanalysis.evaluation.thresholds;

/**
 * Resolves a user's biological sex and age into one of the
 * demographic buckets used throughout the threshold rows (e.g.
 * "male_19_plus", "female_19_50").
 */
public class DemographicResolver {
	public static String resolveBucket(String biologicalSex, int age) {
		if (age < 19)
			return "out_of_scope";
		else if (biologicalSex.equals("male"))
			return "male_19_plus";
		else if (biologicalSex.equals("female") && age < 51)
			return "female_19_50";
		else if (biologicalSex.equals("female"))
			return "female_51_plus";
		else
			return "unspecified";
	}

}
