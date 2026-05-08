package com.temanqris.listener.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.temanqris.listener.MainActivity
import com.temanqris.listener.PaymentNotificationListener
import com.temanqris.listener.R
import com.temanqris.listener.storage.PreferencesManager

/**
 * Onboarding WAJIB - tidak bisa di-skip.
 *
 * Listener WAJIB di-enable di akhir onboarding karena ini fitur inti app.
 * Tanpa listener aktif, app tidak punya value proposition.
 *
 * Animasi: setiap transition step ada slide-in + fade animation.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private var currentStep = 1
    private val totalSteps = 5

    // Container yang akan di-animate
    private lateinit var contentContainer: View
    private lateinit var stepIndicator: TextView
    private lateinit var progressDots: List<View>
    private lateinit var iconView: ImageView
    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var detailsView: TextView
    private lateinit var btnPrimary: Button
    private lateinit var btnSecondary: Button
    private lateinit var configFields: View

    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefs = PreferencesManager(this)
        bindViews()

        // Show step 1 dengan animasi entrance
        showStepWithAnimation(1, isInitial = true)
    }

    override fun onResume() {
        super.onResume()
        // Auto-advance kalau permission di-grant di Settings
        if (currentStep == 4 && isNotificationAccessGranted()) {
            Handler(Looper.getMainLooper()).postDelayed({
                showStepWithAnimation(5)
            }, 500)
        }
    }

    override fun onBackPressed() {
        // Block back button kalau di step yang vital - paksa user selesaikan onboarding
        when (currentStep) {
            1 -> showExitConfirmation()
            else -> {
                if (currentStep > 1) {
                    showStepWithAnimation(currentStep - 1, reverse = true)
                }
            }
        }
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Setup belum selesai")
            .setMessage("App ini butuh setup awal sebelum bisa digunakan. Yakin keluar sekarang?")
            .setPositiveButton("Keluar") { _, _ -> finishAffinity() }
            .setNegativeButton("Lanjutkan Setup", null)
            .show()
    }

    private fun bindViews() {
        contentContainer = findViewById(R.id.contentContainer)
        stepIndicator = findViewById(R.id.textStepIndicator)
        progressDots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4),
            findViewById(R.id.dot5)
        )
        iconView = findViewById(R.id.iconBig)
        titleView = findViewById(R.id.textTitle)
        descriptionView = findViewById(R.id.textDescription)
        detailsView = findViewById(R.id.textDetails)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnSecondary = findViewById(R.id.btnSecondary)
        configFields = findViewById(R.id.configFieldsContainer)

        // SKIP BUTTON DIHAPUS - tidak ada lagi opsi untuk skip
    }

    /**
     * Show step dengan animasi slide + fade.
     */
    private fun showStepWithAnimation(step: Int, reverse: Boolean = false, isInitial: Boolean = false) {
        if (isAnimating && !isInitial) return
        isAnimating = true

        if (isInitial) {
            // First time - hanya entrance animation
            renderStep(step)
            animateEntrance()
        } else {
            // Slide out current, then slide in new
            animateExit(reverse) {
                renderStep(step)
                animateEntrance(reverse)
            }
        }
    }

    private fun animateEntrance(reverse: Boolean = false) {
        val slideFrom = if (reverse) -300f else 300f

        contentContainer.translationX = slideFrom
        contentContainer.alpha = 0f

        val slideAnim = ObjectAnimator.ofFloat(contentContainer, "translationX", slideFrom, 0f)
        val fadeAnim = ObjectAnimator.ofFloat(contentContainer, "alpha", 0f, 1f)

        AnimatorSet().apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(slideAnim, fadeAnim)
            start()
        }

        // Bounce icon
        iconView.scaleX = 0.5f
        iconView.scaleY = 0.5f
        AnimatorSet().apply {
            duration = 500
            interpolator = OvershootInterpolator(1.5f)
            playTogether(
                ObjectAnimator.ofFloat(iconView, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(iconView, "scaleY", 0.5f, 1f)
            )
            startDelay = 100
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({ isAnimating = false }, 350)
    }

    private fun animateExit(reverse: Boolean, onComplete: () -> Unit) {
        val slideTo = if (reverse) 300f else -300f

        val slideAnim = ObjectAnimator.ofFloat(contentContainer, "translationX", 0f, slideTo)
        val fadeAnim = ObjectAnimator.ofFloat(contentContainer, "alpha", 1f, 0f)

        AnimatorSet().apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(slideAnim, fadeAnim)
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({ onComplete() }, 250)
    }

    private fun renderStep(step: Int) {
        currentStep = step
        stepIndicator.text = "Langkah $step dari $totalSteps"

        // Update progress dots dengan smooth color transition
        progressDots.forEachIndexed { index, dot ->
            val isActive = index < step
            dot.setBackgroundResource(
                if (isActive) R.drawable.dot_active else R.drawable.dot_inactive
            )
            // Pulse animation untuk dot yang current
            if (index == step - 1) {
                ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f).apply {
                    duration = 600
                    start()
                }
            }
        }

        configFields.visibility = View.GONE
        btnSecondary.visibility = View.VISIBLE

        when (step) {
            1 -> renderStepWelcome()
            2 -> renderStepPrivacy()
            3 -> renderStepConfig()
            4 -> renderStepPermission()
            5 -> renderStepComplete()
        }
    }

    private fun renderStepWelcome() {
        iconView.setImageResource(R.drawable.ic_step_welcome)
        titleView.text = "Selamat Datang!"
        descriptionView.text = "TemanQRIS Listener mengkonfirmasi pembayaran QRIS otomatis tanpa perlu cek manual."
        detailsView.text = "✓ Hemat waktu - tidak cek e-wallet manual\n" +
                "✓ Akurat - cocokkan otomatis dengan order\n" +
                "✓ Real-time - update status instant\n" +
                "✓ Privacy first - hanya baca notif e-wallet\n\n" +
                "📌 Setup ini wajib diselesaikan sebelum app bisa digunakan."

        btnPrimary.text = "Mulai Setup"
        btnPrimary.setOnClickListener { showStepWithAnimation(2) }
        btnSecondary.visibility = View.GONE
    }

    private fun renderStepPrivacy() {
        iconView.setImageResource(R.drawable.ic_step_privacy)
        titleView.text = "Privacy & Keamanan"
        descriptionView.text = "Penting untuk Anda paham APA yang dibaca dan TIDAK dibaca app ini."
        detailsView.text = "✅ YANG DIBACA:\n" +
                "• Notifikasi dari Gojek, DANA, OVO\n" +
                "• Hanya yang berisi info pembayaran masuk\n\n" +
                "❌ YANG TIDAK DIBACA:\n" +
                "• WhatsApp, SMS, Email pribadi\n" +
                "• Notifikasi bank, social media\n" +
                "• Data lain di HP Anda\n\n" +
                "🔒 Data dikirim ke server Anda sendiri dengan enkripsi HMAC-SHA256."

        btnPrimary.text = "Saya Mengerti, Lanjutkan"
        btnPrimary.setOnClickListener { showStepWithAnimation(3) }
        btnSecondary.text = "Kembali"
        btnSecondary.setOnClickListener { showStepWithAnimation(1, reverse = true) }
    }

    private fun renderStepConfig() {
        iconView.setImageResource(R.drawable.ic_step_config)
        titleView.text = "Konfigurasi Server"
        descriptionView.text = "Masukkan URL server dan device secret dari dashboard merchant Anda."
        detailsView.text = "Belum punya akun? Daftar dulu di dashboard merchant Anda untuk mendapatkan kredensial.\n\n" +
                "💡 Untuk testing, Anda bisa pakai mock server di laptop dengan URL http://[IP-LAPTOP]:3000"

        configFields.visibility = View.VISIBLE

        configFields.findViewById<EditText>(R.id.editServerUrl)
            .setText(prefs.getServerUrl() ?: "")
        configFields.findViewById<EditText>(R.id.editDeviceSecret)
            .setText(prefs.getDeviceSecret() ?: "")

        btnPrimary.text = "Simpan & Lanjutkan"
        btnPrimary.setOnClickListener {
            val url = configFields.findViewById<EditText>(R.id.editServerUrl)
                .text.toString().trim()
            val secret = configFields.findViewById<EditText>(R.id.editDeviceSecret)
                .text.toString().trim()

            // Validation ketat - tidak bisa skip
            if (url.isEmpty()) {
                shakeView(configFields.findViewById(R.id.editServerUrl))
                showError("Server URL wajib diisi untuk lanjut")
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                shakeView(configFields.findViewById(R.id.editServerUrl))
                showError("URL harus dimulai dengan http:// atau https://")
                return@setOnClickListener
            }
            if (secret.isEmpty()) {
                shakeView(configFields.findViewById(R.id.editDeviceSecret))
                showError("Device Secret wajib diisi untuk lanjut")
                return@setOnClickListener
            }
            if (secret.length < 8) {
                shakeView(configFields.findViewById(R.id.editDeviceSecret))
                showError("Device Secret minimal 8 karakter")
                return@setOnClickListener
            }

            prefs.setServerUrl(url.removeSuffix("/"))
            prefs.setDeviceSecret(secret)
            showStepWithAnimation(4)
        }
        btnSecondary.text = "Kembali"
        btnSecondary.setOnClickListener { showStepWithAnimation(2, reverse = true) }
    }

    private fun renderStepPermission() {
        iconView.setImageResource(R.drawable.ic_step_permission)
        titleView.text = "Izin Akses Notifikasi"
        descriptionView.text = "Android akan menampilkan peringatan keras karena permission ini sensitif. Ini NORMAL."

        if (isNotificationAccessGranted()) {
            detailsView.text = "✅ Permission sudah aktif!\n\nKlik lanjutkan untuk menyelesaikan setup."
            btnPrimary.text = "Lanjutkan"
            btnPrimary.setOnClickListener { showStepWithAnimation(5) }
            // Pulse hijau di icon
            ObjectAnimator.ofFloat(iconView, "rotation", 0f, 360f).apply {
                duration = 800
                start()
            }
        } else {
            detailsView.text = "Yang akan terjadi:\n\n" +
                    "1️⃣ Settings Android terbuka\n" +
                    "2️⃣ Cari 'TemanQRIS Listener'\n" +
                    "3️⃣ Aktifkan toggle\n" +
                    "4️⃣ Confirm 'Allow' di popup\n" +
                    "5️⃣ Otomatis kembali ke app\n\n" +
                    "⚠️ Tanpa permission ini, app tidak bisa berfungsi."

            btnPrimary.text = "Buka Settings Android"
            btnPrimary.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        btnSecondary.text = "Kembali"
        btnSecondary.setOnClickListener { showStepWithAnimation(3, reverse = true) }
    }

    private fun renderStepComplete() {
        iconView.setImageResource(R.drawable.ic_step_complete)
        titleView.text = "Setup Selesai! 🎉"
        descriptionView.text = "Listener akan otomatis aktif setelah Anda klik tombol di bawah."
        detailsView.text = "Yang akan terjadi:\n\n" +
                "• Customer scan QRIS Anda dan bayar\n" +
                "• Notifikasi pembayaran muncul\n" +
                "• App otomatis baca dan kirim ke server\n" +
                "• Order otomatis update jadi 'paid' ✓\n\n" +
                "Anda tetap bisa Pause kapan saja dari halaman utama."

        btnPrimary.text = "Aktifkan & Mulai Gunakan"
        btnPrimary.setOnClickListener {
            // WAJIB enable listener - sesuai keputusan strategi
            prefs.setOnboardingComplete(true)
            prefs.setListenerEnabled(true)
            com.temanqris.listener.ListenerKeepAliveService.start(this)

            // Transition dengan animasi
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
        btnSecondary.visibility = View.GONE
    }

    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, 20f, -20f, 20f, -20f, 10f, -10f, 0f).apply {
            duration = 500
            start()
        }
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, PaymentNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}
