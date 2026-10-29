package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

/**
 * The recovery block of the Trend Analysis response. Its shape depends on the
 * window. At windows 7 and 14 the window averages are compared against the
 * 30-day baseline, so the avg and baseline fields and status are populated. At
 * window 30 there is no external baseline, so the first-half and second-half
 * fields are populated, status stays null, and trendDirection is the only
 * signal. Fields that do not apply to the window are null.
 */
public class RecoveryTrend {
    @SerializedName("sufficient_data")
    public boolean sufficientData;

    @SerializedName("evaluation_type")
    public EvaluationType evaluationType;

    @SerializedName("avg_resting_hr")
    public Double avgRestingHr;

    @SerializedName("baseline_resting_hr")
    public Double baselineRestingHr;

    @SerializedName("avg_hrv_sdnn")
    public Double avgHrvSdnn;

    @SerializedName("baseline_hrv_sdnn")
    public Double baselineHrvSdnn;

    @SerializedName("avg_resting_hr_first_half")
    public Double avgRestingHrFirstHalf;

    @SerializedName("avg_resting_hr_second_half")
    public Double avgRestingHrSecondHalf;

    @SerializedName("avg_hrv_sdnn_first_half")
    public Double avgHrvSdnnFirstHalf;

    @SerializedName("avg_hrv_sdnn_second_half")
    public Double avgHrvSdnnSecondHalf;

    public DimensionStatus status;

    @SerializedName("trend_direction")
    public TrendDirection trendDirection;
}
