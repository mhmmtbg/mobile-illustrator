package io.github.mhmmtbg.mobileillustrator.render

import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

/** Metni cihazdaki fontla yola çevirir (dışa aktarmada font bağımlılığını kaldırmak için). */
object TextOutliner {

    private fun paintFor(node: TextNode): AndroidPaint {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.LINEAR_TEXT_FLAG).apply { hinting = AndroidPaint.HINTING_OFF }
        DocumentRenderer.configureText(paint, node)
        return paint
    }

    /**
     * Metnin yerel uzayında (taban çizgisi başlangıcı 0,0) görünen dış hatları: dosyadan gelen harf biçimleri
     * varsa onlar, yoksa cihaz fontuyla çıkarılanlar; metin eğilmişse bükülmüş halleri. Eğriler eğri olarak kalır.
     */
    fun outline(node: TextNode): List<SubPath> {
        val base = node.outline ?: plain(node)
        return node.warp?.apply(base) ?: base
    }

    /** Cihaz fontuyla, eğilmemiş dış hatlar. */
    private fun plain(node: TextNode): List<SubPath> {
        val paint = paintFor(node)
        val path = AndroidPath()
        DocumentRenderer.forEachLine(node, paint) { line, x, y ->
            val p = AndroidPath()
            paint.getTextPath(line, 0, line.length, x, y, p)
            path.addPath(p)
        }
        return PathBoolean.toSubPaths(path)
    }

    /** Ölçülen metin genişliği (yerel birim); çok satırlıysa en geniş satır. */
    fun measure(node: TextNode): Double {
        val paint = paintFor(node.copy(measuredWidth = null))
        return node.text.split('\n').maxOfOrNull { paint.measureText(it).toDouble() } ?: 0.0
    }
}
