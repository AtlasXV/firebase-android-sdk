package com.google.firebase.perf.metrics;

import androidx.annotation.Nullable;

import com.google.firebase.perf.v1.PerfMetric;

/**
 * Created by weiping on 2023/8/31
 */
public interface PerfMetricDispatcher {
    void dispatch(@Nullable PerfMetric perfMetric);
}
