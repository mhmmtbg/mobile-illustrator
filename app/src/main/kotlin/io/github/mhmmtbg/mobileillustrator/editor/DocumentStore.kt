package io.github.mhmmtbg.mobileillustrator.editor

import io.github.mhmmtbg.mobileillustrator.model.tr
import java.io.File
import java.util.Properties
import java.util.UUID

/** Galeride görünen bir belge. */
data class StoredDocument(
    val id: String,
    val name: String,
    val modified: Long,
    /** Belgenin bağlı olduğu dış dosya (cihazdaki .ai vb.); yoksa belge yalnızca uygulamanın içindedir. */
    val linkUri: String?,
    val linkFormat: ExportFormat?,
    /** Bağlı dosyanın üzerine sorulmadan yazılabilir mi (dosyayı bu uygulama yazdıysa). */
    val linkWritable: Boolean,
    /** Bağlı dosyaya henüz yazılmamış değişiklik var mı. */
    val unsaved: Boolean,
    val thumbnail: File,
)

/**
 * Uygulamanın kendi belge deposu. Her belge kendi klasöründe, sürekli otomatik kaydedilir; yeni belge
 * açmak ya da başka dosyaya geçmek hiçbir zaman öncekini silmez. Silme yalnızca kullanıcı isteyince olur.
 *
 * Klasör düzeni: `documents/<id>/doc.ai`, `meta.properties`, `thumb.png`.
 */
class DocumentStore(private val root: File) {
    private val dir = File(root, "documents")
    private val currentFile = File(root, "current-document")

    fun newId(): String = UUID.randomUUID().toString()

    private fun folder(id: String) = File(dir, id)
    fun dataFile(id: String) = File(folder(id), "doc.ai")
    fun thumbFile(id: String) = File(folder(id), "thumb.png")
    private fun metaFile(id: String) = File(folder(id), "meta.properties")

    @Synchronized
    fun exists(id: String): Boolean = dataFile(id).length() > 0

    @Synchronized
    fun list(): List<StoredDocument> =
        (dir.listFiles() ?: emptyArray()).filter { it.isDirectory && File(it, "doc.ai").length() > 0 }
            .mapNotNull { read(it.name) }
            .sortedByDescending { it.modified }

    @Synchronized
    fun read(id: String): StoredDocument? {
        if (!exists(id)) return null
        val p = Properties()
        try {
            metaFile(id).inputStream().use { p.load(it) }
        } catch (e: Exception) {
            // Meta okunamazsa belge yine de listelenir.
        }
        return StoredDocument(
            id = id,
            name = p.getProperty("name")?.takeIf { it.isNotBlank() } ?: "Adsız",
            modified = p.getProperty("modified")?.toLongOrNull() ?: dataFile(id).lastModified(),
            linkUri = p.getProperty("linkUri")?.takeIf { it.isNotBlank() },
            linkFormat = p.getProperty("linkFormat")?.let { n -> ExportFormat.entries.firstOrNull { it.name == n } },
            linkWritable = p.getProperty("linkWritable") == "true",
            unsaved = p.getProperty("unsaved") == "true",
            thumbnail = thumbFile(id),
        )
    }

    /** Belgeyi diske yazar. Önce geçici dosyaya yazılır; yarıda kesilirse eski sürüm bozulmaz. */
    @Synchronized
    fun write(meta: StoredDocument, data: ByteArray, thumbnail: ByteArray?) {
        val f = folder(meta.id)
        f.mkdirs()
        val tmp = File(f, "doc.tmp")
        tmp.writeBytes(data)
        val target = dataFile(meta.id)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException(tr("Belge kaydedilemedi"))
        }
        if (thumbnail != null) thumbFile(meta.id).writeBytes(thumbnail)
        writeMeta(meta)
    }

    @Synchronized
    fun writeMeta(meta: StoredDocument) {
        val f = folder(meta.id)
        if (!f.isDirectory) return
        val p = Properties()
        p.setProperty("name", meta.name)
        p.setProperty("modified", meta.modified.toString())
        p.setProperty("linkUri", meta.linkUri ?: "")
        p.setProperty("linkFormat", meta.linkFormat?.name ?: "")
        p.setProperty("linkWritable", meta.linkWritable.toString())
        p.setProperty("unsaved", meta.unsaved.toString())
        metaFile(meta.id).outputStream().use { p.store(it, null) }
    }

    @Synchronized
    fun readData(id: String): ByteArray = dataFile(id).readBytes()

    @Synchronized
    fun delete(id: String) {
        folder(id).deleteRecursively()
        if (current() == id) setCurrent(null)
    }

    @Synchronized
    fun current(): String? = try {
        currentFile.readText().trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun setCurrent(id: String?) {
        if (id == null) currentFile.delete() else currentFile.writeText(id)
    }

    /** Eski sürümün tek dosyalık otomatik kaydını depoya taşır. */
    @Synchronized
    fun migrateLegacy() {
        val old = File(root, "autosave.ai")
        if (old.length() <= 0) return
        val name = try { File(root, "autosave.name").readText().ifBlank { "Adsız" } } catch (e: Exception) { "Adsız" }
        val id = newId()
        try {
            write(StoredDocument(id, name, old.lastModified(), null, null, false, false, thumbFile(id)), old.readBytes(), null)
            if (current() == null) setCurrent(id)
        } finally {
            old.delete()
            File(root, "autosave.name").delete()
        }
    }
}
