package io.github.mhmmtbg.mobileillustrator.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.mhmmtbg.mobileillustrator.model.L10n
import io.github.mhmmtbg.mobileillustrator.model.tr
import io.github.mhmmtbg.mobileillustrator.ui.CoffeeState
import io.github.mhmmtbg.mobileillustrator.ui.PlatformHost
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Arayüzün masaüstüne özgü işleri: sistemin dosya pencereleri, pano, dil. Satın alma ve reklam yoktur. */
class DesktopHost(private val root: File, private val window: () -> Frame?) : PlatformHost {

    /** Dil değişince artar; ekran bu değere bağlı olarak yeniden kurulur. */
    var languageVersion by mutableIntStateOf(0)
        private set

    /** Esc ile kapatılacak açık panel (varsa). */
    var onBack: (() -> Unit)? = null
        private set

    private var lastDirectory: String? = null

    private fun choose(title: String, save: Boolean, suggested: String? = null, extensions: List<String>? = null): String? {
        val dialog = FileDialog(window(), title, if (save) FileDialog.SAVE else FileDialog.LOAD)
        lastDirectory?.let { dialog.directory = it }
        if (suggested != null) dialog.file = suggested
        if (extensions != null) {
            // Windows dosya penceresi süzgeci dosya adı kalıbından, diğerleri FilenameFilter'dan alır.
            if (!save) dialog.file = extensions.joinToString(";") { "*.$it" }
            dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        lastDirectory = dialog.directory
        return File(dialog.directory, file).path
    }

    override fun pickDocument(onPicked: (String) -> Unit) {
        choose(tr("Aç… (.ai, .pdf, .svg)"), save = false, extensions = listOf("ai", "pdf", "svg"))?.let(onPicked)
    }

    override fun pickFont(onPicked: (String) -> Unit) {
        choose(tr("Font yükle (.ttf, .otf)…"), save = false, extensions = listOf("ttf", "otf"))?.let(onPicked)
    }

    override fun pickImage(onPicked: (String) -> Unit) {
        choose(tr("Görsel yerleştir…"), save = false, extensions = listOf("png", "jpg", "jpeg", "webp", "gif", "bmp"))?.let(onPicked)
    }

    override fun pickSaveLocation(suggestedName: String, onPicked: (String) -> Unit) {
        val picked = choose(tr("Kaydet"), save = true, suggested = suggestedName) ?: return
        // Uzantı yazılmadıysa önerilen addaki uzantı eklenir.
        val extension = suggestedName.substringAfterLast('.', "")
        onPicked(if (extension.isNotEmpty() && !File(picked).name.contains('.')) "$picked.$extension" else picked)
    }

    override fun shareText(subject: String, text: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("$subject\n\n$text"), null)
        } catch (e: Exception) {
            // pano kullanılamıyor
        }
    }

    override fun toggleLanguage() {
        L10n.english = !L10n.english
        try { File(root, "language").writeText(if (L10n.english) "en" else "tr") } catch (e: Exception) { /* bu oturum için geçerli olur */ }
        languageVersion++
    }

    override fun loadThumbnail(file: File): ImageBitmap? =
        if (file.isFile) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() else null

    override fun relativeTime(millis: Long): String {
        val minutes = (System.currentTimeMillis() - millis) / 60_000
        return when {
            minutes < 1 -> tr("az önce")
            minutes < 60 -> tr("%s dk önce", minutes)
            minutes < 24 * 60 -> tr("%s sa önce", minutes / 60)
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
        }
    }

    override val coffee: CoffeeState? get() = null

    override fun buyCoffee() {}

    @Composable
    override fun AdBanner(modifier: Modifier) {
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        DisposableEffect(enabled, onBack) {
            if (enabled) this@DesktopHost.onBack = onBack
            onDispose { if (this@DesktopHost.onBack === onBack) this@DesktopHost.onBack = null }
        }
    }

    companion object {
        /** Arayüz dili: kullanıcı seçtiyse o, seçmediyse bilgisayarın dili (Türkçe değilse İngilizce). */
        fun applyLanguage(root: File) {
            val chosen = try { File(root, "language").readText().trim() } catch (e: Exception) { "" }
            L10n.english = when (chosen) {
                "tr" -> false
                "en" -> true
                else -> java.util.Locale.getDefault().language != "tr"
            }
        }
    }
}
