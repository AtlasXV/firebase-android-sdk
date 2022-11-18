package com.google.firebase.crashlytics.ext;

import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;

/**
 * 外部监听接口 weiping@atlasv.com 2022/11/17
 */
public interface CrashlyticsExtListener {

    void handleUncaughtException(@Nullable Thread thread, @Nullable Throwable ex);

    void onPreNativeCrashPersistence(@Nullable CrashlyticsReport report);

    void onPreCrashEventPersistence(@Nullable CrashlyticsReport.Session.Event event);
}
