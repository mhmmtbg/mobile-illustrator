package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/** "Geliştiriciye kahve ısmarla" satın almasının durumu. */
class CoffeeState(
    /** Mağazadan gelen fiyat metni (ör. "₺99,99"); henüz alınamadıysa `null`. */
    val price: String?,
    /** Satın alındı mı (reklamlar kapalı). */
    val purchased: Boolean,
)

/**
 * Arayüzün platforma bağlı işleri: dosya pencereleri, paylaşım, dil, satın alma ve reklam.
 * Android ve Windows sürümleri bunu kendi araçlarıyla uygular; ekranların geri kalanı ortaktır.
 */
interface PlatformHost {
    /** Dosya seçtirir; seçilen dosyanın adresi (Android'de içerik adresi, masaüstünde yol) verilir. */
    fun pickDocument(onPicked: (String) -> Unit)
    fun pickFont(onPicked: (String) -> Unit)
    fun pickImage(onPicked: (String) -> Unit)

    /** Kaydedilecek yeri seçtirir. */
    fun pickSaveLocation(suggestedName: String, onPicked: (String) -> Unit)

    /** Metni başka uygulamayla paylaşır (masaüstünde panoya kopyalar). */
    fun shareText(subject: String, text: String)

    /** Arayüz dilini Türkçe ile İngilizce arasında değiştirir ve ekranı yeniden kurar. */
    fun toggleLanguage()

    /** Küçük resim dosyasını okur; okunamazsa `null`. */
    fun loadThumbnail(file: File): ImageBitmap?

    /** "5 dakika önce" gibi göreli zaman metni. */
    fun relativeTime(millis: Long): String

    /** Satın alma bu platformda yoksa `null` (menüde gösterilmez). */
    val coffee: CoffeeState?

    /** Satın alma penceresini açar. */
    fun buyCoffee()

    /** Reklam şeridi; reklam gösterilmeyecekse hiçbir şey çizmez. */
    @Composable
    fun AdBanner(modifier: Modifier)

    /** Geri tuşu (Android) ya da Esc (masaüstü). */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)
}

/** Ton (0-360), doygunluk ve parlaklık (0-1) değerlerinden ARGB renk. */
fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = value - c
    fun channel(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

/** ARGB rengin tonu (0-360), doygunluğu ve parlaklığı (0-1). */
fun argbToHsv(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, if (max == 0f) 0f else delta / max, max)
}
