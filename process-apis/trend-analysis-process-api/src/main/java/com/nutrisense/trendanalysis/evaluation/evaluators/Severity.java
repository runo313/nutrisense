package com.nutrisense.trendanalysis.evaluation.evaluators;

import com.nutrisense.trendanalysis.evaluation.model.DimensionStatus;
/**
 * @return whichever status is more severe (HARD_FLAG over SOFT_WARNING over
 *         anything else); a on a tie
 */

public class Severity {
    public static DimensionStatus worse(DimensionStatus a, DimensionStatus b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(DimensionStatus s) {
        switch (s) {
            case HARD_FLAG: return 2;
            case SOFT_WARNING: return 1;
            default: return 0;
        }
    }
}
