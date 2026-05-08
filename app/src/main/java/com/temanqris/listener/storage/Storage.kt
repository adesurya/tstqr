package com.temanqris.listener.storage

import android.content.Context
import android.content.SharedPreferences
import com.temanqris.listener.parser.PaymentNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "temanqris_config",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_LISTENER_ENABLED = "listener_enabled"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_TOTAL_NOTIFICATIONS_PROCESSED = "total_notifications_processed"
        private const val KEY_TOTAL_PAYMENTS_DETECTED = "total_payments_detected"
        private const val KEY_TOTAL_PAYMENTS_MATCHED = "total_payments_matched"
        private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
    }

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)
    fun setServerUrl(url: String) = prefs.edit().putString(KEY_SERVER_URL, url).apply()

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceSecret(): String? = prefs.getString(KEY_DEVICE_SECRET, null)
    fun setDeviceSecret(secret: String) = prefs.edit().putString(KEY_DEVICE_SECRET, secret).apply()

    fun isListenerEnabled(): Boolean = prefs.getBoolean(KEY_LISTENER_ENABLED, false)
    fun setListenerEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_LISTENER_ENABLED, enabled).apply()

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(complete: Boolean) =
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()

    /** Pause listener untuk waktu tertentu (epoch millis). 0 = tidak paused. */
    fun getPausedUntil(): Long = prefs.getLong(KEY_PAUSED_UNTIL, 0)
    fun setPausedUntil(timestamp: Long) = prefs.edit().putLong(KEY_PAUSED_UNTIL, timestamp).apply()
    fun isPaused(): Boolean = getPausedUntil() > System.currentTimeMillis()
    fun resumeNow() = setPausedUntil(0)

    /** Stats untuk privacy dashboard */
    fun incrementTotalProcessed() {
        prefs.edit().putInt(KEY_TOTAL_NOTIFICATIONS_PROCESSED,
            prefs.getInt(KEY_TOTAL_NOTIFICATIONS_PROCESSED, 0) + 1).apply()
    }
    fun incrementPaymentsDetected() {
        prefs.edit().putInt(KEY_TOTAL_PAYMENTS_DETECTED,
            prefs.getInt(KEY_TOTAL_PAYMENTS_DETECTED, 0) + 1).apply()
    }
    fun incrementPaymentsMatched() {
        prefs.edit().putInt(KEY_TOTAL_PAYMENTS_MATCHED,
            prefs.getInt(KEY_TOTAL_PAYMENTS_MATCHED, 0) + 1).apply()
    }
    fun getStats(): Triple<Int, Int, Int> = Triple(
        prefs.getInt(KEY_TOTAL_NOTIFICATIONS_PROCESSED, 0),
        prefs.getInt(KEY_TOTAL_PAYMENTS_DETECTED, 0),
        prefs.getInt(KEY_TOTAL_PAYMENTS_MATCHED, 0)
    )

    fun updateHeartbeat() = prefs.edit().putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis()).apply()
    fun getLastHeartbeat(): Long = prefs.getLong(KEY_LAST_HEARTBEAT, 0)
}

class NotificationLogStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "temanqris_logs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_LOGS = "notification_logs"
        private const val MAX_LOGS = 100
    }

    fun logPayment(payment: PaymentNotification, notificationKey: String): String {
        val id = UUID.randomUUID().toString()
        val entry = JSONObject().apply {
            put("id", id)
            put("type", "payment")
            put("source", payment.source.name)
            put("amount", payment.amount)
            put("payer_name", payment.payerName ?: JSONObject.NULL)
            put("raw_text", payment.rawText)
            put("confidence", payment.confidence)
            put("posted_at", payment.postedAt)
            put("notification_key", notificationKey)
            put("status", "pending_upload")
            put("created_at", System.currentTimeMillis())
        }
        appendLog(entry)
        return id
    }

    fun logSkipped(packageName: String, rawText: String, postedAt: Long) {
        val entry = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("type", "skipped")
            put("package", packageName)
            put("raw_text", rawText)
            put("posted_at", postedAt)
            put("created_at", System.currentTimeMillis())
        }
        appendLog(entry)
    }

    fun markUploaded(logId: String, matched: Boolean, orderId: String?) {
        updateLog(logId) { entry ->
            entry.put("status", "uploaded")
            entry.put("matched", matched)
            entry.put("order_id", orderId ?: JSONObject.NULL)
            entry.put("uploaded_at", System.currentTimeMillis())
        }
    }

    fun markFailed(logId: String, error: String) {
        updateLog(logId) { entry ->
            entry.put("status", "failed")
            entry.put("error", error)
            entry.put("failed_at", System.currentTimeMillis())
        }
    }

    fun getRecentLogs(limit: Int = 20): List<JSONObject> {
        val logs = readLogs()
        return (0 until minOf(logs.length(), limit)).map { logs.getJSONObject(it) }
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun appendLog(entry: JSONObject) {
        synchronized(this) {
            val logs = readLogs()
            val newLogs = JSONArray()
            newLogs.put(entry)
            for (i in 0 until minOf(logs.length(), MAX_LOGS - 1)) {
                newLogs.put(logs.getJSONObject(i))
            }
            prefs.edit().putString(KEY_LOGS, newLogs.toString()).apply()
        }
    }

    private fun updateLog(logId: String, mutation: (JSONObject) -> Unit) {
        synchronized(this) {
            val logs = readLogs()
            for (i in 0 until logs.length()) {
                val entry = logs.getJSONObject(i)
                if (entry.optString("id") == logId) {
                    mutation(entry)
                    prefs.edit().putString(KEY_LOGS, logs.toString()).apply()
                    return
                }
            }
        }
    }

    private fun readLogs(): JSONArray {
        val raw = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }
}
