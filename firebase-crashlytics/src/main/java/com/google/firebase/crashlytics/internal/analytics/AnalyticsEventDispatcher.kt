package com.google.firebase.crashlytics.internal.analytics

import android.os.Bundle
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport

/**
 * Created by weiping on 2025/8/22
 */
object AnalyticsEventDispatcher {
    const val FIND_ANR = "find_anr"
    fun dispatchReportPrePersist(
        eventLogger: AnalyticsEventLogger?,
        event: CrashlyticsReport.Session.Event
    ) {
        eventLogger ?: return
        if (event.type == "anr") {
            eventLogger.logEvent(FIND_ANR, Bundle().apply {
                putString("collected_by", "crashlytics_sdk")
            })
        }
    }
}