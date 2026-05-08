package com.temanqris.listener.privacy

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.temanqris.listener.R
import com.temanqris.listener.storage.NotificationLogStore
import com.temanqris.listener.storage.PreferencesManager

/**
 * Privacy dashboard - transparansi 100% tentang apa yang dibaca app.
 *
 * Tujuan:
 *  - User bisa lihat statistik notifikasi yang diproses
 *  - User bisa hapus semua log
 *  - User bisa baca privacy policy detail
 *  - User bisa lihat exact apa yang dikirim ke server
 */
class PrivacyDashboardActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var logStore: NotificationLogStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        prefs = PreferencesManager(this)
        logStore = NotificationLogStore(this)

        setupViews()
    }

    private fun setupViews() {
        // Stats
        val (total, detected, matched) = prefs.getStats()
        findViewById<TextView>(R.id.textTotalProcessed).text = total.toString()
        findViewById<TextView>(R.id.textPaymentsDetected).text = detected.toString()
        findViewById<TextView>(R.id.textPaymentsMatched).text = matched.toString()

        // Detail apa yang dibaca
        findViewById<TextView>(R.id.textWhatWeRead).text = """
            App ini HANYA membaca notifikasi dari 3 aplikasi:
            
            1. Gojek / GoPay (com.gojek.app)
            2. DANA (id.dana)
            3. OVO (ovo.id)
            
            Notifikasi dari WhatsApp, SMS, email pribadi, bank, social media, dan aplikasi lain TIDAK pernah dibaca atau diproses oleh app ini.
        """.trimIndent()

        // Detail apa yang dikirim
        findViewById<TextView>(R.id.textWhatWeSend).text = """
            Saat notifikasi pembayaran masuk terdeteksi, data berikut dikirim ke server Anda:
            
            • Nominal pembayaran (e.g. Rp 50.000)
            • Nama pembayar (e.g. "Budi Santoso")
            • Sumber e-wallet (Gojek/DANA/OVO)
            • Timestamp notifikasi
            • Teks notifikasi mentah (untuk debugging)
            
            Data dienkripsi dengan HMAC-SHA256 dan dikirim ke server URL yang Anda konfigurasi sendiri (bukan ke pihak ketiga).
        """.trimIndent()

        // Detail apa yang TIDAK dikirim
        findViewById<TextView>(R.id.textWhatWeDontSend).text = """
            Data berikut TIDAK PERNAH dikirim atau disimpan:
            
            • Saldo total e-wallet Anda
            • Riwayat transaksi sebelumnya
            • Notifikasi non-pembayaran
            • Data kontak, foto, atau file di HP
            • Data lokasi
            • Browsing history
        """.trimIndent()

        // Buttons
        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Semua Log?")
                .setMessage("Semua log notifikasi yang tersimpan di HP akan dihapus. Data di server tidak terpengaruh.")
                .setPositiveButton("Hapus") { _, _ ->
                    logStore.clearLogs()
                    android.widget.Toast.makeText(
                        this, "Log dihapus", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            showRecentLogs()
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun showRecentLogs() {
        val logs = logStore.getRecentLogs(50)
        val text = if (logs.isEmpty()) {
            "Belum ada log."
        } else {
            logs.joinToString("\n\n") { entry ->
                """
                |${entry.optString("type").uppercase()}
                |Source: ${entry.optString("source", "-")}
                |Amount: ${entry.optLong("amount", 0)}
                |Raw: ${entry.optString("raw_text", "-").take(100)}
                |Status: ${entry.optString("status", "-")}
                """.trimMargin()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Log Notifikasi")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }
}
