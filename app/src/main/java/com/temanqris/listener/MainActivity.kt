package com.temanqris.listener

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.content.ComponentName
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.temanqris.listener.onboarding.OnboardingActivity
import com.temanqris.listener.privacy.PrivacyDashboardActivity
import com.temanqris.listener.storage.NotificationLogStore
import com.temanqris.listener.storage.PreferencesManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var logStore: NotificationLogStore

    private lateinit var statusCard: LinearLayout
    private lateinit var statusIcon: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var btnQuickAction: Button

    private lateinit var statTotal: TextView
    private lateinit var statDetected: TextView
    private lateinit var statMatched: TextView

    private lateinit var btnPause: Button
    private lateinit var btnPrivacy: Button
    private lateinit var btnLogs: Button
    private lateinit var textRecentActivity: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)

        // Redirect ke onboarding kalau belum selesai
        if (!prefs.isOnboardingComplete()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        logStore = NotificationLogStore(this)

        bindViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun bindViews() {
        statusCard = findViewById(R.id.statusCard)
        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusDescription = findViewById(R.id.statusDescription)
        btnQuickAction = findViewById(R.id.btnQuickAction)

        statTotal = findViewById(R.id.statTotal)
        statDetected = findViewById(R.id.statDetected)
        statMatched = findViewById(R.id.statMatched)

        btnPause = findViewById(R.id.btnPause)
        btnPrivacy = findViewById(R.id.btnPrivacy)
        btnLogs = findViewById(R.id.btnLogs)
        textRecentActivity = findViewById(R.id.textRecentActivity)
    }

    private fun setupClickListeners() {
        btnPause.setOnClickListener { showPauseDialog() }
        btnPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyDashboardActivity::class.java))
        }
        btnLogs.setOnClickListener {
            // Refresh logs in current view
            refreshRecentActivity()
        }
    }

    private fun refreshUI() {
        val permGranted = isNotificationAccessGranted()
        val isPaused = prefs.isPaused()
        val isEnabled = prefs.isListenerEnabled()
        val isConfigured = prefs.getServerUrl() != null && prefs.getDeviceSecret() != null

        // Status visual berdasarkan state
        when {
            !permGranted -> renderStatusError(
                "Permission Belum Aktif",
                "Notification access permission diperlukan untuk membaca pembayaran masuk",
                "Buka Settings"
            ) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            !isConfigured -> renderStatusWarning(
                "Belum Dikonfigurasi",
                "Server URL atau Device Secret belum di-set",
                "Setup Sekarang"
            ) {
                startActivity(Intent(this, OnboardingActivity::class.java))
            }

            isPaused -> {
                val until = prefs.getPausedUntil()
                val remaining = (until - System.currentTimeMillis()) / 60000
                renderStatusPaused(
                    "Listener Di-pause",
                    "Akan resume otomatis dalam $remaining menit",
                    "Resume Sekarang"
                ) {
                    prefs.resumeNow()
                    refreshUI()
                }
            }

            isEnabled -> renderStatusActive(
                "Listener Aktif",
                "Memantau notifikasi pembayaran dari Gojek, DANA, OVO",
                "Matikan"
            ) {
                showStopDialog()
            }

            else -> renderStatusError(
                "Listener Tidak Aktif",
                "Pembayaran tidak akan ter-update otomatis. Aktifkan kembali untuk auto-confirm.",
                "Aktifkan Sekarang"
            ) {
                prefs.setListenerEnabled(true)
                ListenerKeepAliveService.start(this)
                refreshUI()
            }
        }

        // Stats
        val (total, detected, matched) = prefs.getStats()
        statTotal.text = total.toString()
        statDetected.text = detected.toString()
        statMatched.text = matched.toString()

        // Pause button visibility
        btnPause.visibility = if (isEnabled && !isPaused) View.VISIBLE else View.GONE

        refreshRecentActivity()
    }

    private fun renderStatusActive(title: String, desc: String, btnText: String, action: () -> Unit) {
        statusCard.setBackgroundColor(0xFFE8F5E9.toInt())
        statusIcon.text = "✓"
        statusIcon.setTextColor(0xFF22C55E.toInt())
        statusTitle.text = title
        statusTitle.setTextColor(0xFF166534.toInt())
        statusDescription.text = desc
        statusDescription.setTextColor(0xFF15803D.toInt())
        btnQuickAction.text = btnText
        btnQuickAction.setBackgroundColor(0xFF22C55E.toInt())
        btnQuickAction.setOnClickListener { action() }
    }

    private fun renderStatusInactive(title: String, desc: String, btnText: String, action: () -> Unit) {
        statusCard.setBackgroundColor(0xFFF5F5F5.toInt())
        statusIcon.text = "○"
        statusIcon.setTextColor(0xFF666666.toInt())
        statusTitle.text = title
        statusTitle.setTextColor(0xFF333333.toInt())
        statusDescription.text = desc
        statusDescription.setTextColor(0xFF666666.toInt())
        btnQuickAction.text = btnText
        btnQuickAction.setBackgroundColor(0xFFFF6B35.toInt())
        btnQuickAction.setOnClickListener { action() }
    }

    private fun renderStatusError(title: String, desc: String, btnText: String, action: () -> Unit) {
        statusCard.setBackgroundColor(0xFFFFEBEE.toInt())
        statusIcon.text = "!"
        statusIcon.setTextColor(0xFFDC2626.toInt())
        statusTitle.text = title
        statusTitle.setTextColor(0xFF991B1B.toInt())
        statusDescription.text = desc
        statusDescription.setTextColor(0xFFB91C1C.toInt())
        btnQuickAction.text = btnText
        btnQuickAction.setBackgroundColor(0xFFDC2626.toInt())
        btnQuickAction.setOnClickListener { action() }
    }

    private fun renderStatusWarning(title: String, desc: String, btnText: String, action: () -> Unit) {
        statusCard.setBackgroundColor(0xFFFEF3C7.toInt())
        statusIcon.text = "⚠"
        statusIcon.setTextColor(0xFFF59E0B.toInt())
        statusTitle.text = title
        statusTitle.setTextColor(0xFF92400E.toInt())
        statusDescription.text = desc
        statusDescription.setTextColor(0xFFB45309.toInt())
        btnQuickAction.text = btnText
        btnQuickAction.setBackgroundColor(0xFFF59E0B.toInt())
        btnQuickAction.setOnClickListener { action() }
    }

    private fun renderStatusPaused(title: String, desc: String, btnText: String, action: () -> Unit) {
        statusCard.setBackgroundColor(0xFFEDE9FE.toInt())
        statusIcon.text = "II"
        statusIcon.setTextColor(0xFF7C3AED.toInt())
        statusTitle.text = title
        statusTitle.setTextColor(0xFF5B21B6.toInt())
        statusDescription.text = desc
        statusDescription.setTextColor(0xFF6D28D9.toInt())
        btnQuickAction.text = btnText
        btnQuickAction.setBackgroundColor(0xFF7C3AED.toInt())
        btnQuickAction.setOnClickListener { action() }
    }

    private fun showPauseDialog() {
        val options = arrayOf(
            "Pause 15 menit",
            "Pause 1 jam",
            "Pause 4 jam",
            "Pause sampai besok pagi"
        )
        val durations = longArrayOf(
            15L * 60_000,
            60L * 60_000,
            4L * 60L * 60_000,
            12L * 60L * 60_000
        )

        AlertDialog.Builder(this)
            .setTitle("Pause Listener")
            .setItems(options) { _, which ->
                val until = System.currentTimeMillis() + durations[which]
                prefs.setPausedUntil(until)
                refreshUI()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showStopDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠ Yakin Matikan Listener?")
            .setMessage("Konsekuensi mematikan listener:\n\n" +
                    "• Pembayaran TIDAK ter-update otomatis\n" +
                    "• Anda harus cek manual setiap order\n" +
                    "• Customer mungkin menunggu lebih lama\n\n" +
                    "Untuk pause sementara, gunakan tombol Pause (15 menit - 12 jam) di header.\n\n" +
                    "Yakin matikan listener sepenuhnya?")
            .setPositiveButton("Ya, Matikan") { _, _ ->
                prefs.setListenerEnabled(false)
                ListenerKeepAliveService.stop(this)
                refreshUI()
            }
            .setNeutralButton("Pause Saja") { _, _ ->
                showPauseDialog()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refreshRecentActivity() {
        val logs = logStore.getRecentLogs(5)
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        textRecentActivity.text = if (logs.isEmpty()) {
            "Belum ada aktivitas. Listener akan menampilkan log pembayaran masuk di sini."
        } else {
            logs.joinToString("\n") { entry -> formatLogShort(entry, formatter) }
        }
    }

    private fun formatLogShort(entry: JSONObject, formatter: SimpleDateFormat): String {
        val time = formatter.format(Date(entry.optLong("created_at", 0)))
        val type = entry.optString("type")

        return when (type) {
            "payment" -> {
                val source = entry.optString("source")
                val amount = entry.optLong("amount")
                val matched = entry.optBoolean("matched", false)
                val status = if (matched) "✓" else "○"
                "[$time] $status $source - Rp${"%,d".format(amount)}"
            }
            "skipped" -> "[$time] · Notifikasi non-pembayaran"
            else -> "[$time] $type"
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, PaymentNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}
