package com.temanqris.listener.parser

import android.util.Log

/**
 * Parser untuk extract informasi pembayaran dari notifikasi e-wallet.
 *
 * Strategy: Per-app parser dengan regex patterns yang spesifik.
 * Setiap parser mengembalikan PaymentNotification atau null jika
 * notifikasi bukan transaksi penerimaan dana.
 */
object PaymentNotificationParser {

    private const val TAG = "PaymentParser"

    // Package name dari masing-masing app
    private val GOJEK_PACKAGES = setOf(
        "com.gojek.app",
        "com.gojek.gopay"
    )
    private val DANA_PACKAGES = setOf(
        "id.dana"
    )
    private val OVO_PACKAGES = setOf(
        "ovo.id"
    )

    /**
     * Entry point: route notifikasi ke parser yang tepat berdasarkan package name.
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
        postedAt: Long
    ): PaymentNotification? {
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null

        // Combine semua field text supaya tidak miss informasi
        val fullText = listOfNotNull(title, text, bigText, subText)
            .joinToString(" | ")
            .trim()

        Log.d(TAG, "Parsing [$packageName]: $fullText")

        return when (packageName) {
            in GOJEK_PACKAGES -> GojekParser.parse(fullText, postedAt)
            in DANA_PACKAGES -> DanaParser.parse(fullText, postedAt)
            in OVO_PACKAGES -> OvoParser.parse(fullText, postedAt)
            else -> null
        }
    }
}

/**
 * Data class untuk hasil parsing.
 */
data class PaymentNotification(
    val source: PaymentSource,
    val amount: Long,           // dalam Rupiah (long, bukan double)
    val payerName: String?,     // nama pengirim, null kalau tidak terbaca
    val rawText: String,        // teks asli untuk debugging
    val postedAt: Long,         // timestamp dari notifikasi
    val confidence: Float       // 0.0 - 1.0, tingkat kepercayaan parsing
)

enum class PaymentSource {
    GOJEK, DANA, OVO
}

/**
 * Helper untuk normalisasi format Rupiah.
 * Handle: "Rp50.000", "Rp 50.000", "Rp50,000", "IDR 50000", "Rp50.000,00"
 */
object AmountExtractor {

    // Regex untuk capture angka rupiah dalam berbagai format
    private val AMOUNT_PATTERN = Regex(
        """(?:Rp\.?|IDR)\s?([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(text: String): Long? {
        val match = AMOUNT_PATTERN.find(text) ?: return null
        val raw = match.groupValues[1]

        // Handle format Indonesia (.000) dan International (,000)
        // Strategy: hilangkan semua separator yang bukan posisi desimal
        val cleaned = when {
            // Format "50.000,00" - desimal pakai koma
            raw.contains(",") && raw.lastIndexOf(",") > raw.lastIndexOf(".") -> {
                raw.substringBeforeLast(",").replace(".", "").replace(",", "")
            }
            // Format "50,000.00" - desimal pakai titik (less common di ID)
            raw.contains(".") && raw.count { it == '.' } == 1 && raw.length - raw.lastIndexOf(".") <= 3 -> {
                // Cek apakah ini desimal atau separator ribuan
                val afterDot = raw.substringAfterLast(".")
                if (afterDot.length <= 2 && raw.contains(",")) {
                    raw.substringBeforeLast(".").replace(",", "")
                } else {
                    raw.replace(".", "").replace(",", "")
                }
            }
            // Format standar Indonesia "50.000"
            else -> raw.replace(".", "").replace(",", "")
        }

        return cleaned.toLongOrNull()
    }
}

/**
 * Parser untuk Gojek/GoPay.
 *
 * Format notifikasi yang umum (perlu dikonfirmasi dengan data real):
 *  - "GoPay" / "Anda menerima Rp50.000 dari Budi Santoso"
 *  - "Pembayaran QRIS" / "Pembayaran sebesar Rp50.000 berhasil dari Budi"
 *  - "Saldo bertambah" / "Rp50.000 masuk ke GoPay kamu dari Budi"
 */
object GojekParser {

    // Pattern indicator untuk transaksi MASUK (received)
    private val INCOMING_PATTERNS = listOf(
        Regex("""anda menerima""", RegexOption.IGNORE_CASE),
        Regex("""terima.*pembayaran""", RegexOption.IGNORE_CASE),
        Regex("""pembayaran.*berhasil""", RegexOption.IGNORE_CASE),
        Regex("""saldo bertambah""", RegexOption.IGNORE_CASE),
        Regex("""masuk ke gopay""", RegexOption.IGNORE_CASE),
        Regex("""dana masuk""", RegexOption.IGNORE_CASE),
        Regex("""qris.*berhasil""", RegexOption.IGNORE_CASE)
    )

    // Pattern yang menandakan ini BUKAN penerimaan dana
    private val EXCLUSION_PATTERNS = listOf(
        Regex("""kamu (mengirim|membayar|membeli)""", RegexOption.IGNORE_CASE),
        Regex("""pembayaran.*ke""", RegexOption.IGNORE_CASE),
        Regex("""cashback""", RegexOption.IGNORE_CASE),
        Regex("""promo""", RegexOption.IGNORE_CASE),
        Regex("""voucher""", RegexOption.IGNORE_CASE),
        Regex("""tarik tunai""", RegexOption.IGNORE_CASE),
        Regex("""transfer ke""", RegexOption.IGNORE_CASE)
    )

    // Pattern untuk extract nama pengirim
    private val PAYER_PATTERNS = listOf(
        Regex("""dari\s+([A-Z][A-Za-z0-9\s.]{2,40}?)(?:\.|,|$|\s+(?:saldo|sebesar))""", RegexOption.IGNORE_CASE),
        Regex("""pembayaran.*dari\s+([A-Z][A-Za-z0-9\s.]{2,40}?)(?:\.|,|$)""", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String, postedAt: Long): PaymentNotification? {
        // Cek exclusion dulu (priority tinggi)
        if (EXCLUSION_PATTERNS.any { it.containsMatchIn(text) }) {
            Log.d("GojekParser", "Excluded: $text")
            return null
        }

        // Harus ada minimal 1 incoming pattern
        val incomingMatch = INCOMING_PATTERNS.any { it.containsMatchIn(text) }
        if (!incomingMatch) {
            Log.d("GojekParser", "No incoming pattern matched: $text")
            return null
        }

        // Extract amount
        val amount = AmountExtractor.extract(text) ?: run {
            Log.d("GojekParser", "No amount found: $text")
            return null
        }

        // Extract nama pembayar (best effort)
        val payerName = PAYER_PATTERNS.firstNotNullOfOrNull {
            it.find(text)?.groupValues?.getOrNull(1)?.trim()
        }

        // Confidence scoring
        val confidence = calculateConfidence(text, payerName != null)

        return PaymentNotification(
            source = PaymentSource.GOJEK,
            amount = amount,
            payerName = payerName,
            rawText = text,
            postedAt = postedAt,
            confidence = confidence
        )
    }

    private fun calculateConfidence(text: String, hasPayerName: Boolean): Float {
        var score = 0.5f
        if (text.contains("QRIS", ignoreCase = true)) score += 0.2f
        if (text.contains("berhasil", ignoreCase = true)) score += 0.1f
        if (hasPayerName) score += 0.15f
        if (text.contains("menerima", ignoreCase = true)) score += 0.05f
        return score.coerceAtMost(1.0f)
    }
}

/**
 * Parser untuk DANA.
 *
 * Format notifikasi DANA umumnya:
 *  - "DANA Masuk!" / "Saldo DANA kamu bertambah Rp50.000 dari Budi"
 *  - "Pembayaran Diterima" / "Rp50.000 diterima dari Budi via QRIS"
 *  - "Transaksi Berhasil" / "Anda menerima Rp50.000"
 */
object DanaParser {

    private val INCOMING_PATTERNS = listOf(
        Regex("""dana masuk""", RegexOption.IGNORE_CASE),
        Regex("""saldo.*bertambah""", RegexOption.IGNORE_CASE),
        Regex("""pembayaran diterima""", RegexOption.IGNORE_CASE),
        Regex("""anda menerima""", RegexOption.IGNORE_CASE),
        Regex("""diterima dari""", RegexOption.IGNORE_CASE),
        Regex("""transaksi berhasil""", RegexOption.IGNORE_CASE),
        Regex("""qris.*sukses""", RegexOption.IGNORE_CASE)
    )

    private val EXCLUSION_PATTERNS = listOf(
        Regex("""saldo.*berkurang""", RegexOption.IGNORE_CASE),
        Regex("""kamu (mengirim|bayar|membayar)""", RegexOption.IGNORE_CASE),
        Regex("""pengeluaran""", RegexOption.IGNORE_CASE),
        Regex("""cashback""", RegexOption.IGNORE_CASE),
        Regex("""promo""", RegexOption.IGNORE_CASE),
        Regex("""top.?up""", RegexOption.IGNORE_CASE)
    )

    private val PAYER_PATTERNS = listOf(
        Regex("""dari\s+([A-Z][A-Za-z0-9\s.]{2,40}?)(?:\.|,|$|\s+via)""", RegexOption.IGNORE_CASE),
        Regex("""diterima dari\s+([A-Z][A-Za-z0-9\s.]{2,40}?)(?:\.|,|$)""", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String, postedAt: Long): PaymentNotification? {
        if (EXCLUSION_PATTERNS.any { it.containsMatchIn(text) }) {
            Log.d("DanaParser", "Excluded: $text")
            return null
        }
        if (!INCOMING_PATTERNS.any { it.containsMatchIn(text) }) return null

        val amount = AmountExtractor.extract(text) ?: return null
        val payerName = PAYER_PATTERNS.firstNotNullOfOrNull {
            it.find(text)?.groupValues?.getOrNull(1)?.trim()
        }

        val confidence = run {
            var score = 0.5f
            if (text.contains("QRIS", ignoreCase = true)) score += 0.2f
            if (text.contains("diterima", ignoreCase = true)) score += 0.1f
            if (payerName != null) score += 0.15f
            score.coerceAtMost(1.0f)
        }

        return PaymentNotification(
            source = PaymentSource.DANA,
            amount = amount,
            payerName = payerName,
            rawText = text,
            postedAt = postedAt,
            confidence = confidence
        )
    }
}

/**
 * Parser untuk OVO.
 *
 * Format notifikasi OVO umumnya:
 *  - "OVO" / "Anda menerima pembayaran QRIS sebesar Rp50.000 dari Budi"
 *  - "Pembayaran Berhasil" / "Rp50.000 dari Budi melalui QRIS"
 *  - "Saldo OVO Cash bertambah Rp50.000"
 */
object OvoParser {

    private val INCOMING_PATTERNS = listOf(
        Regex("""anda menerima""", RegexOption.IGNORE_CASE),
        Regex("""menerima pembayaran""", RegexOption.IGNORE_CASE),
        Regex("""pembayaran berhasil""", RegexOption.IGNORE_CASE),
        Regex("""ovo cash bertambah""", RegexOption.IGNORE_CASE),
        Regex("""dana masuk""", RegexOption.IGNORE_CASE),
        Regex("""qris.*berhasil""", RegexOption.IGNORE_CASE)
    )

    private val EXCLUSION_PATTERNS = listOf(
        Regex("""kamu (membayar|mengirim|bayar)""", RegexOption.IGNORE_CASE),
        Regex("""pembayaran ke""", RegexOption.IGNORE_CASE),
        Regex("""ovo points""", RegexOption.IGNORE_CASE),
        Regex("""cashback""", RegexOption.IGNORE_CASE),
        Regex("""promo""", RegexOption.IGNORE_CASE),
        Regex("""top.?up""", RegexOption.IGNORE_CASE),
        Regex("""saldo.*berkurang""", RegexOption.IGNORE_CASE)
    )

    private val PAYER_PATTERNS = listOf(
        Regex("""dari\s+([A-Z][A-Za-z0-9\s.]{2,40}?)(?:\.|,|$|\s+(?:melalui|via))""", RegexOption.IGNORE_CASE)
    )

    fun parse(text: String, postedAt: Long): PaymentNotification? {
        if (EXCLUSION_PATTERNS.any { it.containsMatchIn(text) }) return null
        if (!INCOMING_PATTERNS.any { it.containsMatchIn(text) }) return null

        val amount = AmountExtractor.extract(text) ?: return null
        val payerName = PAYER_PATTERNS.firstNotNullOfOrNull {
            it.find(text)?.groupValues?.getOrNull(1)?.trim()
        }

        val confidence = run {
            var score = 0.5f
            if (text.contains("QRIS", ignoreCase = true)) score += 0.2f
            if (text.contains("berhasil", ignoreCase = true)) score += 0.1f
            if (payerName != null) score += 0.15f
            score.coerceAtMost(1.0f)
        }

        return PaymentNotification(
            source = PaymentSource.OVO,
            amount = amount,
            payerName = payerName,
            rawText = text,
            postedAt = postedAt,
            confidence = confidence
        )
    }
}
