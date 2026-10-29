package com.nutrisense.trendanalysis.evaluation.model;

import com.google.gson.annotations.SerializedName;

public class SleepSession {
    public String stage;

    @SerializedName("start_time")
    public String startTime;

    @SerializedName("end_time")
    public String endTime;
}