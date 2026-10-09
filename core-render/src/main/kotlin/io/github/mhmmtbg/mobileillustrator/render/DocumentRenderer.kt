package io.github.mhmmtbg.mobileillustrator.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import io.github.mhmmtbg.mobileillustrator.model.BlendMode
import io.github.mhmmtbg.mobileillustrator.model.ClipPath
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import java.util.WeakHashMap
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
    // Doğrusal metin + ipucu kapalı: harf genişlikleri yakınlaştırmayla birlikte tam ölçeklenir.
    private val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.SUBPIXEL_TEXT_FLAG or AndroidPaint.LINEAR_TEXT_FLAG)
        .apply { hinting = AndroidPaint.HINTING_OFF }
    private val bitmapPaint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.ANTI_ALIAS_FLAG)
    private val artboardPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL }
    private val layerPaint = AndroidPaint()
    private val matrix = AndroidMatrix()
    private val shaderMatrix = AndroidMatrix()
    private val matrixValues = FloatArray(9)
    private val clipScratch = AndroidPath()
    private val dst = RectF()

    /** Yollar düğüm kimliğine göre saklanır; geometri değişmediyse (aynı liste nesnesi) yeniden kurulmaz. */
    private class CachedPath(val source: List<SubPath>, val rule: FillRule, val path: AndroidPath)

    private val pathCache = HashMap<String, CachedPath>()
    private val bitmapCache = WeakHashMap<ImageData, Bitmap>()

    /**
     * @param artboardColor çalışma yüzeyinin ARGB zemin rengi; `null` ise zemin çizilmez
     * (saydam PNG çıktısı için).
     */
    fun draw(
        canvas: Canvas,
        document: Document,
        artboardColor: Int? = 0xFFFFFFFF.toInt(),
        /** Verilirse yalnızca bu bölgeye (belge koordinatı) değen üst düzey nesneler çizilir. */
        visible: io.github.mhmmtbg.mobileillustrator.model.Rect? = null,
        /** Çizilmeyecek üst düzey nesneler (sürüklenirken ayrıca çizilenler). */
        skip: Set<String>? = null,
    ) {
        if (artboardColor != null) {
            artboardPaint.color = artboardColor
            for (ab in document.artboards) {
                val b = ab.bounds
                canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), artboardPaint)
            }
        }
        if (pathCache.size > 60_000) pathCache.clear()
        for (layer in document.layers) {
            if (!layer.visible || layer.opacity <= 0.0) continue
            val isolated = layer.opacity < 1.0
            if (isolated) canvas.saveLayerAlpha(null, alphaOf(layer.opacity))
            if (visible == null && skip == null) {
                for (node in layer.children) drawNode(canvas, node)
            } else {
                val index = if (visible != null) io.github.mhmmtbg.mobileillustrator.model.DocIndex.of(document) else null
                for (node in layer.children) {
                    if (skip != null && node.id in skip) continue
                    if (index != null && visible != null) {
                        // Görünen alanın dışındaki nesneler hiç çizilmez (yakınlaştırılmış büyük belgelerde asıl kazanç).
                        val box = index.painted(node) ?: continue
                        if (!box.intersects(visible)) continue
                    }
                    drawNode(canvas, node)
                }
            }
            if (isolated) canvas.restore()
        }
    }

    /** Tek bir düğümü (ve altındakileri) çizer; küçük resimler için. */
    fun drawNode(canvas: Canvas, node: Node) {
        if (!node.visible || node.opacity <= 0.0) return
        val count = canvas.save()
        if (!node.transform.isIdentity) canvas.concat(toAndroid(node.transform, matrix))
        when (node) {
            is GroupNode -> {
                node.clip?.let { canvas.clipPath(buildClip(it)) }
                if (node.opacity < 1.0 || node.blendMode != BlendMode.Normal) saveBlendLayer(canvas, node.opacity, node.blendMode)
                for (child in node.children) drawNode(canvas, child)
            }
            is PathNode -> drawPath(canvas, node)
            is ImageNode -> drawImage(canvas, node)
            is TextNode -> drawText(canvas, node)
        }
        canvas.restoreToCount(count)
    }

    private fun drawPath(canvas: Canvas, node: PathNode) {
        val fill = node.fill
        val stroke = node.stroke?.takeIf { it.width > 0.0 }
        if (fill == null && stroke == null) return

        val path = pathFor(node)
        // Görünür alanın dışındaki yolları boyamaya çalışma (kontur payıyla birlikte).
        if (stroke == null && canvas.quickReject(path, Canvas.EdgeType.AA)) return

        // Dolgu ve kontur birlikteyse yarı saydamlıkta üst üste binen kısım koyulaşmasın
        // diye nesne ayrı bir katmanda birleştirilir.
        var alpha = node.opacity
        var blend = node.blendMode
        if (fill != null && stroke != null && (alpha < 1.0 || blend != BlendMode.Normal)) {
            saveBlendLayer(canvas, alpha, blend)
            alpha = 1.0
            blend = BlendMode.Normal
        }
        if (fill != null) {
            applyPaint(fillPaint, fill, alpha)
            setBlend(fillPaint, blend)
            canvas.drawPath(path, fillPaint)
        }
        if (stroke != null) {
            applyStroke(strokePaint, stroke, alpha)
            setBlend(strokePaint, blend)
            canvas.drawPath(path, strokePaint)
        }
    }

    /** Opaklığı ve karışım modunu içeriğin tamamına birden uygulamak için ara katman açar. */
    private fun saveBlendLayer(canvas: Canvas, opacity: Double, blend: BlendMode) {
        layerPaint.alpha = alphaOf(opacity)
        setBlend(layerPaint, blend)
        canvas.saveLayer(null, layerPaint)
    }

    private fun setBlend(paint: AndroidPaint, blend: BlendMode) {
        if (blend == BlendMode.Normal) {
            if (paint.xfermode != null) paint.xfermode = null
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            paint.blendMode = when (blend) {
                BlendMode.Multiply -> android.graphics.BlendMode.MULTIPLY
                BlendMode.Screen -> android.graphics.BlendMode.SCREEN
                BlendMode.Overlay -> android.graphics.BlendMode.OVERLAY
                BlendMode.Darken -> android.graphics.BlendMode.DARKEN
                BlendMode.Lighten -> android.graphics.BlendMode.LIGHTEN
                BlendMode.ColorDodge -> android.graphics.BlendMode.COLOR_DODGE
                BlendMode.ColorBurn -> android.graphics.BlendMode.COLOR_BURN
                BlendMode.HardLight -> android.graphics.BlendMode.HARD_LIGHT
                BlendMode.SoftLight -> android.graphics.BlendMode.SOFT_LIGHT
                BlendMode.Difference -> android.graphics.BlendMode.DIFFERENCE
                BlendMode.Exclusion -> android.graphics.BlendMode.EXCLUSION
                BlendMode.Hue -> android.graphics.BlendMode.HUE
                BlendMode.Saturation -> android.graphics.BlendMode.SATURATION
                BlendMode.Color -> android.graphics.BlendMode.COLOR
                BlendMode.Luminosity -> android.graphics.BlendMode.LUMINOSITY
                BlendMode.Normal -> android.graphics.BlendMode.SRC_OVER
            }
        } else {
            // Android 9 ve öncesinde yalnızca temel modlar var; diğerleri normal çizilir.
            val mode = when (blend) {
                BlendMode.Multiply -> android.graphics.PorterDuff.Mode.MULTIPLY
                BlendMode.Screen -> android.graphics.PorterDuff.Mode.SCREEN
                BlendMode.Overlay -> android.graphics.PorterDuff.Mode.OVERLAY
                BlendMode.Darken -> android.graphics.PorterDuff.Mode.DARKEN
                BlendMode.Lighten -> android.graphics.PorterDuff.Mode.LIGHTEN
                else -> null
            }
            paint.xfermode = mode?.let { android.graphics.PorterDuffXfermode(it) }
        }
    }

    private fun pathFor(node: PathNode): AndroidPath {
        val cached = pathCache[node.id]
        if (cached != null && cached.source === node.subpaths && cached.rule == node.fillRule) return cached.path
        val p = AndroidPath()
        fillPath(p, node.subpaths, node.fillRule)
        pathCache[node.id] = CachedPath(node.subpaths, node.fillRule, p)
        return p
    }

    private fun buildClip(clip: ClipPath): AndroidPath {
        // clipPath yolu kopyaladığı için tek bir çalışma nesnesi yeterli.
        fillPath(clipScratch, clip.subpaths, clip.rule)
        return clipScratch
    }

    private fun drawImage(canvas: Canvas, node: ImageNode) {
        val bmp = bitmapFor(node.image) ?: return
        dst.set(0f, 0f, node.image.width.toFloat(), node.image.height.toFloat())
        if (canvas.quickReject(dst, Canvas.EdgeType.AA)) return
        bitmapPaint.alpha = alphaOf(node.opacity)
        setBlend(bitmapPaint, node.blendMode)
        canvas.drawBitmap(bmp, null, dst, bitmapPaint)
    }

    private fun bitmapFor(image: ImageData): Bitmap? {
        bitmapCache[image]?.let { return it }
        val opts = BitmapFactory.Options()
        // Çok büyük görseller bellek için küçültülerek açılır; ekranda fark edilmez.
        var sample = 1
        while (image.width.toLong() * image.height / (sample * sample) > 16_000_000L) sample *= 2
        opts.inSampleSize = sample
        val bmp = try {
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, opts)
        } catch (e: Throwable) {
            null
        } ?: return null
        bitmapCache[image] = bmp
        return bmp
    }

    private fun drawText(canvas: Canvas, node: TextNode) {
        node.outline?.let { shapes ->
            // Dosyadaki fontun harf biçimleri: yol olarak çizilir.
            val cached = pathCache[node.id]
            val path = if (cached != null && cached.source === shapes) cached.path else AndroidPath().also {
                fillPath(it, shapes, FillRule.NonZero)
                pathCache[node.id] = CachedPath(shapes, FillRule.NonZero, it)
            }
            node.fill?.let { applyPaint(fillPaint, it, node.opacity); setBlend(fillPaint, node.blendMode); canvas.drawPath(path, fillPaint) }
            node.stroke?.takeIf { it.width > 0.0 }?.let {
                applyStroke(strokePaint, it, node.opacity); setBlend(strokePaint, node.blendMode); canvas.drawPath(path, strokePaint)
            }
            return
        }
        if (node.text.isEmpty()) return
        configureText(textPaint, node)
        setBlend(textPaint, node.blendMode)
        val fill = node.fill
        val stroke = node.stroke?.takeIf { it.width > 0.0 }
        if (fill != null) {
            textPaint.style = AndroidPaint.Style.FILL
            applyPaint(textPaint, fill, node.opacity)
            forEachLine(node, textPaint) { line, x, y -> canvas.drawText(line, x, y, textPaint) }
        }
        if (stroke != null) {
            textPaint.style = AndroidPaint.Style.STROKE
            applyStroke(textPaint, stroke, node.opacity)
            forEachLine(node, textPaint) { line, x, y -> canvas.drawText(line, x, y, textPaint) }
            textPaint.pathEffect = null
        }
    }

    private fun applyStroke(target: AndroidPaint, stroke: Stroke, alpha: Double) {
        applyPaint(target, stroke.paint, alpha)
        target.strokeWidth = stroke.width.toFloat()
        target.strokeCap = when (stroke.cap) {
            LineCap.Butt -> AndroidPaint.Cap.BUTT
            LineCap.Round -> AndroidPaint.Cap.ROUND
            LineCap.Square -> AndroidPaint.Cap.SQUARE
        }
        target.strokeJoin = when (stroke.join) {
            LineJoin.Miter -> AndroidPaint.Join.MITER
            LineJoin.Round -> AndroidPaint.Join.ROUND
            LineJoin.Bevel -> AndroidPaint.Join.BEVEL
        }
        target.strokeMiter = stroke.miterLimit.toFloat()
        // DashPathEffect çift sayıda, en az iki ve toplamı sıfırdan büyük aralık ister.
        var dash = stroke.dash.filter { it >= 0.0 }
        if (dash.size % 2 == 1) dash = dash + dash
        target.pathEffect = if (dash.size >= 2 && dash.sum() > 0.0) {
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
                target.shader = gradientOrNull(paint.stops, paint.transform) { colors, positions ->
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
                    gradientOrNull(paint.stops, paint.transform) { colors, positions ->
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

    private inline fun gradientOrNull(stops: List<GradientStop>, transform: Matrix, make: (IntArray, FloatArray) -> Shader): Shader? {
        if (stops.size < 2) return null
        val sorted = stops.sortedBy { it.offset }
        val colors = IntArray(sorted.size) { sorted[it].color.toArgb() }
        val positions = FloatArray(sorted.size) { sorted[it].offset.coerceIn(0.0, 1.0).toFloat() }
        val shader = try {
            make(colors, positions)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (!transform.isIdentity) shader.setLocalMatrix(toAndroid(transform, shaderMatrix))
        return shader
    }

    private fun fallbackColor(stops: List<GradientStop>, alpha: Double): Int {
        val c = stops.lastOrNull()?.color ?: return 0
        return c.copy(a = c.a * alpha).toArgb()
    }

    private fun alphaOf(opacity: Double) = (opacity.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()

    private fun toAndroid(m: Matrix, into: AndroidMatrix): AndroidMatrix {
        matrixValues[0] = m.a.toFloat(); matrixValues[1] = m.c.toFloat(); matrixValues[2] = m.e.toFloat()
        matrixValues[3] = m.b.toFloat(); matrixValues[4] = m.d.toFloat(); matrixValues[5] = m.f.toFloat()
        matrixValues[6] = 0f; matrixValues[7] = 0f; matrixValues[8] = 1f
        into.setValues(matrixValues)
        return into
    }

    companion object {
        fun fillPath(path: AndroidPath, subpaths: List<SubPath>, rule: FillRule) {
            path.rewind()
            path.fillType = if (rule == FillRule.EvenOdd) AndroidPath.FillType.EVEN_ODD else AndroidPath.FillType.WINDING
            for (sp in subpaths) {
                val anchors = sp.anchors
                if (anchors.isEmpty()) continue
                val first = anchors[0].point
                path.moveTo(first.x.toFloat(), first.y.toFloat())
                val n = anchors.size
                val last = if (sp.closed) n else n - 1
                for (i in 0 until last) {
                    val a = anchors[i]
                    val b = anchors[(i + 1) % n]
                    if (a.handleOut == null && b.handleIn == null) {
                        // Kapalı yolun son doğru parçasını close() çizer.
                        if (!(sp.closed && i == n - 1)) path.lineTo(b.point.x.toFloat(), b.point.y.toFloat())
                    } else {
                        val c1 = a.handleOut ?: a.point
                        val c2 = b.handleIn ?: b.point
                        path.cubicTo(
                            c1.x.toFloat(), c1.y.toFloat(), c2.x.toFloat(), c2.y.toFloat(),
                            b.point.x.toFloat(), b.point.y.toFloat(),
                        )
                    }
                }
                if (sp.closed) path.close()
            }
        }

        fun typefaceFor(node: TextNode): Typeface {
            val family = node.fontFamily.lowercase()
            val base = when {
                family.contains("mono") || family.contains("courier") -> Typeface.MONOSPACE
                family.contains("serif") && !family.contains("sans") -> Typeface.SERIF
                else -> Typeface.SANS_SERIF
            }
            val style = when {
                node.bold && node.italic -> Typeface.BOLD_ITALIC
                node.bold -> Typeface.BOLD
                node.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            return Typeface.create(base, style)
        }

        /**
         * Metnin her satırını, yerel uzaydaki başlangıç noktasıyla verir. Tek satırlı metinde (0,0)'dır;
         * çok satırlıda satırlar aşağı doğru dizilir ve hizalamaya göre yatay kayar.
         */
        inline fun forEachLine(node: TextNode, paint: AndroidPaint, action: (line: String, x: Float, y: Float) -> Unit) {
            if (node.text.indexOf('\n') < 0) {
                action(node.text, 0f, 0f)
                return
            }
            val step = (node.fontSize * 1.2).toFloat()
            var y = 0f
            for (line in node.text.split('\n')) {
                action(line, 0f, y)
                y += step
            }
        }

        /** Yazı tipini, boyutu ve (biliniyorsa) özgün genişliğe uyacak yatay ölçeği ayarlar. */
        fun configureText(paint: AndroidPaint, node: TextNode) {
            paint.typeface = typefaceFor(node)
            paint.textSize = node.fontSize.toFloat()
            paint.textScaleX = 1f
            val want = node.measuredWidth
            if (want != null && want > 0.0 && node.text.isNotBlank()) {
                val have = paint.measureText(node.text)
                if (have > 0f) paint.textScaleX = (want / have).toFloat().coerceIn(0.5f, 2f)
            }
        }
    }
}
