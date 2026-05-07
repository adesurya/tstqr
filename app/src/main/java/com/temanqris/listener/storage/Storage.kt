package com.temanqris.listener.storage

import android.content.Context
import android.content.SharedPreferences
import com.temanqris.listener.parser.PaymentNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Manager untuk SharedPreferences (config, device credentials).
 */
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
    }

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)
    fun setServerUrl(url: String) = prefs.edit().putString(KEY_SERVER_URL, url).apply()

    /** Auto-generate device ID kalau belum ada. */
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
}

/**
 * Simple log store pakai SharedPreferences (untuk POC).
 * Di production: pakai Room database.
 */
class NotificationLogStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "temanqris_logs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_LOGS = "notification_logs"
        private const val MAX_LOGS = 100
    }

    /**
     * Log notifikasi yang berhasil di-parse sebagai payment.
     * Return: log entry ID
     */
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

    /**
     * Log notifikasi yang di-skip (untuk debugging false negatives).
     */
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

    private fun appendLog(entry: JSONObject) {
        synchronized(this) {
            val logs = readLogs()
            // Prepend (newest first)
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
