package com.lifetrace.finance.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lifetrace.finance.AppGraph
import com.lifetrace.finance.core.NotificationSample
import com.lifetrace.finance.core.NotificationTransactionParser
import com.lifetrace.finance.sync.SyncScheduler
import kotlinx.coroutines.*

class NotificationCaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras
        val sample = NotificationSample(
            packageName = sbn.packageName,
            postTimeMillis = sbn.postTime,
            title = extras.getCharSequence("android.title")?.toString(),
            text = extras.getCharSequence("android.text")?.toString(),
            bigText = extras.getCharSequence("android.bigText")?.toString(),
            subText = extras.getCharSequence("android.subText")?.toString(),
            notificationKey = sbn.key,
        )
        val candidate = NotificationTransactionParser.parse(sample) ?: return
        val dedupKey = NotificationTransactionParser.dedupKey(candidate)
        scope.launch {
            val graph = AppGraph.get(applicationContext)
            val profile = graph.finance.ensureProfile()
            graph.db.notificationDao().deleteBefore(java.time.Instant.now().minusSeconds(7 * 24 * 3600L).toString())
            val txId = graph.finance.captureNotificationCandidate(profile.id, candidate, dedupKey)
            if (txId != null) {
                graph.diagnostics.event(
                    component = "NOTIFICATION_PARSE",
                    code = "CANDIDATE_CREATED",
                    message = "candidate created source=${candidate.sourcePackage} confidence=${candidate.confidence}",
                )
                SyncScheduler.scheduleNow(applicationContext)
            } else {
                graph.diagnostics.event("NOTIFICATION_PARSE", "DUPLICATE_IGNORED", "duplicate payment notification ignored")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
