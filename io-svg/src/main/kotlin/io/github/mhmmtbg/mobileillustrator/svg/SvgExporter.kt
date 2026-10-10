package io.github.mhmmtbg.mobileillustrator.svg

import io.github.mhmmtbg.mobileillustrator.model.BlendMode
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToLong

/** Belgeyi SVG 1.1 olarak yazar. Katmanlar, Illustrator ve Inkscape'in katman olarak tanıdığı üst düzey gruplardır. */
object SvgExporter {

    /**
     * @param artboard yalnızca bu çalışma yüzeyini yaz; `null` ise hepsini kapsayan alan.
     * @param outlineText eğilmiş metinlerin harf biçimlerini verir (SVG metni bükülemediği için yol olarak yazılırlar).
     */
    fun export(document: Document, artboard: Int? = null, outlineText: ((TextNode) -> List<SubPath>?)? = null): String {
        val box = artboard?.let { document.artboards.getOrNull(it)?.bounds }
            ?: document.artboardBounds()
            ?: Rect(0.0, 0.0, 100.0, 100.0)
        val w = Writer(outlineText)
        val sb = w.sb
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
        sb.append(" xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\"")
        sb.append(" width=\"").append(num(box.width)).append("\" height=\"").append(num(box.height)).append('"')
        sb.append(" viewBox=\"").append(num(box.left)).append(' ').append(num(box.top)).append(' ')
            .append(num(box.width)).append(' ').append(num(box.height)).append("\">\n")
        sb.append("<title>").append(esc(document.name)).append("</title>\n")
        for ((i, layer) in document.layers.withIndex()) {
            sb.append("<g id=\"").append(idOf(layer.name, i)).append("\" data-name=\"").append(esc(layer.name)).append('"')
            sb.append(" inkscape:groupmode=\"layer\" inkscape:label=\"").append(esc(layer.name)).append('"')
            if (!layer.visible) sb.append(" display=\"none\"")
            if (layer.opacity < 1.0) sb.append(" opacity=\"").append(num(layer.opacity)).append('"')
            sb.append(">\n")
            for (n in layer.children) w.node(n, 1)
            sb.append("</g>\n")
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun idOf(name: String, index: Int): String {
        val clean = name.map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }.joinToString("").trim('_')
        return if (clean.isEmpty()) "Layer_${index + 1}" else "${clean}_${index + 1}"
    }

    internal fun num(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val r = (v * 10000).roundToLong()
        if (r % 10000 == 0L) return (r / 10000).toString()
        val neg = r < 0
        val a = abs(r)
        val frac = (a % 10000).toString().padStart(4, '0').trimEnd('0')
        return (if (neg) "-" else "") + (a / 10000) + "." + frac
    }

    internal fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> if (c.code >= 32 || c == '\n' || c == '\t') sb.append(c)
            }
        }
        return sb.toString()
    }

    fun pathData(subpaths: List<SubPath>): String {
        val sb = StringBuilder()
        for (sp in subpaths) {
            val a = sp.anchors
            if (a.isEmpty()) continue
            sb.append('M').append(num(a[0].point.x)).append(' ').append(num(a[0].point.y))
            for (s in sp.segments()) {
                if (s.isLine) {
                    sb.append('L').append(num(s.p3.x)).append(' ').append(num(s.p3.y))
                } else {
                    sb.append('C').append(num(s.p1.x)).append(' ').append(num(s.p1.y)).append(' ')
                        .append(num(s.p2.x)).append(' ').append(num(s.p2.y)).append(' ')
                        .append(num(s.p3.x)).append(' ').append(num(s.p3.y))
                }
            }
            if (sp.closed) sb.append('Z')
        }
        return sb.toString()
    }

    private fun matrix(m: Matrix) =
        "matrix(${num6(m.a)} ${num6(m.b)} ${num6(m.c)} ${num6(m.d)} ${num(m.e)} ${num(m.f)})"

    private fun num6(v: Double): String {
        val s = String.format(java.util.Locale.ROOT, "%.6f", v)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.').ifEmpty { "0" }.let { if (it == "-0") "0" else it } else s
    }

    private class Writer(private val outlineText: ((TextNode) -> List<SubPath>?)?) {
        val sb = StringBuilder(1 shl 16)
        private var nextId = 0

        fun node(n: Node, depth: Int) {
            when (n) {
                is PathNode -> path(n)
                is GroupNode -> group(n, depth)
                is ImageNode -> image(n)
                is TextNode -> text(n)
            }
        }

        private fun common(n: Node) {
            if (!n.visible) sb.append(" display=\"none\"")
            if (n.opacity < 1.0) sb.append(" opacity=\"").append(num(n.opacity)).append('"')
            if (n.blendMode != BlendMode.Normal) sb.append(" style=\"mix-blend-mode:").append(n.blendMode.cssName).append("\"")
            if (!n.transform.isIdentity) sb.append(" transform=\"").append(matrix(n.transform)).append('"')
        }

        private fun paintRef(p: Paint?): Pair<String, Double> = when (p) {
            null -> "none" to 1.0
            is Paint.Solid -> hex(p.color.toArgb()) to p.color.a
            is Paint.LinearGradient -> {
                val id = "g${nextId++}"
                sb.append("<linearGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\"")
                    .append(" x1=\"").append(num(p.start.x)).append("\" y1=\"").append(num(p.start.y))
                    .append("\" x2=\"").append(num(p.end.x)).append("\" y2=\"").append(num(p.end.y)).append('"')
                if (!p.transform.isIdentity) sb.append(" gradientTransform=\"").append(matrix(p.transform)).append('"')
                sb.append('>')
                stops(p.stops)
                sb.append("</linearGradient>\n")
                "url(#$id)" to 1.0
            }
            is Paint.RadialGradient -> {
                val id = "g${nextId++}"
                sb.append("<radialGradient id=\"").append(id).append("\" gradientUnits=\"userSpaceOnUse\"")
                    .append(" cx=\"").append(num(p.center.x)).append("\" cy=\"").append(num(p.center.y))
                    .append("\" r=\"").append(num(p.radius)).append('"')
                if (!p.transform.isIdentity) sb.append(" gradientTransform=\"").append(matrix(p.transform)).append('"')
                sb.append('>')
                stops(p.stops)
                sb.append("</radialGradient>\n")
                "url(#$id)" to 1.0
            }
        }

        private fun stops(stops: List<GradientStop>) {
            for (s in stops) {
                sb.append("<stop offset=\"").append(num(s.offset)).append("\" stop-color=\"").append(hex(s.color.toArgb())).append('"')
                if (s.color.a < 1.0) sb.append(" stop-opacity=\"").append(num(s.color.a)).append('"')
                sb.append("/>")
            }
        }

        private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

        private fun style(fill: Paint?, stroke: Stroke?, rule: FillRule?) {
            val (f, fa) = paintRef(fill)
            val strokeRef = stroke?.let { paintRef(it.paint) }
            sb.append(TAG)
            sb.append(" fill=\"").append(f).append('"')
            if (fa < 1.0) sb.append(" fill-opacity=\"").append(num(fa)).append('"')
            if (rule == FillRule.EvenOdd && fill != null) sb.append(" fill-rule=\"evenodd\"")
            if (stroke != null && strokeRef != null) {
                sb.append(" stroke=\"").append(strokeRef.first).append("\" stroke-width=\"").append(num(stroke.width)).append('"')
                if (strokeRef.second < 1.0) sb.append(" stroke-opacity=\"").append(num(strokeRef.second)).append('"')
                when (stroke.cap) {
                    LineCap.Round -> sb.append(" stroke-linecap=\"round\"")
                    LineCap.Square -> sb.append(" stroke-linecap=\"square\"")
                    LineCap.Butt -> {}
                }
                when (stroke.join) {
                    LineJoin.Round -> sb.append(" stroke-linejoin=\"round\"")
                    LineJoin.Bevel -> sb.append(" stroke-linejoin=\"bevel\"")
                    LineJoin.Miter -> if (stroke.miterLimit != 4.0) sb.append(" stroke-miterlimit=\"").append(num(stroke.miterLimit)).append('"')
                }
                if (stroke.dash.isNotEmpty()) {
                    sb.append(" stroke-dasharray=\"").append(stroke.dash.joinToString(" ") { num(it) }).append('"')
                    if (stroke.dashOffset != 0.0) sb.append(" stroke-dashoffset=\"").append(num(stroke.dashOffset)).append('"')
                }
            }
        }

        // Gradyan tanımları öğeden önce yazılmalı; öğenin açılış etiketi bu yüzden style() içinde eklenir.
        private var TAG = ""

        private fun path(n: PathNode) {
            TAG = "<path"
            style(n.fill, n.stroke, n.fillRule)
            common(n)
            sb.append(" d=\"").append(pathData(n.subpaths)).append("\"/>\n")
        }

        private fun group(n: GroupNode, depth: Int) {
            var clipId: String? = null
            n.clip?.let { c ->
                clipId = "c${nextId++}"
                sb.append("<clipPath id=\"").append(clipId).append("\"><path d=\"").append(pathData(c.subpaths)).append('"')
                if (c.rule == FillRule.EvenOdd) sb.append(" clip-rule=\"evenodd\"")
                sb.append("/></clipPath>\n")
            }
            sb.append("<g")
            if (n.name.isNotEmpty()) sb.append(" data-name=\"").append(esc(n.name)).append('"')
            common(n)
            if (clipId != null) sb.append(" clip-path=\"url(#").append(clipId).append(")\"")
            sb.append(">\n")
            for (c in n.children) node(c, depth + 1)
            sb.append("</g>\n")
        }

        private fun image(n: ImageNode) {
            sb.append("<image width=\"").append(n.image.width).append("\" height=\"").append(n.image.height)
                .append("\" preserveAspectRatio=\"none\"")
            common(n)
            sb.append(" xlink:href=\"data:").append(n.image.mimeType).append(";base64,")
                .append(Base64.getEncoder().encodeToString(n.image.bytes)).append("\"/>\n")
        }

        private fun text(n: TextNode) {
            val warp = n.warp
            val shown = if (warp != null) (try { outlineText?.invoke(n) } catch (e: RuntimeException) { null }) ?: n.outline?.let { warp.apply(it) } else n.outline
            shown?.let { shapes ->
                // Özgün fontun harf biçimleri: metin yol olarak yazılır, içerik aria-label'da kalır.
                TAG = "<path"
                style(n.fill, n.stroke, null)
                common(n)
                sb.append(" aria-label=\"").append(esc(n.text)).append("\" d=\"").append(pathData(shapes)).append("\"/>\n")
                return
            }
            TAG = "<text"
            style(n.fill, n.stroke, null)
            common(n)
            sb.append(" font-family=\"").append(esc(n.fontFamily)).append("\" font-size=\"").append(num(n.fontSize)).append('"')
            if (n.bold) sb.append(" font-weight=\"bold\"")
            if (n.italic) sb.append(" font-style=\"italic\"")
            n.measuredWidth?.takeIf { it > 0 && n.text.length > 1 && n.text.indexOf('\n') < 0 }?.let {
                sb.append(" textLength=\"").append(num(it)).append("\" lengthAdjust=\"spacingAndGlyphs\"")
            }
            when (n.align) {
                io.github.mhmmtbg.mobileillustrator.model.TextAlign.Center -> sb.append(" text-anchor=\"middle\"")
                io.github.mhmmtbg.mobileillustrator.model.TextAlign.End -> sb.append(" text-anchor=\"end\"")
                else -> {}
            }
            sb.append(" xml:space=\"preserve\">")
            val lines = n.text.split('\n')
            if (lines.size == 1) {
                sb.append(esc(n.text))
            } else {
                for ((i, line) in lines.withIndex()) {
                    sb.append("<tspan x=\"0\" y=\"").append(num(i * n.fontSize * n.lineHeight)).append("\">").append(esc(line)).append("</tspan>")
                }
            }
            sb.append("</text>\n")
        }
    }
}
