package io.github.mhmmtbg.mobileillustrator.editor

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.provider.OpenableColumns
import io.github.mhmmtbg.mobileillustrator.ai.AiExporter
import io.github.mhmmtbg.mobileillustrator.ai.AiImporter
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImportException
import io.github.mhmmtbg.mobileillustrator.model.ImportResult
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import io.github.mhmmtbg.mobileillustrator.render.TextOutliner
import io.github.mhmmtbg.mobileillustrator.svg.SvgExporter
import io.github.mhmmtbg.mobileillustrator.svg.SvgImporter
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Dosya okuma ve yazma. Tüm işlevler arka plan iş parçacığında çağrılmalıdır. */
class DocumentIo(private val app: Application) {

    val store = DocumentStore(app.filesDir)
    val fonts = FontStore(app.filesDir)

    /** Seçilen font dosyasını uygulamaya yükler; fontun adını döndürür. */
    fun importFont(uri: Uri): String {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Dosya açılamadı")
        return fonts.add(displayName(uri), bytes)
    }

    private val exportOptions = AiExporter.Options(outlineText = { TextOutliner.outline(it) })

    class Opened(val result: ImportResult, val format: ExportFormat)

    fun open(uri: Uri): Opened {
        val name = displayName(uri)
        val bytes = try {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: SecurityException) {
            throw IOException("Dosyaya erişim izni yok")
        } catch (e: OutOfMemoryError) {
            throw IOException("Dosya belleğe sığmayacak kadar büyük")
        } ?: throw IOException("Dosya açılamadı")
        return parse(bytes, name)
    }

    fun openAsset(path: String, name: String): Opened = parse(app.assets.open(path).use { it.readBytes() }, "$name.ai")

    private fun parse(bytes: ByteArray, fileName: String): Opened {
        val base = fileName.substringBeforeLast('.').ifBlank { "Adsız" }
        val looksSvg = fileName.endsWith(".svg", ignoreCase = true) || SvgImporter.sniff(bytes)
        try {
            return if (looksSvg) {
                Opened(SvgImporter.import(bytes, base), ExportFormat.Svg)
            } else {
                Opened(AiImporter.import(bytes, base), if (fileName.endsWith(".pdf", ignoreCase = true)) ExportFormat.Pdf else ExportFormat.Ai)
            }
        } catch (e: ImportException) {
            throw IOException(e.message)
        } catch (e: OutOfMemoryError) {
            throw IOException("Dosya belleğe sığmayacak kadar karmaşık")
        } catch (e: StackOverflowError) {
            throw IOException("Dosya çok derin iç içe yapı içeriyor")
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
                }
            } catch (e: Exception) {
                // Bazı sağlayıcılar sorguyu desteklemez; yoldan türet.
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Adsız"
    }

    /**
     * Dosyaya kalıcı erişim ister; böylece uygulama yeniden açıldığında da "Kaydet" çalışır.
     * @return yazma izni de alınabildiyse `true`.
     */
    fun persistAccess(uri: Uri): Boolean {
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

    fun encode(doc: Document, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.Ai, ExportFormat.Pdf -> AiExporter.export(doc, exportOptions)
        ExportFormat.Svg -> SvgExporter.export(doc).toByteArray(Charsets.UTF_8)
        ExportFormat.Png -> renderPng(doc, 4096, transparent = true)
    }

    fun export(doc: Document, uri: Uri, format: ExportFormat) {
        val bytes = encode(doc, format)
        val out = try {
            app.contentResolver.openOutputStream(uri, "wt")
        } catch (e: SecurityException) {
            throw IOException("Dosyaya yazma izni yok")
        } catch (e: java.io.FileNotFoundException) {
            throw IOException("Dosya artık yerinde değil")
        } ?: throw IOException("Dosya yazılamadı")
        out.use { it.write(bytes) }
    }

    /** Çalışma yüzeylerini PNG olarak çizer; uzun kenar en çok [maxSide] piksel olur. */
    private fun renderPng(doc: Document, maxSide: Int, transparent: Boolean): ByteArray {
        val box = doc.artboardBounds() ?: throw IOException("Çalışma yüzeyi yok")
        val longest = maxOf(box.width, box.height)
        val scale = minOf(maxSide / longest, 4.0).coerceAtLeast(0.001)
        val w = (box.width * scale).toInt().coerceAtLeast(1)
        val h = (box.height * scale).toInt().coerceAtLeast(1)
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw IOException("Görsel için yeterli bellek yok")
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

    /** Galeriden seçilen görseli okur. JPEG olduğu gibi kalır; diğerleri PNG'ye çevrilir. */
    fun readImage(uri: Uri): ImageData {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Görsel açılamadı")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Bu dosya bir görsel değil")
        if (bounds.outMimeType == "image/jpeg") return ImageData(bytes, "image/jpeg", bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample * sample) > 24_000_000L) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Görsel çözülemedi")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val data = ImageData(out.toByteArray(), "image/png", bmp.width, bmp.height)
        bmp.recycle()
        return data
    }

    // ---- Uygulama içi belge deposu -----------------------------------------

    /** Belgeyi depoya yazar (otomatik kayıt). Küçük resim de yenilenir. */
    fun saveToStore(meta: StoredDocument, doc: Document) {
        val thumb = try { renderPng(doc, 360, transparent = false) } catch (e: Throwable) { null }
        store.write(meta.copy(name = doc.name, modified = System.currentTimeMillis()), AiExporter.export(doc, exportOptions), thumb)
    }

    fun loadFromStore(id: String): Document {
        val meta = store.read(id) ?: throw IOException("Belge bulunamadı")
        try {
            return AiImporter.import(store.readData(id), meta.name).document
        } catch (e: ImportException) {
            throw IOException(e.message)
        }
    }
}
