package io.github.mhmmtbg.mobileillustrator.editor

import io.github.mhmmtbg.mobileillustrator.ai.AiExporter
import io.github.mhmmtbg.mobileillustrator.ai.AiImporter
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImportException
import io.github.mhmmtbg.mobileillustrator.model.ImportResult
import io.github.mhmmtbg.mobileillustrator.model.tr
import io.github.mhmmtbg.mobileillustrator.render.TextOutliner
import io.github.mhmmtbg.mobileillustrator.svg.SvgExporter
import io.github.mhmmtbg.mobileillustrator.trace.PixelImage
import io.github.mhmmtbg.mobileillustrator.svg.SvgImporter
import java.io.File
import java.io.IOException

/**
 * Dosya okuma ve yazma. Tüm işlevler arka plan iş parçacığında çağrılmalıdır.
 *
 * Dosyalar bir "adres" metniyle anılır: Android'de içerik adresi (content://…), masaüstünde dosya yolu.
 * Adresin nasıl okunup yazılacağını platform sınıfı bilir; biçim dönüştürme burada ortaktır.
 *
 * @param root uygulamanın kendi veri klasörü (belge deposu ve yüklenen fontlar burada durur).
 */
abstract class DocumentIo(root: File) {

    val store = DocumentStore(root)
    val fonts = FontStore(root)

    private val exportOptions = AiExporter.Options(outlineText = { TextOutliner.outline(it) })

    class Opened(val result: ImportResult, val format: ExportFormat)

    // ---- Platformun sağladıkları --------------------------------------------

    protected abstract fun readBytes(address: String): ByteArray
    protected abstract fun writeBytes(address: String, bytes: ByteArray)

    /** Adresin kullanıcıya gösterilecek dosya adı (uzantısıyla). */
    protected abstract fun displayName(address: String): String

    /** Uygulamayla birlikte gelen dosya (örnek belge). */
    protected abstract fun assetBytes(path: String): ByteArray

    /** Çalışma yüzeylerini PNG olarak çizer; uzun kenar en çok [maxSide] piksel olur. */
    protected abstract fun renderPng(doc: Document, maxSide: Int, transparent: Boolean): ByteArray

    /** Görsel dosyasını okur. JPEG olduğu gibi kalır; diğerleri PNG'ye çevrilir. */
    protected abstract fun decodeImage(bytes: ByteArray): ImageData

    /**
     * Görseli piksellerine çözer (vektöre çevirmek için); uzun kenar [maxSide] pikseli geçmeyecek biçimde küçültülür.
     */
    abstract fun decodePixels(image: ImageData, maxSide: Int): PixelImage

    /** Bu cihazda seçilebilen hazır fontlar: görünen ad ve metin düğümüne yazılacak aile adı. */
    abstract fun systemFonts(): List<Pair<String, String>>

    /** Dosya bu platformda font olarak açılabiliyor mu. */
    protected abstract fun isValidFont(file: File): Boolean

    /**
     * Dosyaya kalıcı erişim ister; böylece uygulama yeniden açıldığında da "Kaydet" çalışır.
     * @return yazma izni de alınabildiyse `true`.
     */
    abstract fun persistAccess(address: String): Boolean

    /** Önceki açılış çökmeyle bittiyse kaydını verir ve siler (bir kez gösterilir). */
    abstract fun takeCrashReport(): String?

    // ---- Ortak işler ---------------------------------------------------------

    /** Seçilen font dosyasını uygulamaya yükler; fontun adını döndürür. */
    fun importFont(address: String): String = fonts.add(displayName(address), readBytes(address), ::isValidFont)

    fun open(address: String): Opened {
        val name = displayName(address)
        val bytes = try {
            readBytes(address)
        } catch (e: SecurityException) {
            throw IOException(tr("Dosyaya erişim izni yok"))
        } catch (e: OutOfMemoryError) {
            throw IOException(tr("Dosya belleğe sığmayacak kadar büyük"))
        }
        return parse(bytes, name)
    }

    fun openAsset(path: String, name: String): Opened = parse(assetBytes(path), "$name.ai")

    private fun parse(bytes: ByteArray, fileName: String): Opened {
        val base = fileName.substringBeforeLast('.').ifBlank { tr("Adsız") }
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
            throw IOException(tr("Dosya belleğe sığmayacak kadar karmaşık"))
        } catch (e: StackOverflowError) {
            throw IOException(tr("Dosya çok derin iç içe yapı içeriyor"))
        }
    }

    fun encode(doc: Document, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.Ai, ExportFormat.Pdf -> AiExporter.export(doc, exportOptions)
        ExportFormat.Svg -> SvgExporter.export(doc, outlineText = { TextOutliner.outline(it) }).toByteArray(Charsets.UTF_8)
        ExportFormat.Png -> renderPng(doc, 4096, transparent = true)
    }

    fun export(doc: Document, address: String, format: ExportFormat) {
        val bytes = encode(doc, format)
        try {
            writeBytes(address, bytes)
        } catch (e: SecurityException) {
            throw IOException(tr("Dosyaya yazma izni yok"))
        } catch (e: java.io.FileNotFoundException) {
            throw IOException(tr("Dosya artık yerinde değil"))
        }
    }

    fun readImage(address: String): ImageData = decodeImage(readBytes(address))

    // ---- Uygulama içi belge deposu -----------------------------------------

    /** Belgeyi depoya yazar (otomatik kayıt). Küçük resim de yenilenir. */
    fun saveToStore(meta: StoredDocument, doc: Document) {
        val thumb = try { renderPng(doc, 360, transparent = false) } catch (e: Throwable) { null }
        store.write(meta.copy(name = doc.name, modified = System.currentTimeMillis()), AiExporter.export(doc, exportOptions), thumb)
    }

    fun loadFromStore(id: String): Document {
        val meta = store.read(id) ?: throw IOException(tr("Belge bulunamadı"))
        try {
            return AiImporter.import(store.readData(id), meta.name).document
        } catch (e: ImportException) {
            throw IOException(e.message)
        }
    }
}
