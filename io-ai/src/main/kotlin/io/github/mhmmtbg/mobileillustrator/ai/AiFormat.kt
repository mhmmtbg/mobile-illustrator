package io.github.mhmmtbg.mobileillustrator.ai

/**
 * Illustrator (.ai) içe aktarma bu modüle gelecek (Aşama 5). Şimdilik yalnızca biçim tanıma.
 *
 * Illustrator 9 ve sonrası .ai dosyaları PDF kabuğu taşır; daha eskileri PostScript'tir.
 */
object AiFormat {
    const val MIME_TYPE = "application/illustrator"
    const val EXTENSION = "ai"

    enum class Container { Pdf, PostScript, Unknown }

    fun sniff(header: ByteArray): Container {
        val text = String(header, 0, minOf(header.size, 16), Charsets.ISO_8859_1)
        return when {
            text.startsWith("%PDF-") -> Container.Pdf
            text.startsWith("%!PS-Adobe") -> Container.PostScript
            else -> Container.Unknown
        }
    }
}
