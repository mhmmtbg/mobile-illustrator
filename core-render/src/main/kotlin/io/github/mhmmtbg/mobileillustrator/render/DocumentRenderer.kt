package io.github.mhmmtbg.mobileillustrator.render

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

/**
 * Belgeyi bir [Canvas] üzerine çizer. Tuvalin dönüşümü (kaydırma, yakınlaştırma)
 * çağıran tarafından ayarlanmış olmalıdır; burada her şey belge koordinatındadır.
 *
 * Ekran, PNG ve PDF çıktısı aynı kodu kullanır.
 */
class DocumentRenderer {
    private val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.FILL }
    private val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }
    private val artboardPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL }
    private val path = AndroidPath()
    private val matrix = AndroidMatrix()
    private val matrixValues = FloatArray(9)

    /**
     * @param artboardColor çalışma yüzeyinin ARGB zemin rengi; `null` ise zemin çizilmez
     * (saydam PNG çıktısı için).
     */
    fun draw(canvas: Canvas, document: Document, artboardColor: Int? = 0xFFFFFFFF.toInt()) {
        if (artboardColor != null) {
            artboardPaint.color = artboardColor
            for (ab in document.artboards) {
                val b = ab.bounds
                canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), artboardPaint)
            }
        }
        for (layer in document.layers) {
            if (!layer.visible || layer.opacity <= 0.0) continue
            val isolated = layer.opacity < 1.0
            if (isolated) canvas.saveLayerAlpha(null, alphaOf(layer.opacity))
            for (node in layer.children) drawNode(canvas, node)
            if (isolated) canvas.restore()
        }
    }

    private fun drawNode(canvas: Canvas, node: Node) {
        if (!node.visible || node.opacity <= 0.0) return
        val count = canvas.save()
        if (!node.transform.isIdentity) canvas.concat(toAndroid(node.transform))
        when (node) {
            is GroupNode -> {
                if (node.opacity < 1.0) canvas.saveLayerAlpha(null, alphaOf(node.opacity))
                for (child in node.children) drawNode(canvas, child)
            }
            is PathNode -> drawPath(canvas, node)
        }
        canvas.restoreToCount(count)
    }

    private fun drawPath(canvas: Canvas, node: PathNode) {
        val fill = node.fill
        val stroke = node.stroke?.takeIf { it.width > 0.0 }
        if (fill == null && stroke == null) return

        buildPath(node.subpaths, node.fillRule)

        // Dolgu ve kontur birlikteyse yarı saydamlıkta üst üste binen kısım koyulaşmasın
        // diye nesne ayrı bir katmanda birleştirilir.
        var alpha = node.opacity
        if (alpha < 1.0 && fill != null && stroke != null) {
            canvas.saveLayerAlpha(null, alphaOf(alpha))
            alpha = 1.0
        }
        if (fill != null) {
            applyPaint(fillPaint, fill, alpha)
            canvas.drawPath(path, fillPaint)
        }
        if (stroke != null) {
            applyStroke(stroke, alpha)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun buildPath(subpaths: List<SubPath>, rule: FillRule) {
        path.rewind()
        path.fillType = if (rule == FillRule.EvenOdd) AndroidPath.FillType.EVEN_ODD else AndroidPath.FillType.WINDING
        for (sp in subpaths) {
            val anchors = sp.anchors
            if (anchors.isEmpty()) continue
            val first = anchors[0].point
            path.moveTo(first.x.toFloat(), first.y.toFloat())
            for (s in sp.segments()) {
                if (s.isLine) {
                    // Kapalı yolun son doğru parçasını close() çizer.
                    path.lineTo(s.p3.x.toFloat(), s.p3.y.toFloat())
                } else {
                    path.cubicTo(
                        s.p1.x.toFloat(), s.p1.y.toFloat(),
                        s.p2.x.toFloat(), s.p2.y.toFloat(),
                        s.p3.x.toFloat(), s.p3.y.toFloat(),
                    )
                }
            }
            if (sp.closed) path.close()
        }
    }

    private fun applyStroke(stroke: Stroke, alpha: Double) {
        applyPaint(strokePaint, stroke.paint, alpha)
        strokePaint.strokeWidth = stroke.width.toFloat()
        strokePaint.strokeCap = when (stroke.cap) {
            LineCap.Butt -> AndroidPaint.Cap.BUTT
            LineCap.Round -> AndroidPaint.Cap.ROUND
            LineCap.Square -> AndroidPaint.Cap.SQUARE
        }
        strokePaint.strokeJoin = when (stroke.join) {
            LineJoin.Miter -> AndroidPaint.Join.MITER
            LineJoin.Round -> AndroidPaint.Join.ROUND
            LineJoin.Bevel -> AndroidPaint.Join.BEVEL
        }
        strokePaint.strokeMiter = stroke.miterLimit.toFloat()
        // DashPathEffect çift sayıda, en az iki ve toplamı sıfırdan büyük aralık ister.
        val dash = stroke.dash.filter { it >= 0.0 }
        strokePaint.pathEffect = if (dash.size >= 2 && dash.size % 2 == 0 && dash.sum() > 0.0) {
            DashPathEffect(FloatArray(dash.size) { dash[it].toFloat() }, stroke.dashOffset.toFloat())
        } else {
            null
        }
    }

    private fun applyPaint(target: AndroidPaint, paint: Paint, alpha: Double) {
        when (paint) {
            is Paint.Solid -> {
                target.shader = null
                target.color = paint.color.copy(a = paint.color.a * alpha).toArgb()
            }
            is Paint.LinearGradient -> {
                target.color = 0xFF000000.toInt()
                target.alpha = alphaOf(alpha)
                target.shader = gradientOrNull(paint.stops) { colors, positions ->
                    LinearGradient(
                        paint.start.x.toFloat(), paint.start.y.toFloat(),
                        paint.end.x.toFloat(), paint.end.y.toFloat(),
                        colors, positions, Shader.TileMode.CLAMP,
                    )
                }
                if (target.shader == null) target.color = fallbackColor(paint.stops, alpha)
            }
            is Paint.RadialGradient -> {
                target.color = 0xFF000000.toInt()
                target.alpha = alphaOf(alpha)
                target.shader = if (paint.radius > 0.0) {
                    gradientOrNull(paint.stops) { colors, positions ->
                        RadialGradient(
                            paint.center.x.toFloat(), paint.center.y.toFloat(), paint.radius.toFloat(),
                            colors, positions, Shader.TileMode.CLAMP,
                        )
                    }
                } else {
                    null
                }
                if (target.shader == null) target.color = fallbackColor(paint.stops, alpha)
            }
        }
    }

    private inline fun gradientOrNull(stops: List<GradientStop>, make: (IntArray, FloatArray) -> Shader): Shader? {
        if (stops.size < 2) return null
        val sorted = stops.sortedBy { it.offset }
        val colors = IntArray(sorted.size) { sorted[it].color.toArgb() }
        val positions = FloatArray(sorted.size) { sorted[it].offset.coerceIn(0.0, 1.0).toFloat() }
        return make(colors, positions)
    }

    private fun fallbackColor(stops: List<GradientStop>, alpha: Double): Int {
        val c = stops.lastOrNull()?.color ?: return 0
        return c.copy(a = c.a * alpha).toArgb()
    }

    private fun alphaOf(opacity: Double) = (opacity.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()

    private fun toAndroid(m: Matrix): AndroidMatrix {
        matrixValues[0] = m.a.toFloat(); matrixValues[1] = m.c.toFloat(); matrixValues[2] = m.e.toFloat()
        matrixValues[3] = m.b.toFloat(); matrixValues[4] = m.d.toFloat(); matrixValues[5] = m.f.toFloat()
        matrixValues[6] = 0f; matrixValues[7] = 0f; matrixValues[8] = 1f
        matrix.setValues(matrixValues)
        return matrix
    }
}
