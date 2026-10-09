package io.github.mhmmtbg.mobileillustrator.render

import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Path as SkiaPath

/** Metni bilgisayardaki fontla yola çevirir (dışa aktarmada font bağımlılığını kaldırmak için). */
object TextOutliner {

    /** Metnin yerel uzayında (taban çizgisi başlangıcı 0,0) dış hatları; eğriler eğri olarak kalır. */
    fun outline(node: TextNode): List<SubPath> {
        val font = DocumentRenderer.fontFor(node)
        val path = SkiaPath()
        DocumentRenderer.forEachLine(node, font) { line, x, y ->
            val glyphs = font.getStringGlyphs(line)
            val widths = font.getWidths(glyphs)
            var pen = x
            for (i in glyphs.indices) {
                font.getPath(glyphs[i])?.let { glyph -> path.addPath(glyph, Matrix33.makeTranslate(pen, y)) }
                pen += widths[i]
            }
        }
        return PathBoolean.toSubPaths(path)
    }

    /** Ölçülen metin genişliği (yerel birim); çok satırlıysa en geniş satır. */
    fun measure(node: TextNode): Double {
        val font = DocumentRenderer.fontFor(node.copy(measuredWidth = null))
        return node.text.split('\n').maxOfOrNull { font.measureTextWidth(it).toDouble() } ?: 0.0
    }
}
