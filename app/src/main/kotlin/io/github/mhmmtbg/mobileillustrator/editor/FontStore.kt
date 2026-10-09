package io.github.mhmmtbg.mobileillustrator.editor

import io.github.mhmmtbg.mobileillustrator.model.tr
import android.graphics.Typeface
import java.io.File
import java.io.IOException

/** Kullanıcının yüklediği fontlar (.ttf, .otf). Uygulamanın kendi klasöründe saklanır. */
class FontStore(root: File) {
    private val dir = File(root, "fonts")
    private val cache = HashMap<String, Typeface?>()

    @Synchronized
    fun list(): List<String> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.extension.lowercase() in Extensions }.map { it.nameWithoutExtension }.sorted()

    private fun fileOf(name: String): File? =
        (dir.listFiles() ?: emptyArray()).firstOrNull { it.nameWithoutExtension == name && it.extension.lowercase() in Extensions }

    /** Yüklü fontu döndürür; dosya yoksa ya da bozuksa `null` (çizim cihaz fontuna düşer). */
    @Synchronized
    fun typeface(name: String): Typeface? = cache.getOrPut(name) {
        val f = fileOf(name) ?: return@getOrPut null
        try { Typeface.createFromFile(f) } catch (e: RuntimeException) { null }
    }

    /**
     * Font dosyasını depoya kopyalar.
     * @return fontun adı (dosya adından).
     */
    @Synchronized
    fun add(fileName: String, bytes: ByteArray): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in Extensions) throw IOException(tr("Yalnızca .ttf ve .otf font dosyaları yüklenebilir"))
        if (bytes.size > 40 * 1024 * 1024) throw IOException(tr("Font dosyası çok büyük"))
        val name = fileName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _-]"), "_").trim().ifEmpty { "Font" }.take(60)
        dir.mkdirs()
        val target = File(dir, "$name.$ext")
        target.writeBytes(bytes)
        // Dosya gerçekten bir font mu? Değilse bırakma.
        val ok = try { Typeface.createFromFile(target) != null } catch (e: RuntimeException) { false }
        if (!ok) {
            target.delete()
            throw IOException(tr("Bu dosya geçerli bir font değil"))
        }
        cache.remove(name)
        return name
    }

    private companion object {
        val Extensions = setOf("ttf", "otf")
    }
}
