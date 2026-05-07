package com.temanqris.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.temanqris.listener.network.WebhookClient
import com.temanqris.listener.parser.PaymentNotificationParser
import com.temanqris.listener.storage.NotificationLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service inti yang menangkap setiap notifikasi yang muncul di status bar.
 *
 * IMPORTANT:
 *  - User HARUS grant permission "Notification Access" di Settings secara manual
 *  - Service ini bisa di-kill OS, makanya kita pair dengan ListenerKeepAliveService
 *  - Hanya proses notifikasi dari package e-wallet yang kita whitelist
 */
class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentListener"

        // Whitelist: hanya package ini yang akan kita proses
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

    // Deduplication: simpan key notifikasi yang sudah diproses
    // (kadang OS deliver notifikasi yang sama 2x)
    private val recentlyProcessed = mutableSetOf<String>()
    private val DEDUP_WINDOW_MS = 30_000L

    override fun onCreate() {
        super.onCreate()
        webhookClient = WebhookClient(applicationContext)
        logStore = NotificationLogStore(applicationContext)
        Log.d(TAG, "Service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected to notification system")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Filter awal: hanya package yang di-watch
        if (sbn.packageName !in WATCHED_PACKAGES) return

        // Deduplication berdasarkan key + timestamp window
        val dedupKey = "${sbn.key}_${sbn.postTime / 1000}"
        if (recentlyProcessed.contains(dedupKey)) {
            Log.d(TAG, "Duplicate notification ignored: $dedupKey")
            return
        }
        recentlyProcessed.add(dedupKey)
        cleanupDedupCache()

        // Extract content dari notification
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        Log.d(TAG, "Received from ${sbn.packageName}: $title - $text")

        // Process di background coroutine
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
        // 1. Parse notifikasi
        val payment = PaymentNotificationParser.parse(
            packageName, title, text, bigText, subText, postedAt
        )

        if (payment == null) {
            Log.d(TAG, "Notification not a valid payment: $title - $text")
            // Tetap log untuk debugging
            logStore.logSkipped(packageName, "$title | $text", postedAt)
            return
        }

        Log.i(TAG, "✓ Payment detected: ${payment.source} Rp${payment.amount} from ${payment.payerName} (confidence: ${payment.confidence})")

        // 2. Simpan ke local log untuk audit & UI
        val logId = logStore.logPayment(payment, notificationKey)

        // 3. Kirim ke server
        try {
            val response = webhookClient.sendPaymentNotification(payment)
            logStore.markUploaded(logId, response.matched, response.orderId)

            Log.i(TAG, "✓ Sent to server. Matched=${response.matched}, OrderID=${response.orderId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to server", e)
            logStore.markFailed(logId, e.message ?: "Unknown error")
            // Akan di-retry oleh WorkManager (tidak diimplementasi di POC ini
            // tapi struktur sudah siap)
        }
    }

    private fun cleanupDedupCache() {
        // Simple cleanup: kalau cache > 100 entries, drop half
        if (recentlyProcessed.size > 100) {
            val toRemove = recentlyProcessed.take(50)
            recentlyProcessed.removeAll(toRemove.toSet())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Tidak perlu handle removal untuk POC ini
    }
}
