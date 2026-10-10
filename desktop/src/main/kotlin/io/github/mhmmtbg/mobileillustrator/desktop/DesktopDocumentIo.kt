package io.github.mhmmtbg.mobileillustrator.desktop

import io.github.mhmmtbg.mobileillustrator.editor.DocumentIo
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.model.tr
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import io.github.mhmmtbg.mobileillustrator.trace.PixelImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.io.File
import java.io.IOException

/** Masaüstünde dosya işleri: adresler dosya yoludur. */
class DesktopDocumentIo(private val root: File) : DocumentIo(root) {

    private val typefaces = HashMap<String, Typeface?>()

    init {
        root.mkdirs()
        // Yüklenen fontlar çizicide adlarıyla bulunur.
        DocumentRenderer.customFonts = ::typeface
    }

    private fun typeface(name: String): Typeface? = synchronized(typefaces) {
        typefaces.getOrPut(name) {
            val file = fonts.fileOf(name) ?: return@getOrPut null
            try { FontMgr.default.makeFromFile(file.path) } catch (e: RuntimeException) { null }
        }
    }

    override fun isValidFont(file: File): Boolean {
        val ok = FontMgr.default.makeFromFile(file.path) != null
        synchronized(typefaces) { typefaces.remove(file.nameWithoutExtension) }
        return ok
    }

    override fun readBytes(address: String): ByteArray {
        val file = File(address)
        if (!file.isFile) throw IOException(tr("Dosya açılamadı"))
        return file.readBytes()
    }

    override fun writeBytes(address: String, bytes: ByteArray) {
        // Önce geçici dosyaya yazılır; yarıda kesilirse eski dosya bozulmaz.
        val target = File(address)
        val tmp = File(target.parentFile ?: File("."), target.name + ".tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IOException(tr("Dosya yazılamadı"))
            }
        } finally {
            tmp.delete()
        }
    }

    override fun displayName(address: String): String = File(address).name.ifBlank { tr("Adsız") }

    override fun assetBytes(path: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() } ?: throw IOException(tr("Dosya açılamadı"))

    override fun persistAccess(address: String): Boolean = File(address).let { it.canWrite() || !it.exists() }

    val crashFile: File get() = File(root, "last-crash.txt")

    override fun takeCrashReport(): String? {
        val f = crashFile
        return if (f.length() > 0) f.readText().take(20_000).also { f.delete() } else null
    }

    override fun renderPng(doc: Document, maxSide: Int, transparent: Boolean): ByteArray {
        val box = doc.artboardBounds() ?: throw IOException(tr("Çalışma yüzeyi yok"))
        val longest = maxOf(box.width, box.height)
        val scale = minOf(maxSide / longest, 4.0).coerceAtLeast(0.001)
        val w = (box.width * scale).toInt().coerceAtLeast(1)
        val h = (box.height * scale).toInt().coerceAtLeast(1)
        val surface = try {
            Surface.makeRasterN32Premul(w, h)
        } catch (e: Throwable) {
            throw IOException(tr("Görsel için yeterli bellek yok"))
        }
        try {
            val canvas = surface.canvas
            canvas.scale(scale.toFloat(), scale.toFloat())
            canvas.translate(-box.left.toFloat(), -box.top.toFloat())
            DocumentRenderer().draw(canvas, doc, artboardColor = if (transparent) null else 0xFFFFFFFF.toInt())
            return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes ?: throw IOException(tr("Görsel çözülemedi"))
        } finally {
            surface.close()
        }
    }

    override fun decodePixels(image: ImageData, maxSide: Int): PixelImage {
        val decoded = try { Image.makeFromEncoded(image.bytes) } catch (e: Throwable) { null } ?: throw IOException(tr("Görsel çözülemedi"))
        val k = minOf(1.0, maxSide.toDouble() / maxOf(decoded.width, decoded.height))
        val w = (decoded.width * k).toInt().coerceAtLeast(1)
        val h = (decoded.height * k).toInt().coerceAtLeast(1)
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
        val pixmap = bitmap.peekPixels() ?: throw IOException(tr("Görsel çözülemedi"))
        decoded.scalePixels(pixmap, SamplingMode.LINEAR, false)
        val bytes = bitmap.readPixels() ?: throw IOException(tr("Görsel çözülemedi"))
        bitmap.close()
        val argb = IntArray(w * h) { i ->
            val b = bytes[i * 4].toInt() and 0xFF
            val g = bytes[i * 4 + 1].toInt() and 0xFF
            val r = bytes[i * 4 + 2].toInt() and 0xFF
            val a = bytes[i * 4 + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return PixelImage(w, h, argb)
    }

    override fun decodeImage(bytes: ByteArray): ImageData {
        val image = try { Image.makeFromEncoded(bytes) } catch (e: Throwable) { null } ?: throw IOException(tr("Bu dosya bir görsel değil"))
        val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        if (isJpeg) return ImageData(bytes, "image/jpeg", image.width, image.height)
        // Çok büyük görseller bellek için küçültülür.
        var w = image.width
        var h = image.height
        while (w.toLong() * h > 24_000_000L) { w /= 2; h /= 2 }
        val png = if (w == image.width) {
            image.encodeToData(EncodedImageFormat.PNG)?.bytes
        } else {
            val surface = Surface.makeRasterN32Premul(w, h)
            surface.canvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
            surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes.also { surface.close() }
        } ?: throw IOException(tr("Görsel çözülemedi"))
        return ImageData(png, "image/png", w, h)
    }

    companion object {
        /** Uygulamanın veri klasörü: Windows'ta %APPDATA%, diğerlerinde kullanıcının ev klasörü altında. */
        fun defaultRoot(): File {
            System.getProperty("mobileillustrator.data")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            val base = if (appData != null) File(appData) else File(System.getProperty("user.home"), ".local/share")
            return File(base, "MobileIllustrator")
        }
    }
}
