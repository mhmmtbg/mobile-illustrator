package io.github.mhmmtbg.mobileillustrator.render

import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Path as SkiaPath

/** Metni bilgisayardaki fontla yola çevirir (dışa aktarmada font bağımlılığını kaldırmak için). */
object TextOutliner {

    /**
     * Metnin yerel uzayında (taban çizgisi başlangıcı 0,0) görünen dış hatları: dosyadan gelen harf biçimleri
     * varsa onlar, yoksa bilgisayardaki fontla çıkarılanlar; metin eğilmişse bükülmüş halleri.
     */
    fun outline(node: TextNode): List<SubPath> {
        val base = node.outline ?: plain(node)
        return node.warp?.apply(base) ?: base
    }

    /** Bilgisayardaki fontla, eğilmemiş dış hatlar. */
    private fun plain(node: TextNode): List<SubPath> {
        val font = DocumentRenderer.fontFor(node)
        val path = SkiaPath()
        DocumentRenderer.forEachLine(node, font) { line, x, y ->
            val glyphs = font.getStringGlyphs(line)
            val widths = font.getWidths(glyphs)
            val extra = DocumentRenderer.letterGap(node)
            var pen = x + extra / 2
            for (i in glyphs.indices) {
                font.getPath(glyphs[i])?.let { glyph -> path.addPath(glyph, Matrix33.makeTranslate(pen, y)) }
                pen += widths[i] + extra
            }
        }
        return PathBoolean.toSubPaths(path)
    }

    /** Ölçülen metin genişliği (yerel birim); çok satırlıysa en geniş satır. */
    fun measure(node: TextNode): Double {
        val font = DocumentRenderer.fontFor(node.copy(measuredWidth = null))
        return node.text.split('\n').maxOfOrNull { DocumentRenderer.lineWidth(node, font, it).toDouble() } ?: 0.0
    }
}
