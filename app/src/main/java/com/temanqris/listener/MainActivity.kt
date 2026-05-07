package com.temanqris.listener

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.temanqris.listener.databinding.ActivityMainBinding
import com.temanqris.listener.storage.NotificationLogStore
import com.temanqris.listener.storage.PreferencesManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI sederhana untuk:
 *  - Setup server URL dan device secret
 *  - Cek status notification access permission
 *  - Lihat log notifikasi terakhir (untuk debugging)
 *  - Test koneksi ke server
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var logStore: NotificationLogStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        logStore = NotificationLogStore(this)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLogs()
    }

    private fun setupUI() {
        // Load saved values
        binding.editServerUrl.setText(prefs.getServerUrl() ?: "https://api.example.com")
        binding.editDeviceSecret.setText(prefs.getDeviceSecret() ?: "")
        binding.textDeviceId.text = "Device ID: ${prefs.getDeviceId()}"

        // Save config button
        binding.btnSaveConfig.setOnClickListener {
            val url = binding.editServerUrl.text.toString().trim()
            val secret = binding.editDeviceSecret.text.toString().trim()

            if (url.isEmpty() || secret.isEmpty()) {
                Toast.makeText(this, "URL dan secret wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.setServerUrl(url.removeSuffix("/"))
            prefs.setDeviceSecret(secret)
            Toast.makeText(this, "Konfigurasi tersimpan", Toast.LENGTH_SHORT).show()
        }

        // Open notification access settings
        binding.btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Start/stop listener
        binding.btnToggleListener.setOnClickListener {
            if (prefs.isListenerEnabled()) {
                prefs.setListenerEnabled(false)
                ListenerKeepAliveService.stop(this)
                Toast.makeText(this, "Listener dimatikan", Toast.LENGTH_SHORT).show()
            } else {
                if (!isNotificationAccessGranted()) {
                    Toast.makeText(this, "Grant permission dulu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.setListenerEnabled(true)
                ListenerKeepAliveService.start(this)
                Toast.makeText(this, "Listener aktif", Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }

        // Refresh logs button
        binding.btnRefreshLogs.setOnClickListener {
            refreshLogs()
        }
    }

    private fun refreshStatus() {
        val permGranted = isNotificationAccessGranted()
        val listenerOn = prefs.isListenerEnabled()
        val configured = prefs.getServerUrl() != null && prefs.getDeviceSecret() != null

        binding.textPermissionStatus.text = if (permGranted) {
            "✓ Permission granted"
        } else {
            "✗ Permission belum granted - klik tombol di bawah"
        }

        binding.textListenerStatus.text = when {
            !configured -> "⚠ Belum dikonfigurasi"
            !permGranted -> "⚠ Butuh permission"
            listenerOn -> "✓ Listener AKTIF"
            else -> "○ Listener tidak aktif"
        }

        binding.btnToggleListener.text = if (listenerOn) "Matikan Listener" else "Aktifkan Listener"
    }

    private fun refreshLogs() {
        val logs = logStore.getRecentLogs(20)
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val text = if (logs.isEmpty()) {
            "Belum ada log."
        } else {
            logs.joinToString("\n\n") { entry -> formatLog(entry, formatter) }
        }

        binding.textLogs.text = text
    }

    private fun formatLog(entry: JSONObject, formatter: SimpleDateFormat): String {
        val time = formatter.format(Date(entry.optLong("created_at", 0)))
        val type = entry.optString("type")

        return when (type) {
            "payment" -> {
                val source = entry.optString("source")
                val amount = entry.optLong("amount")
                val payer = entry.optString("payer_name", "Unknown")
                val status = entry.optString("status")
                val matched = entry.optBoolean("matched", false)
                val orderId = entry.optString("order_id", "-")

                buildString {
                    append("[$time] $source - Rp${"%,d".format(amount)}\n")
                    append("Pembayar: $payer\n")
                    append("Status: $status")
                    if (status == "uploaded") {
                        append(matched.let { if (it) " ✓ MATCH order: $orderId" else " (no match)" })
                    }
                }
            }
            "skipped" -> {
                val pkg = entry.optString("package")
                val raw = entry.optString("raw_text").take(80)
                "[$time] SKIPPED ($pkg): $raw..."
            }
            else -> "[$time] Unknown entry"
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, PaymentNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}
