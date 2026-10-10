package io.github.mhmmtbg.mobileillustrator.render

import io.github.mhmmtbg.mobileillustrator.model.BlendMode
import io.github.mhmmtbg.mobileillustrator.model.ClipPath
import io.github.mhmmtbg.mobileillustrator.model.DocIndex
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
import io.github.mhmmtbg.mobileillustrator.model.TextAlign
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Typeface
import java.util.WeakHashMap
import org.jetbrains.skia.BlendMode as SkiaBlendMode
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.Path as SkiaPath
import org.jetbrains.skia.Rect as SkiaRect

/**
 * Belgeyi bir Skia tuvaline çizer (Windows sürümü). Android'deki çizicinin karşılığıdır: aynı ad, aynı
 * işlevler; ortak ekran kodu ikisini de aynı biçimde çağırır.
 *
 * Tuvalin dönüşümü (kaydırma, yakınlaştırma) çağıran tarafından ayarlanmış olmalıdır; burada her şey
 * belge koordinatındadır.
 */
class DocumentRenderer {
    private val fillPaint = SkiaPaint().apply { isAntiAlias = true; mode = PaintMode.FILL }
    private val strokePaint = SkiaPaint().apply { isAntiAlias = true; mode = PaintMode.STROKE }
    private val textPaint = SkiaPaint().apply { isAntiAlias = true }
    private val imagePaint = SkiaPaint().apply { isAntiAlias = true }
    private val artboardPaint = SkiaPaint().apply { mode = PaintMode.FILL }
    private val layerPaint = SkiaPaint()
    private val clipScratch = SkiaPath()

    /** Yollar düğüm kimliğine göre saklanır; geometri değişmediyse (aynı liste nesnesi) yeniden kurulmaz. */
    private class CachedPath(val source: List<SubPath>, val rule: FillRule, val path: SkiaPath)

    private val pathCache = HashMap<String, CachedPath>()
    private val imageCache = WeakHashMap<ImageData, Image>()

    /**
     * @param artboardColor çalışma yüzeyinin ARGB zemin rengi; `null` ise zemin çizilmez (saydam PNG çıktısı için).
     * @param visible verilirse yalnızca bu bölgeye (belge koordinatı) değen üst düzey nesneler çizilir.
     * @param skip çizilmeyecek üst düzey nesneler (sürüklenirken ayrıca çizilenler).
     */
    fun draw(
        canvas: Canvas,
        document: Document,
        artboardColor: Int? = 0xFFFFFFFF.toInt(),
        visible: io.github.mhmmtbg.mobileillustrator.model.Rect? = null,
        skip: Set<String>? = null,
        /** Arka plan çiziminde: `true` dönerse çizim yarıda bırakılır (sonuç artık gerekmiyordur). */
        stop: (() -> Boolean)? = null,
    ) {
        if (artboardColor != null) {
            artboardPaint.color = artboardColor
            for (ab in document.artboards) {
                val b = ab.bounds
                canvas.drawRect(SkiaRect.makeLTRB(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()), artboardPaint)
            }
        }
        if (pathCache.size > 60_000) pathCache.clear()
        for (layer in document.layers) {
            if (!layer.visible || layer.opacity <= 0.0) continue
            val isolated = layer.opacity < 1.0
            if (isolated) {
                layerPaint.blendMode = SkiaBlendMode.SRC_OVER
                layerPaint.alpha = alphaOf(layer.opacity)
                canvas.saveLayer(null, layerPaint)
            }
            if (stop?.invoke() == true) {
                // yarıda bırak
            } else if (visible == null && skip == null) {
                for (node in layer.children) drawNode(canvas, node)
            } else {
                val index = if (visible != null) DocIndex.of(document) else null
                var count = 0
                for (node in layer.children) {
                    if (stop != null && (count++ and 63) == 0 && stop()) break
                    if (skip != null && node.id in skip) continue
                    if (index != null && visible != null) {
                        val box = index.painted(node) ?: continue
                        if (!box.intersects(visible)) continue
                    }
                    drawNode(canvas, node)
                }
            }
            if (isolated) canvas.restore()
        }
    }

    /** Tek bir düğümü (ve altındakileri) çizer. */
    fun drawNode(canvas: Canvas, node: Node) {
        if (!node.visible || node.opacity <= 0.0) return
        val count = canvas.save()
        if (!node.transform.isIdentity) canvas.concat(toSkia(node.transform))
        when (node) {
            is GroupNode -> {
                var layerBounds: SkiaRect? = null
                node.clip?.let {
                    val clip = buildClip(it)
                    canvas.clipPath(clip, ClipMode.INTERSECT, true)
                    // Kırpılmış grubun ara katmanı kırpma alanından büyük olamaz.
                    layerBounds = clip.bounds
                }
                if (node.opacity < 1.0 || node.blendMode != BlendMode.Normal) saveBlendLayer(canvas, node.opacity, node.blendMode, layerBounds)
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

        // Dolgu ve kontur birlikteyse yarı saydamlıkta üst üste binen kısım koyulaşmasın diye
        // nesne ayrı bir katmanda birleştirilir.
        var alpha = node.opacity
        var blend = node.blendMode
        if (fill != null && stroke != null && (alpha < 1.0 || blend != BlendMode.Normal)) {
            // Ara katman yalnızca nesnenin kapladığı alan kadar açılır. Sınırsız katman tüm tuval boyutunda
            // bellek ayırır; binlerce yarı saydam nesnede çizimi on kat yavaşlatır.
            val pad = (stroke.width / 2 * maxOf(1.0, stroke.miterLimit)).toFloat() + 1f
            saveBlendLayer(canvas, alpha, blend, path.bounds.inflate(pad))
            alpha = 1.0
            blend = BlendMode.Normal
        }
        if (fill != null) {
            applyPaint(fillPaint, fill, alpha)
            fillPaint.blendMode = toSkia(blend)
            canvas.drawPath(path, fillPaint)
        }
        if (stroke != null) {
            applyStroke(strokePaint, stroke, alpha)
            strokePaint.blendMode = toSkia(blend)
            canvas.drawPath(path, strokePaint)
        }
    }

    /** Opaklığı ve karışım modunu içeriğin tamamına birden uygulamak için ara katman açar. */
    private fun saveBlendLayer(canvas: Canvas, opacity: Double, blend: BlendMode, bounds: SkiaRect? = null) {
        layerPaint.alpha = alphaOf(opacity)
        layerPaint.blendMode = toSkia(blend)
        canvas.saveLayer(bounds, layerPaint)
    }

    private fun toSkia(blend: BlendMode): SkiaBlendMode = when (blend) {
        BlendMode.Normal -> SkiaBlendMode.SRC_OVER
        BlendMode.Multiply -> SkiaBlendMode.MULTIPLY
        BlendMode.Screen -> SkiaBlendMode.SCREEN
        BlendMode.Overlay -> SkiaBlendMode.OVERLAY
        BlendMode.Darken -> SkiaBlendMode.DARKEN
        BlendMode.Lighten -> SkiaBlendMode.LIGHTEN
        BlendMode.ColorDodge -> SkiaBlendMode.COLOR_DODGE
        BlendMode.ColorBurn -> SkiaBlendMode.COLOR_BURN
        BlendMode.HardLight -> SkiaBlendMode.HARD_LIGHT
        BlendMode.SoftLight -> SkiaBlendMode.SOFT_LIGHT
        BlendMode.Difference -> SkiaBlendMode.DIFFERENCE
        BlendMode.Exclusion -> SkiaBlendMode.EXCLUSION
        BlendMode.Hue -> SkiaBlendMode.HUE
        BlendMode.Saturation -> SkiaBlendMode.SATURATION
        BlendMode.Color -> SkiaBlendMode.COLOR
        BlendMode.Luminosity -> SkiaBlendMode.LUMINOSITY
    }

    private fun pathFor(node: PathNode): SkiaPath {
        val cached = pathCache[node.id]
        if (cached != null && cached.source === node.subpaths && cached.rule == node.fillRule) return cached.path
        val p = SkiaPath()
        fillPath(p, node.subpaths, node.fillRule)
        pathCache[node.id] = CachedPath(node.subpaths, node.fillRule, p)
        return p
    }

    private fun buildClip(clip: ClipPath): SkiaPath {
        fillPath(clipScratch, clip.subpaths, clip.rule)
        return clipScratch
    }

    private fun drawImage(canvas: Canvas, node: ImageNode) {
        val image = imageFor(node.image) ?: return
        imagePaint.alpha = alphaOf(node.opacity)
        imagePaint.blendMode = toSkia(node.blendMode)
        canvas.drawImageRect(
            image,
            SkiaRect.makeWH(image.width.toFloat(), image.height.toFloat()),
            SkiaRect.makeWH(node.image.width.toFloat(), node.image.height.toFloat()),
            SamplingMode.LINEAR,
            imagePaint,
            true,
        )
    }

    private fun imageFor(data: ImageData): Image? {
        imageCache[data]?.let { return it }
        val image = try { Image.makeFromEncoded(data.bytes) } catch (e: Throwable) { null } ?: return null
        imageCache[data] = image
        return image
    }

    /** Eğilmiş metnin yolu; metnin biçimini belirleyen özellikler değişmedikçe yeniden kurulmaz. */
    private class WarpedText(val node: TextNode, val path: SkiaPath)

    private val warpCache = HashMap<String, WarpedText>()

    private fun warpedPath(node: TextNode): SkiaPath {
        val cached = warpCache[node.id]
        if (cached != null) {
            val o = cached.node
            if (o === node || (o.text == node.text && o.warp == node.warp && o.fontSize == node.fontSize && o.fontFamily == node.fontFamily &&
                    o.bold == node.bold && o.italic == node.italic && o.align == node.align && o.lineHeight == node.lineHeight &&
                    o.measuredWidth == node.measuredWidth && o.outline === node.outline)
            ) {
                return cached.path
            }
        }
        if (warpCache.size > 2000) warpCache.clear()
        val path = SkiaPath()
        fillPath(path, TextOutliner.outline(node), FillRule.NonZero)
        warpCache[node.id] = WarpedText(node, path)
        return path
    }

    private fun drawText(canvas: Canvas, node: TextNode) {
        if (node.warp != null) {
            // Eğilmiş metin: harf biçimleri bükülür ve yol olarak çizilir.
            val path = warpedPath(node)
            node.fill?.let { applyPaint(fillPaint, it, node.opacity); fillPaint.blendMode = toSkia(node.blendMode); canvas.drawPath(path, fillPaint) }
            node.stroke?.takeIf { it.width > 0.0 }?.let {
                applyStroke(strokePaint, it, node.opacity); strokePaint.blendMode = toSkia(node.blendMode); canvas.drawPath(path, strokePaint)
            }
            return
        }
        node.outline?.let { shapes ->
            // Dosyadaki fontun harf biçimleri: yol olarak çizilir.
            val cached = pathCache[node.id]
            val path = if (cached != null && cached.source === shapes) cached.path else SkiaPath().also {
                fillPath(it, shapes, FillRule.NonZero)
                pathCache[node.id] = CachedPath(shapes, FillRule.NonZero, it)
            }
            node.fill?.let { applyPaint(fillPaint, it, node.opacity); fillPaint.blendMode = toSkia(node.blendMode); canvas.drawPath(path, fillPaint) }
            node.stroke?.takeIf { it.width > 0.0 }?.let {
                applyStroke(strokePaint, it, node.opacity); strokePaint.blendMode = toSkia(node.blendMode); canvas.drawPath(path, strokePaint)
            }
            return
        }
        if (node.text.isEmpty()) return
        val font = fontFor(node)
        textPaint.blendMode = toSkia(node.blendMode)
        val fill = node.fill
        val stroke = node.stroke?.takeIf { it.width > 0.0 }
        if (fill != null) {
            textPaint.mode = PaintMode.FILL
            textPaint.pathEffect = null
            applyPaint(textPaint, fill, node.opacity)
            forEachLine(node, font) { line, x, y -> drawLine(canvas, node, font, line, x, y) }
        }
        if (stroke != null) {
            applyStroke(textPaint, stroke, node.opacity)
            textPaint.mode = PaintMode.STROKE
            forEachLine(node, font) { line, x, y -> drawLine(canvas, node, font, line, x, y) }
            textPaint.pathEffect = null
        }
    }

    /** Bir satırı çizer; harf aralığı varsa harfler tek tek yerleştirilir. */
    private fun drawLine(canvas: Canvas, node: TextNode, font: Font, line: String, x: Float, y: Float) {
        val extra = letterGap(node)
        if (extra == 0f) {
            canvas.drawString(line, x, y, font, textPaint)
            return
        }
        val glyphs = font.getStringGlyphs(line)
        if (glyphs.isEmpty()) return
        val widths = font.getWidths(glyphs)
        val xs = FloatArray(glyphs.size)
        // Aralık her harfin iki yanına eşit paylaştırılır (Android'in çizimiyle aynı).
        var pen = extra / 2
        for (i in glyphs.indices) { xs[i] = pen; pen += widths[i] + extra }
        org.jetbrains.skia.TextBlob.makeFromPosH(glyphs, xs, 0f, font)?.let { canvas.drawTextBlob(it, x, y, textPaint) }
    }

    private fun applyStroke(target: SkiaPaint, stroke: Stroke, alpha: Double) {
        applyPaint(target, stroke.paint, alpha)
        target.strokeWidth = stroke.width.toFloat()
        target.strokeCap = when (stroke.cap) {
            LineCap.Butt -> PaintStrokeCap.BUTT
            LineCap.Round -> PaintStrokeCap.ROUND
            LineCap.Square -> PaintStrokeCap.SQUARE
        }
        target.strokeJoin = when (stroke.join) {
            LineJoin.Miter -> PaintStrokeJoin.MITER
            LineJoin.Round -> PaintStrokeJoin.ROUND
            LineJoin.Bevel -> PaintStrokeJoin.BEVEL
        }
        target.strokeMiter = stroke.miterLimit.toFloat()
        // Kesikli çizgi çift sayıda, en az iki ve toplamı sıfırdan büyük aralık ister.
        var dash = stroke.dash.filter { it >= 0.0 }
        if (dash.size % 2 == 1) dash = dash + dash
        target.pathEffect = if (dash.size >= 2 && dash.sum() > 0.0) {
            PathEffect.makeDash(FloatArray(dash.size) { dash[it].toFloat() }, stroke.dashOffset.toFloat())
        } else {
            null
        }
    }

    private fun applyPaint(target: SkiaPaint, paint: Paint, alpha: Double) {
        when (paint) {
            is Paint.Solid -> {
                target.shader = null
                target.color = paint.color.copy(a = paint.color.a * alpha).toArgb()
            }
            is Paint.LinearGradient -> {
                target.color = 0xFF000000.toInt()
                target.alpha = alphaOf(alpha)
                target.shader = gradientOrNull(paint.stops, paint.transform) { colors, positions, style ->
                    Shader.makeLinearGradient(
                        paint.start.x.toFloat(), paint.start.y.toFloat(), paint.end.x.toFloat(), paint.end.y.toFloat(),
                        colors, positions, style,
                    )
                }
                if (target.shader == null) target.color = fallbackColor(paint.stops, alpha)
            }
            is Paint.RadialGradient -> {
                target.color = 0xFF000000.toInt()
                target.alpha = alphaOf(alpha)
                target.shader = if (paint.radius > 0.0) {
                    gradientOrNull(paint.stops, paint.transform) { colors, positions, style ->
                        Shader.makeRadialGradient(paint.center.x.toFloat(), paint.center.y.toFloat(), paint.radius.toFloat(), colors, positions, style)
                    }
                } else {
                    null
                }
                if (target.shader == null) target.color = fallbackColor(paint.stops, alpha)
            }
        }
    }

    private inline fun gradientOrNull(stops: List<GradientStop>, transform: Matrix, make: (IntArray, FloatArray, GradientStyle) -> Shader): Shader? {
        if (stops.size < 2) return null
        val sorted = stops.sortedBy { it.offset }
        val colors = IntArray(sorted.size) { sorted[it].color.toArgb() }
        val positions = FloatArray(sorted.size) { sorted[it].offset.coerceIn(0.0, 1.0).toFloat() }
        val style = GradientStyle(FilterTileMode.CLAMP, true, if (transform.isIdentity) null else toSkia(transform))
        return try {
            make(colors, positions, style)
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun fallbackColor(stops: List<GradientStop>, alpha: Double): Int {
        val c = stops.lastOrNull()?.color ?: return 0
        return c.copy(a = c.a * alpha).toArgb()
    }

    private fun alphaOf(opacity: Double) = (opacity.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()

    companion object {
        const val CUSTOM_FONT_PREFIX = "font:"

        /** Yüklenen fontları adından bulur; uygulama açılışında ayarlanır. Font yoksa `null` döner. */
        @Volatile
        var customFonts: ((String) -> Typeface?)? = null

        fun toSkia(m: Matrix): Matrix33 = Matrix33(
            m.a.toFloat(), m.c.toFloat(), m.e.toFloat(),
            m.b.toFloat(), m.d.toFloat(), m.f.toFloat(),
            0f, 0f, 1f,
        )

        fun fillPath(path: SkiaPath, subpaths: List<SubPath>, rule: FillRule) {
            path.reset()
            path.fillMode = if (rule == FillRule.EvenOdd) PathFillMode.EVEN_ODD else PathFillMode.WINDING
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
                        // Kapalı yolun son doğru parçasını closePath() çizer.
                        if (!(sp.closed && i == n - 1)) path.lineTo(b.point.x.toFloat(), b.point.y.toFloat())
                    } else {
                        val c1 = a.handleOut ?: a.point
                        val c2 = b.handleIn ?: b.point
                        path.cubicTo(c1.x.toFloat(), c1.y.toFloat(), c2.x.toFloat(), c2.y.toFloat(), b.point.x.toFloat(), b.point.y.toFloat())
                    }
                }
                if (sp.closed) path.closePath()
            }
        }

        private val sansFamilies = arrayOf("Segoe UI", "Arial", "Helvetica Neue", "Helvetica", "DejaVu Sans", "Liberation Sans", "Noto Sans")
        private val serifFamilies = arrayOf("Times New Roman", "Georgia", "DejaVu Serif", "Liberation Serif", "Noto Serif")
        private val monoFamilies = arrayOf("Consolas", "Courier New", "DejaVu Sans Mono", "Liberation Mono", "Noto Sans Mono")
        private val typefaces = HashMap<String, Typeface?>()

        private fun systemTypeface(families: Array<String>, style: FontStyle): Typeface? = synchronized(typefaces) {
            typefaces.getOrPut(families[0] + style) {
                families.firstNotNullOfOrNull { name -> FontMgr.default.matchFamilyStyle(name, style) }
                    ?: FontMgr.default.matchFamilyStyle(null, style)
            }
        }

        private val genericFamilies = setOf("sans-serif", "serif", "monospace")
        private val installed: Set<String> by lazy {
            val manager = FontMgr.default
            (0 until manager.familiesCount).map { manager.getFamilyName(it) }.toSet()
        }

        /** Bilgisayarda yüklü font aileleri (ada göre sıralı). */
        fun systemFamilies(): List<String> = installed.filter { it.isNotBlank() && !it.startsWith(".") }.sortedBy { it.lowercase() }

        /** Adı tam olarak tutan yüklü font; genel adlar (sans-serif…) ve bilinmeyen adlar için `null`. */
        private fun exactFamily(name: String, style: FontStyle): Typeface? {
            if (name in genericFamilies || name !in installed) return null
            return synchronized(typefaces) { typefaces.getOrPut("=" + name + style) { FontMgr.default.matchFamilyStyle(name, style) } }
        }

        /** Metin düğümünün yazı tipi, boyutu ve (biliniyorsa) özgün genişliğe uyacak yatay ölçeğiyle fontu. */
        fun fontFor(node: TextNode): Font {
            val style = when {
                node.bold && node.italic -> FontStyle.BOLD_ITALIC
                node.bold -> FontStyle.BOLD
                node.italic -> FontStyle.ITALIC
                else -> FontStyle.NORMAL
            }
            var synthesize = false
            val typeface = if (node.fontFamily.startsWith(CUSTOM_FONT_PREFIX)) {
                // Kullanıcının yüklediği font: kalın ve eğik biçimi dosyada yoktur, yapay olarak verilir.
                customFonts?.invoke(node.fontFamily.removePrefix(CUSTOM_FONT_PREFIX))?.also { synthesize = true }
            } else {
                null
            } ?: exactFamily(node.fontFamily, style) ?: run {
                val family = node.fontFamily.lowercase()
                val families = when {
                    family.contains("mono") || family.contains("courier") -> monoFamilies
                    family.contains("serif") && !family.contains("sans") -> serifFamilies
                    else -> sansFamilies
                }
                systemTypeface(families, style)
            }
            val font = Font(typeface, node.fontSize.toFloat())
            font.isSubpixel = true
            font.isLinearMetrics = true
            font.hinting = FontHinting.NONE
            font.edging = FontEdging.ANTI_ALIAS
            if (synthesize) {
                font.isEmboldened = node.bold
                if (node.italic) font.skewX = -0.2f
            }
            val want = node.measuredWidth
            if (want != null && want > 0.0 && node.text.isNotBlank() && node.text.indexOf('\n') < 0) {
                val have = font.measureTextWidth(node.text) + letterGap(node) * node.text.length
                if (have > 0f) font.scaleX = (want / have).toFloat().coerceIn(0.5f, 2f)
            }
            return font
        }

        /** Harfler arasına eklenen boşluk (yerel birim). */
        fun letterGap(node: TextNode): Float = (node.tracking / 1000.0 * node.fontSize).toFloat()

        /** Satırın harf aralığı dahil genişliği. */
        fun lineWidth(node: TextNode, font: Font, line: String): Float {
            val extra = letterGap(node)
            return font.measureTextWidth(line) + if (extra == 0f) 0f else extra * font.getStringGlyphs(line).size
        }

        /**
         * Metnin her satırını, yerel uzaydaki başlangıç noktasıyla verir. Tek satırlı metinde (0,0)'dır;
         * çok satırlıda satırlar aşağı doğru dizilir ve hizalamaya göre yatay kayar.
         */
        inline fun forEachLine(node: TextNode, font: Font, action: (line: String, x: Float, y: Float) -> Unit) {
            val start = node.align == TextAlign.Start
            if (start && node.text.indexOf('\n') < 0) {
                action(node.text, 0f, 0f)
                return
            }
            val step = (node.fontSize * node.lineHeight).toFloat()
            var y = 0f
            for (line in node.text.split('\n')) {
                val x = if (start) 0f else lineWidth(node, font, line).let { if (node.align == TextAlign.Center) -it / 2 else -it }
                action(line, x, y)
                y += step
            }
        }
    }
}
