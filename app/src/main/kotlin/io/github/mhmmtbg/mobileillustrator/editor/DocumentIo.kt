package io.github.mhmmtbg.mobileillustrator.editor

import android.app.Application
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
import java.io.File
import java.io.IOException

/** Dosya okuma ve yazma. Tüm işlevler arka plan iş parçacığında çağrılmalıdır. */
class DocumentIo(private val app: Application) {

    private val autosaveFile get() = File(app.filesDir, "autosave.ai")
    private val autosaveName get() = File(app.filesDir, "autosave.name")

    private val exportOptions = AiExporter.Options(outlineText = { TextOutliner.outline(it) })

    fun open(uri: Uri): ImportResult {
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

    fun openAsset(path: String, name: String): ImportResult =
        parse(app.assets.open(path).use { it.readBytes() }, "$name.ai")

    private fun parse(bytes: ByteArray, fileName: String): ImportResult {
        val base = fileName.substringBeforeLast('.').ifBlank { "Adsız" }
        val looksSvg = fileName.endsWith(".svg", ignoreCase = true) || SvgImporter.sniff(bytes)
        try {
            return if (looksSvg) SvgImporter.import(bytes, base) else AiImporter.import(bytes, base)
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

    fun export(doc: Document, uri: Uri, format: ExportFormat) {
        val bytes = when (format) {
            ExportFormat.Ai, ExportFormat.Pdf -> AiExporter.export(doc, exportOptions)
            ExportFormat.Svg -> SvgExporter.export(doc).toByteArray(Charsets.UTF_8)
            ExportFormat.Png -> renderPng(doc)
        }
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Dosya yazılamadı")
        out.use { it.write(bytes) }
    }

    /** Çalışma yüzeylerini saydam zeminli PNG olarak çizer; uzun kenar en çok 4096 piksel olur. */
    private fun renderPng(doc: Document): ByteArray {
        val box = doc.artboardBounds() ?: throw IOException("Çalışma yüzeyi yok")
        val longest = maxOf(box.width, box.height)
        val scale = minOf(4096.0 / longest, 4.0).coerceAtLeast(0.01)
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
        DocumentRenderer().draw(canvas, doc, artboardColor = null)
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

    // ---- Otomatik kayıt ---------------------------------------------------

    fun hasAutosave(): Boolean = autosaveFile.length() > 0

    @Synchronized
    fun autosave(doc: Document) {
        val tmp = File(app.filesDir, "autosave.tmp")
        tmp.writeBytes(AiExporter.export(doc, exportOptions))
        if (!tmp.renameTo(autosaveFile)) {
            autosaveFile.delete()
            tmp.renameTo(autosaveFile)
        }
        autosaveName.writeText(doc.name)
    }

    @Synchronized
    fun loadAutosave(): Document {
        val name = try { autosaveName.readText().ifBlank { "Adsız" } } catch (e: IOException) { "Adsız" }
        return AiImporter.import(autosaveFile.readBytes(), name).document
    }
}
