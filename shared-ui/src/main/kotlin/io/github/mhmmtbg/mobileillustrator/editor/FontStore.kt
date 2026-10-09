package io.github.mhmmtbg.mobileillustrator.editor

import io.github.mhmmtbg.mobileillustrator.model.tr
import java.io.File
import java.io.IOException

/** Kullanıcının yüklediği fontlar (.ttf, .otf). Uygulamanın kendi klasöründe saklanır. */
class FontStore(root: File) {
    private val dir = File(root, "fonts")

    @Synchronized
    fun list(): List<String> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.extension.lowercase() in Extensions }.map { it.nameWithoutExtension }.sorted()

    /** Yüklü fontun dosyası; yoksa `null` (çizim cihaz fontuna düşer). */
    @Synchronized
    fun fileOf(name: String): File? =
        (dir.listFiles() ?: emptyArray()).firstOrNull { it.nameWithoutExtension == name && it.extension.lowercase() in Extensions }

    /**
     * Font dosyasını depoya kopyalar.
     * @param isValidFont dosyanın bu platformda font olarak açılıp açılmadığını denetler.
     * @return fontun adı (dosya adından).
     */
    @Synchronized
    fun add(fileName: String, bytes: ByteArray, isValidFont: (File) -> Boolean): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in Extensions) throw IOException(tr("Yalnızca .ttf ve .otf font dosyaları yüklenebilir"))
        if (bytes.size > 40 * 1024 * 1024) throw IOException(tr("Font dosyası çok büyük"))
        val name = fileName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _-]"), "_").trim().ifEmpty { "Font" }.take(60)
        dir.mkdirs()
        val target = File(dir, "$name.$ext")
        target.writeBytes(bytes)
        // Dosya gerçekten bir font mu? Değilse bırakma.
        val ok = try { isValidFont(target) } catch (e: RuntimeException) { false }
        if (!ok) {
            target.delete()
            throw IOException(tr("Bu dosya geçerli bir font değil"))
        }
        return name
    }

    private companion object {
        val Extensions = setOf("ttf", "otf")
    }
}
