package com.temanqris.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.temanqris.listener.network.WebhookClient
import com.temanqris.listener.parser.PaymentNotificationParser
import com.temanqris.listener.storage.NotificationLogStore
import com.temanqris.listener.storage.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"

        private val WATCHED_PACKAGES = setOf(
            "com.gojek.app",
            "com.gojek.gopay",
            "id.dana",
            "ovo.id"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var webhookClient: WebhookClient
    private lateinit var logStore: NotificationLogStore
    private lateinit var prefs: PreferencesManager

    private val recentlyProcessed = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        webhookClient = WebhookClient(applicationContext)
        logStore = NotificationLogStore(applicationContext)
        prefs = PreferencesManager(applicationContext)
        Log.d(TAG, "Service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        prefs.updateHeartbeat()
        Log.d(TAG, "Listener connected to notification system")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Filter awal: hanya package yang di-watch
        if (sbn.packageName !in WATCHED_PACKAGES) return

        // Cek state - listener mungkin di-pause atau di-disable
        if (!prefs.isListenerEnabled()) {
            Log.d(TAG, "Listener disabled, skipping")
            return
        }
        if (prefs.isPaused()) {
            Log.d(TAG, "Listener paused, skipping")
            return
        }

        // Update heartbeat
        prefs.updateHeartbeat()
        prefs.incrementTotalProcessed()

        // Deduplication
        val dedupKey = "${sbn.key}_${sbn.postTime / 1000}"
        if (recentlyProcessed.contains(dedupKey)) {
            return
        }
        recentlyProcessed.add(dedupKey)
        cleanupDedupCache()

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        Log.d(TAG, "Received from ${sbn.packageName}: $title - $text")

        scope.launch {
            processNotification(
                packageName = sbn.packageName,
                title = title,
                text = text,
                bigText = bigText,
                subText = subText,
                postedAt = sbn.postTime,
                notificationKey = sbn.key
            )
        }
    }

    private suspend fun processNotification(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
        postedAt: Long,
        notificationKey: String
    ) {
        val payment = PaymentNotificationParser.parse(
            packageName, title, text, bigText, subText, postedAt
        )

        if (payment == null) {
            logStore.logSkipped(packageName, "$title | $text", postedAt)
            return
        }

        Log.i(TAG, "Payment detected: ${payment.source} Rp${payment.amount}")
        prefs.incrementPaymentsDetected()

        val logId = logStore.logPayment(payment, notificationKey)

        try {
            val response = webhookClient.sendPaymentNotification(payment)
            logStore.markUploaded(logId, response.matched, response.orderId)

            if (response.matched) {
                prefs.incrementPaymentsMatched()
            }

            Log.i(TAG, "Sent to server. Matched=${response.matched}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to server", e)
            logStore.markFailed(logId, e.message ?: "Unknown error")
        }
    }

    private fun cleanupDedupCache() {
        if (recentlyProcessed.size > 100) {
            val toRemove = recentlyProcessed.take(50)
            recentlyProcessed.removeAll(toRemove.toSet())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not needed
    }
}
