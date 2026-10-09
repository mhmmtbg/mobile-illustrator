package io.github.mhmmtbg.mobileillustrator.ai

import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.Layer
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
import io.github.mhmmtbg.mobileillustrator.model.bounds
import io.github.mhmmtbg.mobileillustrator.pdf.PngDecoder
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Belgeyi PDF olarak yazar; `.ai` uzantısıyla kaydedildiğinde Illustrator bunu açar.
 *
 * Illustrator'ın kendi özel verisi (AIPrivateData) belgelenmemiş olduğu için yazılmaz; dosya
 * "PDF uyumlu .ai" dosyalarının PDF yarısıyla aynı yapıdadır: her çalışma yüzeyi bir sayfa,
 * her katman bir isteğe bağlı içerik grubu (OCG). Bu uygulama aynı dosyayı kayıpsız geri okur.
 */
object AiExporter {

    class Options(
        /**
         * Metni yola çevirir. Verilirse, standart fontlarla yazılamayan (Latin dışı) metinler
         * yola çevrilerek korunur; `null` ise o metinler atlanır.
         */
        val outlineText: ((TextNode) -> List<SubPath>?)? = null,
        /** Tüm metinleri yola çevir (font farkı olmasın). */
        val outlineAllText: Boolean = false,
        val creator: String = "Mobile Illustrator",
    )

    fun export(document: Document, options: Options = Options()): ByteArray = Writer(document, options).write()

    private class Writer(val doc: Document, val opt: Options) {
        val objects = ArrayList<ByteArray?>() // 1 tabanlı: objects[0] = nesne 1
        val extGStates = LinkedHashMap<String, String>() // tanım -> ad
        val patterns = ArrayList<Int>()
        val xobjects = ArrayList<Int>()
        val fonts = LinkedHashMap<String, Int>()
        val imageCache = java.util.IdentityHashMap<Any, Int>()
        val nodeOcgs = ArrayList<Triple<Int, Boolean, Boolean>>() // nesne no, görünür, kilitli
        var resourcesId = 0

        fun reserve(): Int { objects += null; return objects.size }
        fun set(id: Int, body: String) { objects[id - 1] = body.toByteArray(Charsets.ISO_8859_1) }
        fun add(body: String): Int { val id = reserve(); set(id, body); return id }

        fun stream(id: Int, dict: String, data: ByteArray, compress: Boolean = true) {
            val out = ByteArrayOutputStream(data.size / 2 + 128)
            val payload = if (compress) deflate(data) else data
            val filter = if (compress) " /Filter /FlateDecode" else ""
            out.write("<< $dict$filter /Length ${payload.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
            out.write(payload)
            out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
            objects[id - 1] = out.toByteArray()
        }

        fun deflate(data: ByteArray): ByteArray {
            val d = Deflater(6)
            d.setInput(data)
            d.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 64)
            val buf = ByteArray(64 * 1024)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }

        fun write(): ByteArray {
            val catalogId = reserve()
            val pagesId = reserve()
            resourcesId = reserve()
            val infoId = add("<< /Title ${textString(doc.name)} /Creator ${textString(opt.creator)} /Producer ${textString(opt.creator)} >>")

            val layerIds = doc.layers.map { add("<< /Type /OCG /Name ${textString(it.name)} >>") }

            val artboards = doc.artboards.ifEmpty {
                listOf(io.github.mhmmtbg.mobileillustrator.model.Artboard(bounds = Rect(0.0, 0.0, 612.0, 792.0)))
            }
            val pageIds = ArrayList<Int>()
            for (ab in artboards) {
                val b = ab.bounds
                val content = StringBuilder(1 shl 14)
                // Belge koordinatı (y aşağı) -> PDF sayfa koordinatı (y yukarı)
                val base = Matrix(1.0, 0.0, 0.0, -1.0, -b.left, b.bottom)
                content.append("q ").append(cm(base)).append('\n')
                for ((i, layer) in doc.layers.withIndex()) {
                    content.append("/OC /MC").append(i).append(" BDC\n")
                    layer(content, layer, base, if (artboards.size > 1) b else null)
                    content.append("EMC\n")
                }
                content.append("Q\n")
                val contentId = reserve()
                stream(contentId, "", content.toString().toByteArray(Charsets.ISO_8859_1))
                pageIds += add(
                    "<< /Type /Page /Parent $pagesId 0 R /MediaBox [0 0 ${n(b.width)} ${n(b.height)}] " +
                        "/TrimBox [0 0 ${n(b.width)} ${n(b.height)}] /Contents $contentId 0 R /Resources $resourcesId 0 R " +
                        "/Group << /S /Transparency /CS /DeviceRGB >> >>",
                )
            }

            val res = StringBuilder("<<")
            if (extGStates.isNotEmpty()) {
                res.append(" /ExtGState <<")
                for ((def, name) in extGStates) res.append(" /").append(name).append(' ').append(def)
                res.append(" >>")
            }
            if (patterns.isNotEmpty()) {
                res.append(" /Pattern <<")
                for ((i, id) in patterns.withIndex()) res.append(" /P").append(i).append(' ').append(id).append(" 0 R")
                res.append(" >>")
            }
            if (xobjects.isNotEmpty()) {
                res.append(" /XObject <<")
                for ((i, id) in xobjects.withIndex()) res.append(" /X").append(i).append(' ').append(id).append(" 0 R")
                res.append(" >>")
            }
            if (fonts.isNotEmpty()) {
                res.append(" /Font <<")
                for ((name, id) in fonts) res.append(" /").append(name).append(' ').append(id).append(" 0 R")
                res.append(" >>")
            }
            res.append(" /Properties <<")
            for ((i, id) in layerIds.withIndex()) res.append(" /MC").append(i).append(' ').append(id).append(" 0 R")
            for ((i, o) in nodeOcgs.withIndex()) res.append(" /MH").append(i).append(' ').append(o.first).append(" 0 R")
            res.append(" >> >>")
            set(resourcesId, res.toString())

            set(pagesId, "<< /Type /Pages /Count ${pageIds.size} /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] >>")
            fun refs(ids: List<Int>) = ids.joinToString(" ") { "$it 0 R" }
            val all = refs(layerIds + nodeOcgs.map { it.first })
            // Katman panelleri listeyi üstten alta gösterir; nesne grupları panelde görünmez.
            val order = refs(layerIds.asReversed())
            val off = refs(doc.layers.indices.filter { !doc.layers[it].visible }.map { layerIds[it] } + nodeOcgs.filter { !it.second }.map { it.first })
            val locked = refs(doc.layers.indices.filter { doc.layers[it].locked }.map { layerIds[it] } + nodeOcgs.filter { it.third }.map { it.first })
            set(
                catalogId,
                "<< /Type /Catalog /Pages $pagesId 0 R /OCProperties << /OCGs [$all] " +
                    "/D << /Order [$order] /OFF [$off] /Locked [$locked] >> >> >>",
            )

            val out = ByteArrayOutputStream(1 shl 16)
            fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
            w("%PDF-1.5\n%âãÏÓ\n")
            val offsets = IntArray(objects.size)
            for ((i, body) in objects.withIndex()) {
                offsets[i] = out.size()
                w("${i + 1} 0 obj\n")
                out.write(body ?: "null".toByteArray())
                w("\nendobj\n")
            }
            val xref = out.size()
            w("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            for (o in offsets) w(o.toString().padStart(10, '0') + " 00000 n \n")
            w("trailer\n<< /Size ${objects.size + 1} /Root $catalogId 0 R /Info $infoId 0 R >>\nstartxref\n$xref\n%%EOF\n")
            return out.toByteArray()
        }

        // ---- İçerik -------------------------------------------------------

        fun layer(sb: StringBuilder, layer: Layer, ctm: Matrix, pageBox: Rect?) {
            val nodes = if (pageBox == null) layer.children else layer.children.filter { it.bounds()?.intersects(pageBox) != false }
            if (nodes.isEmpty()) return
            if (layer.opacity < 1.0) {
                isolated(sb, layer.opacity, ctm) { inner -> for (n in nodes) node(inner, n, ctm) }
            } else {
                for (n in nodes) node(sb, n, ctm)
            }
        }

        /** İçeriği saydamlık grubu olarak yazar; opaklık parçalara tek tek değil bütüne uygulanır. */
        fun isolated(sb: StringBuilder, opacity: Double, ctm: Matrix, body: (StringBuilder) -> Unit) {
            val inner = StringBuilder()
            body(inner)
            val id = reserve()
            // Form kendi uzayında çizilir; çağrı anındaki dönüşüm aynen geçerli olduğu için kutu çok geniş tutulur.
            stream(
                id,
                "/Type /XObject /Subtype /Form /BBox [-1000000 -1000000 1000000 1000000] " +
                    "/Group << /S /Transparency /CS /DeviceRGB /I true >> /Resources $resourcesId 0 R",
                inner.toString().toByteArray(Charsets.ISO_8859_1),
            )
            xobjects += id
            sb.append("q ").append(gs(opacity, opacity)).append(" /X").append(xobjects.size - 1).append(" Do Q\n")
        }

        fun gs(fillAlpha: Double, strokeAlpha: Double): String {
            val def = "<< /ca ${n(fillAlpha.coerceIn(0.0, 1.0))} /CA ${n(strokeAlpha.coerceIn(0.0, 1.0))} >>"
            val name = extGStates.getOrPut(def) { "GS${extGStates.size}" }
            return "/$name gs"
        }

        fun node(sb: StringBuilder, node: Node, parent: Matrix) {
            // Gizli ya da kilitli nesneler kendi içerik grubuna sarılır: PDF görüntüleyiciler gizliyi göstermez,
            // bu uygulama da durumu geri okur.
            val flagged = !node.visible || node.locked
            if (flagged) {
                val id = add("<< /Type /OCG /Name ${textString(node.name)} >>")
                nodeOcgs += Triple(id, node.visible, node.locked)
                sb.append("/OC /MH").append(nodeOcgs.size - 1).append(" BDC\n")
            }
            emitNode(sb, node, parent)
            if (flagged) sb.append("EMC\n")
        }

        fun emitNode(sb: StringBuilder, node: Node, parent: Matrix) {
            val ctm = parent * node.transform
            when (node) {
                is PathNode -> {
                    val named = node.name !in DefaultNames
                    if (named) sb.append("/MI << /N ").append(textString(node.name)).append(" >> BDC\n")
                    sb.append("q\n")
                    if (!node.transform.isIdentity) sb.append(cm(node.transform)).append('\n')
                    paintPath(sb, node.subpaths, node.fill, node.stroke, node.fillRule, node.opacity, ctm)
                    sb.append("Q\n")
                    if (named) sb.append("EMC\n")
                }
                is GroupNode -> {
                    sb.append("/MI << /N ").append(textString(node.name)).append(" /T /Group >> BDC\n")
                    val body: (StringBuilder) -> Unit = { s ->
                        s.append("q\n")
                        if (!node.transform.isIdentity) s.append(cm(node.transform)).append('\n')
                        node.clip?.let { c ->
                            pathOps(s, c.subpaths)
                            s.append(if (c.rule == FillRule.EvenOdd) "W* n\n" else "W n\n")
                        }
                        for (child in node.children) node(s, child, ctm)
                        s.append("Q\n")
                    }
                    if (node.opacity < 1.0) isolated(sb, node.opacity, parent, body) else body(sb)
                    sb.append("EMC\n")
                }
                is ImageNode -> image(sb, node)
                is TextNode -> text(sb, node, ctm)
            }
        }

        fun pathOps(sb: StringBuilder, subpaths: List<SubPath>) {
            for (sp in subpaths) {
                val a = sp.anchors
                if (a.isEmpty()) continue
                sb.append(n(a[0].point.x)).append(' ').append(n(a[0].point.y)).append(" m\n")
                val segs = sp.segments()
                for ((i, s) in segs.withIndex()) {
                    if (s.isLine) {
                        // Kapalı yolun son doğru parçasını "h" çizer.
                        if (sp.closed && i == segs.size - 1) continue
                        sb.append(n(s.p3.x)).append(' ').append(n(s.p3.y)).append(" l\n")
                    } else {
                        sb.append(n(s.p1.x)).append(' ').append(n(s.p1.y)).append(' ')
                            .append(n(s.p2.x)).append(' ').append(n(s.p2.y)).append(' ')
                            .append(n(s.p3.x)).append(' ').append(n(s.p3.y)).append(" c\n")
                    }
                }
                if (sp.closed) sb.append("h\n")
            }
        }

        fun paintPath(sb: StringBuilder, subpaths: List<SubPath>, fill: Paint?, stroke: Stroke?, rule: FillRule, opacity: Double, ctm: Matrix) {
            val st = stroke?.takeIf { it.width > 0 }
            if (fill == null && st == null) return
            val fa = opacity * alphaOf(fill)
            val sa = opacity * alphaOf(st?.paint)
            if (fa < 1.0 || sa < 1.0) sb.append(gs(fa, sa)).append('\n')
            if (fill != null) setPaint(sb, fill, false, ctm)
            if (st != null) {
                setPaint(sb, st.paint, true, ctm)
                strokeState(sb, st)
            }
            pathOps(sb, subpaths)
            val eo = rule == FillRule.EvenOdd
            sb.append(
                when {
                    fill != null && st != null -> if (eo) "B*\n" else "B\n"
                    fill != null -> if (eo) "f*\n" else "f\n"
                    else -> "S\n"
                },
            )
        }

        fun strokeState(sb: StringBuilder, st: Stroke) {
            sb.append(n(st.width)).append(" w ")
            sb.append(when (st.cap) { LineCap.Butt -> 0; LineCap.Round -> 1; LineCap.Square -> 2 }).append(" J ")
            sb.append(when (st.join) { LineJoin.Miter -> 0; LineJoin.Round -> 1; LineJoin.Bevel -> 2 }).append(" j ")
            sb.append(n(st.miterLimit.coerceAtLeast(1.0))).append(" M")
            val dash = st.dash.filter { it >= 0 }
            if (dash.isNotEmpty() && dash.sum() > 0) {
                sb.append(" [").append(dash.joinToString(" ") { n(it) }).append("] ").append(n(st.dashOffset)).append(" d")
            }
            sb.append('\n')
        }

        fun alphaOf(p: Paint?): Double = when (p) {
            null -> 1.0
            is Paint.Solid -> p.color.a
            is Paint.LinearGradient -> p.stops.minOfOrNull { it.color.a } ?: 1.0
            is Paint.RadialGradient -> p.stops.minOfOrNull { it.color.a } ?: 1.0
        }

        fun setPaint(sb: StringBuilder, p: Paint, stroke: Boolean, ctm: Matrix) {
            when (p) {
                is Paint.Solid -> sb.append(n(p.color.r)).append(' ').append(n(p.color.g)).append(' ').append(n(p.color.b))
                    .append(if (stroke) " RG\n" else " rg\n")
                is Paint.LinearGradient -> pattern(sb, stroke, ctm * p.transform, p.stops) {
                    "/ShadingType 2 /Coords [${n(p.start.x)} ${n(p.start.y)} ${n(p.end.x)} ${n(p.end.y)}]"
                }
                is Paint.RadialGradient -> pattern(sb, stroke, ctm * p.transform, p.stops) {
                    "/ShadingType 3 /Coords [${n(p.center.x)} ${n(p.center.y)} 0 ${n(p.center.x)} ${n(p.center.y)} ${n(p.radius)}]"
                }
            }
        }

        fun pattern(sb: StringBuilder, stroke: Boolean, matrix: Matrix, stops: List<GradientStop>, geometry: () -> String) {
            val fn = gradientFunction(stops)
            val id = add(
                "<< /Type /Pattern /PatternType 2 /Matrix [${mat(matrix)}] /Shading << ${geometry()} " +
                    "/ColorSpace /DeviceRGB /Function $fn /Extend [true true] >> >>",
            )
            patterns += id
            val name = "/P${patterns.size - 1}"
            sb.append(if (stroke) "/Pattern CS $name SCN\n" else "/Pattern cs $name scn\n")
        }

        fun gradientFunction(raw: List<GradientStop>): String {
            var stops = raw.sortedBy { it.offset }.map { it.copy(offset = it.offset.coerceIn(0.0, 1.0)) }
            if (stops.isEmpty()) stops = listOf(GradientStop(0.0, io.github.mhmmtbg.mobileillustrator.model.Rgba.Black))
            if (stops.first().offset > 0.0) stops = listOf(stops.first().copy(offset = 0.0)) + stops
            if (stops.last().offset < 1.0) stops = stops + stops.last().copy(offset = 1.0)
            fun c(s: GradientStop) = "[${n(s.color.r)} ${n(s.color.g)} ${n(s.color.b)}]"
            fun seg(a: GradientStop, b: GradientStop) = "<< /FunctionType 2 /Domain [0 1] /C0 ${c(a)} /C1 ${c(b)} /N 1 >>"
            if (stops.size == 2) return seg(stops[0], stops[1])
            val fns = StringBuilder()
            val bounds = StringBuilder()
            val encode = StringBuilder()
            var last = 0.0
            for (i in 0 until stops.size - 1) {
                fns.append(seg(stops[i], stops[i + 1])).append(' ')
                encode.append("0 1 ")
                if (i > 0) {
                    // Sınırlar kesin artan olmalı; üst üste binen duraklar çok az kaydırılır.
                    val b = maxOf(stops[i].offset, last + 1e-5).coerceAtMost(1.0 - 1e-5 * (stops.size - 1 - i))
                    bounds.append(n6(b)).append(' ')
                    last = b
                }
            }
            return "<< /FunctionType 3 /Domain [0 1] /Functions [$fns] /Bounds [$bounds] /Encode [$encode] >>"
        }

        // ---- Görseller ----------------------------------------------------

        fun image(sb: StringBuilder, node: ImageNode) {
            val img = node.image
            val id = imageCache.getOrPut(img) { embed(img.bytes, img.mimeType, img.width, img.height) ?: -1 }
            if (id < 0) return
            val index = xobjects.indexOf(id).let { if (it >= 0) it else { xobjects += id; xobjects.size - 1 } }
            sb.append("q\n")
            if (node.opacity < 1.0) sb.append(gs(node.opacity, node.opacity)).append('\n')
            // Görsel uzayı birim karedir ve y yukarıdır; piksel uzayına (y aşağı) çevrilir.
            val m = node.transform * Matrix(img.width.toDouble(), 0.0, 0.0, -img.height.toDouble(), 0.0, img.height.toDouble())
            sb.append(cm(m)).append(" /X").append(index).append(" Do\nQ\n")
        }

        fun embed(bytes: ByteArray, mime: String, w: Int, h: Int): Int? {
            if (mime == "image/jpeg") {
                val comps = PngDecoder.jpegComponents(bytes) ?: 3
                val cs = when (comps) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK"; else -> "/DeviceRGB" }
                val id = reserve()
                val out = ByteArrayOutputStream(bytes.size + 256)
                out.write(
                    ("<< /Type /XObject /Subtype /Image /Width $w /Height $h /ColorSpace $cs /BitsPerComponent 8 " +
                        "/Filter /DCTDecode /Length ${bytes.size} >>\nstream\n").toByteArray(Charsets.ISO_8859_1),
                )
                out.write(bytes)
                out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
                objects[id - 1] = out.toByteArray()
                return id
            }
            val raw = PngDecoder.decode(bytes) ?: return null
            var smask = ""
            raw.alpha?.let { a ->
                val sid = reserve()
                stream(sid, "/Type /XObject /Subtype /Image /Width ${raw.width} /Height ${raw.height} /ColorSpace /DeviceGray /BitsPerComponent 8", a)
                smask = " /SMask $sid 0 R"
            }
            val id = reserve()
            stream(id, "/Type /XObject /Subtype /Image /Width ${raw.width} /Height ${raw.height} /ColorSpace /DeviceRGB /BitsPerComponent 8$smask", raw.rgb)
            return id
        }

        // ---- Metin --------------------------------------------------------

        fun text(sb: StringBuilder, node: TextNode, ctm: Matrix) {
            val encoded = if (opt.outlineAllText) null else encode(node.text)
            // Metnin özellikleri ayrıca saklanır: bu uygulama dosyayı geri açtığında metin düzenlenebilir kalır.
            sb.append("/MI << /N ").append(textString(node.name)).append(" /T /Text /S ").append(textString(node.text))
                .append(" /F ").append(textString(node.fontFamily)).append(" /Z ").append(n6(node.fontSize))
                .append(" /B ").append(node.bold).append(" /I ").append(node.italic)
                .append(" /M [").append(mat(node.transform)).append("] >> BDC\n")
            sb.append("q\n")
            if (!node.transform.isIdentity) sb.append(cm(node.transform)).append('\n')
            if (encoded != null) {
                val fa = node.opacity * alphaOf(node.fill)
                val sa = node.opacity * alphaOf(node.stroke?.paint)
                if (fa < 1.0 || sa < 1.0) sb.append(gs(fa, sa)).append('\n')
                node.fill?.let { setPaint(sb, it, false, ctm) }
                node.stroke?.let { setPaint(sb, it.paint, true, ctm); strokeState(sb, it) }
                val mode = when {
                    node.fill != null && node.stroke != null -> 2
                    node.stroke != null -> 1
                    node.fill != null -> 0
                    else -> 3
                }
                sb.append("BT /").append(font(node)).append(' ').append(n(node.fontSize)).append(" Tf ").append(mode)
                    .append(" Tr 1 0 0 -1 0 0 Tm ").append(encoded).append(" Tj ET\n")
            } else {
                val outline = opt.outlineText?.invoke(node)
                if (outline != null) paintPath(sb, outline, node.fill, node.stroke, FillRule.NonZero, node.opacity, ctm)
            }
            sb.append("Q\nEMC\n")
        }

        fun font(node: TextNode): String {
            val family = node.fontFamily.lowercase()
            val base = when {
                family.contains("mono") || family.contains("courier") -> when {
                    node.bold && node.italic -> "Courier-BoldOblique"; node.bold -> "Courier-Bold"
                    node.italic -> "Courier-Oblique"; else -> "Courier"
                }
                family.contains("serif") && !family.contains("sans") -> when {
                    node.bold && node.italic -> "Times-BoldItalic"; node.bold -> "Times-Bold"
                    node.italic -> "Times-Italic"; else -> "Times-Roman"
                }
                else -> when {
                    node.bold && node.italic -> "Helvetica-BoldOblique"; node.bold -> "Helvetica-Bold"
                    node.italic -> "Helvetica-Oblique"; else -> "Helvetica"
                }
            }
            val name = "F" + base.replace("-", "")
            fonts.getOrPut(name) {
                add(
                    "<< /Type /Font /Subtype /Type1 /BaseFont /$base /Encoding << /Type /Encoding /BaseEncoding /WinAnsiEncoding " +
                        "/Differences [208 /Gbreve 221 /Idotaccent /Scedilla 240 /gbreve 253 /dotlessi /scedilla] >> >>",
                )
            }
            return name
        }

        /** Latin-1 + Türkçe harfler tek baytla yazılabilir; başka karakter varsa `null`. */
        fun encode(text: String): String? {
            val sb = StringBuilder("(")
            for (ch in text) {
                val code: Int = when (ch) {
                    'Ğ' -> 208; 'İ' -> 221; 'Ş' -> 222; 'ğ' -> 240; 'ı' -> 253; 'ş' -> 254
                    else -> {
                        if (ch.code in 32..126 || (ch.code in 160..255 && ch.code !in TurkishSlots)) {
                            ch.code
                        } else {
                            val b = Cp1252?.let { cs -> ch.toString().toByteArray(cs).singleOrNull()?.toInt()?.and(0xFF) }
                            if (b == null || b == '?'.code || b !in 128..159) return null
                            b
                        }
                    }
                }
                when (code) {
                    '('.code, ')'.code, '\\'.code -> sb.append('\\').append(code.toChar())
                    in 32..126 -> sb.append(code.toChar())
                    else -> sb.append('\\').append(code.toString(8).padStart(3, '0'))
                }
            }
            return sb.append(')').toString()
        }

        // ---- Biçimlendirme ------------------------------------------------

        fun cm(m: Matrix) = "${mat(m)} cm"
        fun mat(m: Matrix) = "${n6(m.a)} ${n6(m.b)} ${n6(m.c)} ${n6(m.d)} ${n(m.e)} ${n(m.f)}"

        fun n(v: Double): String = fixed(v, 10000, 4)
        fun n6(v: Double): String = fixed(v, 1000000, 6)

        fun fixed(v: Double, scale: Long, digits: Int): String {
            if (v.isNaN() || v.isInfinite()) return "0"
            val r = (v * scale).roundToLong()
            if (r % scale == 0L) return (r / scale).toString()
            val a = abs(r)
            val frac = (a % scale).toString().padStart(digits, '0').trimEnd('0')
            return (if (r < 0) "-" else "") + (a / scale) + "." + frac
        }

        /** PDF metin dizgisi: ASCII ise düz, değilse UTF-16BE onaltılık. */
        fun textString(s: String): String {
            if (s.all { it.code in 32..126 && it != '(' && it != ')' && it != '\\' }) return "($s)"
            val sb = StringBuilder("<FEFF")
            for (b in s.toByteArray(Charsets.UTF_16BE)) sb.append("%02X".format(b.toInt() and 0xFF))
            return sb.append('>').toString()
        }
    }

    private val DefaultNames = setOf("Yol", "Grup", "Kırpma grubu", "Görsel")
    private val TurkishSlots = setOf(208, 221, 222, 240, 253, 254)
    private val Cp1252: Charset? = try { Charset.forName("windows-1252") } catch (e: Exception) { null }
}
