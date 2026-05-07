package com.temanqris.listener.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.temanqris.listener.parser.PaymentNotification
import com.temanqris.listener.storage.PreferencesManager
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.UUID

/**
 * HTTP client untuk mengirim notifikasi pembayaran ke server.
 *
 * Pakai HttpURLConnection (built-in) supaya POC ini tidak perlu dependency
 * tambahan. Di production sebaiknya pakai Retrofit/OkHttp.
 *
 * Security:
 *  - Setiap request di-sign dengan HMAC-SHA256 menggunakan device secret
 *  - Body di-hash supaya server bisa verify integrity
 *  - Device ID dikirim untuk identifikasi merchant mana yang punya device ini
 */
class WebhookClient(private val context: Context) {

    companion object {
        private const val TAG = "WebhookClient"
        private const val TIMEOUT_MS = 15_000
    }

    private val prefs = PreferencesManager(context)

    data class WebhookResponse(
        val success: Boolean,
        val matched: Boolean,        // apakah notifikasi match dengan order pending
        val orderId: String?,        // order_id yang ke-match
        val message: String?
    )

    suspend fun sendPaymentNotification(payment: PaymentNotification): WebhookResponse {
        val serverUrl = prefs.getServerUrl()
            ?: throw IllegalStateException("Server URL not configured")
        val deviceId = prefs.getDeviceId()
        val deviceSecret = prefs.getDeviceSecret()
            ?: throw IllegalStateException("Device not registered")

        // Build payload
        val requestId = UUID.randomUUID().toString()
        val body = buildPayload(payment, deviceId, requestId)
        val signature = signBody(body, deviceSecret)

        Log.d(TAG, "POST $serverUrl")
        Log.d(TAG, "Body: $body")

        val url = URL("$serverUrl/api/listener/notification")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            doInput = true

            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Device-ID", deviceId)
            setRequestProperty("X-Request-ID", requestId)
            setRequestProperty("X-Signature", "sha256=$signature")
            setRequestProperty("User-Agent", "TemanQrisListener/1.0 (Android ${Build.VERSION.SDK_INT})")
        }

        try {
            // Write body
            connection.outputStream.use { os: OutputStream ->
                os.write(body.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            Log.d(TAG, "Response $responseCode: $responseBody")

            return parseResponse(responseCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(
        payment: PaymentNotification,
        deviceId: String,
        requestId: String
    ): String {
        return JSONObject().apply {
            put("request_id", requestId)
            put("device_id", deviceId)
            put("source", payment.source.name.lowercase())
            put("amount", payment.amount)
            put("payer_name", payment.payerName ?: JSONObject.NULL)
            put("raw_text", payment.rawText)
            put("posted_at", payment.postedAt)
            put("confidence", payment.confidence)
            put("listener_version", "1.0")
        }.toString()
    }

    private fun signBody(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseResponse(code: Int, body: String): WebhookResponse {
        if (code !in 200..299) {
            return WebhookResponse(
                success = false,
                matched = false,
                orderId = null,
                message = "HTTP $code: $body"
            )
        }

        return try {
            val json = JSONObject(body)
            WebhookResponse(
                success = json.optBoolean("success", false),
                matched = json.optBoolean("matched", false),
                orderId = json.optString("order_id").takeIf { it.isNotEmpty() },
                message = json.optString("message")
            )
        } catch (e: Exception) {
            WebhookResponse(
                success = false,
                matched = false,
                orderId = null,
                message = "Failed to parse response: ${e.message}"
            )
        }
    }
}
