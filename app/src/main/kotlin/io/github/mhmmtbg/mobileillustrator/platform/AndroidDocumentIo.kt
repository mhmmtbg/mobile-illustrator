package io.github.mhmmtbg.mobileillustrator.platform

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import io.github.mhmmtbg.mobileillustrator.App
import io.github.mhmmtbg.mobileillustrator.editor.DocumentIo
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.model.tr
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import io.github.mhmmtbg.mobileillustrator.trace.PixelImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Android'de dosya işleri: adresler içerik adresidir (content://…) ve sistemin dosya seçicisinden gelir. */
class AndroidDocumentIo(private val app: Application) : DocumentIo(app.filesDir) {

    private val typefaces = HashMap<String, Typeface?>()

    init {
        // Yüklenen fontlar çizicide adlarıyla bulunur.
        DocumentRenderer.customFonts = ::typeface
    }

    private fun typeface(name: String): Typeface? = synchronized(typefaces) {
        typefaces.getOrPut(name) {
            val file = fonts.fileOf(name) ?: return@getOrPut null
            try { Typeface.createFromFile(file) } catch (e: RuntimeException) { null }
        }
    }

    override fun systemFonts(): List<Pair<String, String>> = DocumentRenderer.SYSTEM_FONTS

    override fun isValidFont(file: File): Boolean {
        val ok = Typeface.createFromFile(file) != null
        // Aynı adla yeniden yüklenen font eski biçimiyle kalmasın.
        synchronized(typefaces) { typefaces.remove(file.nameWithoutExtension) }
        return ok
    }

    override fun readBytes(address: String): ByteArray =
        app.contentResolver.openInputStream(Uri.parse(address))?.use { it.readBytes() } ?: throw IOException(tr("Dosya açılamadı"))

    override fun writeBytes(address: String, bytes: ByteArray) {
        val out = app.contentResolver.openOutputStream(Uri.parse(address), "wt") ?: throw IOException(tr("Dosya yazılamadı"))
        out.use { it.write(bytes) }
    }

    override fun assetBytes(path: String): ByteArray = app.assets.open(path).use { it.readBytes() }

    override fun displayName(address: String): String {
        val uri = Uri.parse(address)
        if (uri.scheme == "content") {
            try {
                app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
                }
            } catch (e: Exception) {
                // Bazı sağlayıcılar sorguyu desteklemez; yoldan türet.
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: tr("Adsız")
    }

    override fun persistAccess(address: String): Boolean {
        val uri = Uri.parse(address)
        if (uri.scheme != "content") return uri.scheme == "file"
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            app.contentResolver.takePersistableUriPermission(uri, rw)
            true
        } catch (e: Exception) {
            try {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e2: Exception) {
                // Kalıcı izin vermeyen sağlayıcı: bu oturum boyunca erişim sürer.
            }
            false
        }
    }

    override fun takeCrashReport(): String? {
        val f = App.crashFile(app)
        return if (f.length() > 0) f.readText().take(20_000).also { f.delete() } else null
    }

    override fun renderPng(doc: Document, maxSide: Int, transparent: Boolean): ByteArray {
        val box = doc.artboardBounds() ?: throw IOException(tr("Çalışma yüzeyi yok"))
        val longest = maxOf(box.width, box.height)
        val scale = minOf(maxSide / longest, 4.0).coerceAtLeast(0.001)
        val w = (box.width * scale).toInt().coerceAtLeast(1)
        val h = (box.height * scale).toInt().coerceAtLeast(1)
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw IOException(tr("Görsel için yeterli bellek yok"))
        }
        val canvas = Canvas(bmp)
        canvas.scale(scale.toFloat(), scale.toFloat())
        canvas.translate(-box.left.toFloat(), -box.top.toFloat())
        DocumentRenderer().draw(canvas, doc, artboardColor = if (transparent) null else 0xFFFFFFFF.toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    override fun decodePixels(image: ImageData, maxSide: Int): PixelImage {
        val bytes = image.bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException(tr("Görsel çözülemedi"))
        // Önce kabaca (ikinin katlarıyla), sonra tam istenen boyuta küçültülür.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException(tr("Görsel çözülemedi"))
        val k = minOf(1.0, maxSide.toDouble() / maxOf(decoded.width, decoded.height))
        val w = (decoded.width * k).toInt().coerceAtLeast(1)
        val h = (decoded.height * k).toInt().coerceAtLeast(1)
        val scaled = if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
        val argb = IntArray(w * h)
        scaled.getPixels(argb, 0, w, 0, 0, w, h)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return PixelImage(w, h, argb)
    }

    override fun decodeImage(bytes: ByteArray): ImageData {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException(tr("Bu dosya bir görsel değil"))
        if (bounds.outMimeType == "image/jpeg") return ImageData(bytes, "image/jpeg", bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample * sample) > 24_000_000L) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException(tr("Görsel çözülemedi"))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val data = ImageData(out.toByteArray(), "image/png", bmp.width, bmp.height)
        bmp.recycle()
        return data
    }
}
