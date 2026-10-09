package io.github.mhmmtbg.mobileillustrator.render

import android.graphics.PathMeasure
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

/** Metni cihazdaki fontla yola çevirir (dışa aktarmada font bağımlılığını kaldırmak için). */
object TextOutliner {

    /** Metnin yerel uzayında (taban çizgisi başlangıcı 0,0) dış hatları. */
    fun outline(node: TextNode): List<SubPath> {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.LINEAR_TEXT_FLAG).apply { hinting = AndroidPaint.HINTING_OFF }
        DocumentRenderer.configureText(paint, node)
        val path = AndroidPath()
        paint.getTextPath(node.text, 0, node.text.length, 0f, 0f, path)
        return toSubPaths(path, (node.fontSize * 0.002).toFloat().coerceAtLeast(0.002f))
    }

    /** Ölçülen metin genişliği (yerel birim). */
    fun measure(node: TextNode): Double {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.LINEAR_TEXT_FLAG).apply { hinting = AndroidPaint.HINTING_OFF }
        DocumentRenderer.configureText(paint, node.copy(measuredWidth = null))
        return paint.measureText(node.text).toDouble()
    }

    /** Android yolunu, eğrileri [tolerance] hatayla doğru parçalarına bölerek alt yollara çevirir. */
    fun toSubPaths(path: AndroidPath, tolerance: Float): List<SubPath> {
        val out = ArrayList<SubPath>()
        val measure = PathMeasure(path, false)
        val contour = AndroidPath()
        do {
            val len = measure.length
            if (len <= 0f) continue
            contour.rewind()
            measure.getSegment(0f, len, contour, true)
            // approximate(): [oran, x, y] üçlüleri
            val pts = contour.approximate(tolerance)
            val anchors = ArrayList<Anchor>(pts.size / 3)
            var i = 0
            while (i + 2 < pts.size) {
                val p = Vec2(pts[i + 1].toDouble(), pts[i + 2].toDouble())
                if (anchors.isEmpty() || anchors[anchors.size - 1].point != p) anchors += Anchor(p)
                i += 3
            }
            val closed = measure.isClosed
            if (closed && anchors.size > 1 && anchors[0].point == anchors[anchors.size - 1].point) anchors.removeAt(anchors.size - 1)
            if (anchors.size >= 2) out += SubPath(anchors, closed)
        } while (measure.nextContour())
        return out
    }
}
