package com.google.firebase.crashlytics.ext;

import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;

/** weiping@atlasv.com 2022/11/17 */
public class NoopCrashlyticsExtListener implements CrashlyticsExtListener {
  @Override
  public void handleUncaughtException(@Nullable Thread thread, @Nullable Throwable ex) {}

  @Override
  public void onPreNativeCrashPersistence(@Nullable CrashlyticsReport report) {}

  @Override
  public void onPreCrashEventPersistence(@Nullable Event event) {}
}
