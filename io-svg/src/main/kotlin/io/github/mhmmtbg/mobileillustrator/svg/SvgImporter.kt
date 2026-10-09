package io.github.mhmmtbg.mobileillustrator.svg

import io.github.mhmmtbg.mobileillustrator.model.tr
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.Artboard
import io.github.mhmmtbg.mobileillustrator.model.BlendMode
import io.github.mhmmtbg.mobileillustrator.model.ClipPath
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.ImportException
import io.github.mhmmtbg.mobileillustrator.model.ImportResult
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.withTransform
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/** SVG dosyalarını belgeye çevirir. Üst düzey gruplar katman olur (Illustrator ve Inkscape böyle yazar). */
object SvgImporter {

    fun sniff(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        return head.contains("<svg") && !head.startsWith("%PDF")
    }

    fun import(bytes: ByteArray, name: String): ImportResult {
        val root = try {
            if (bytes.size > 96 * 1024 * 1024) throw ImportException(tr("SVG dosyası çok büyük"))
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = false
            // Varlık açılımı bombalarına ("billion laughs") karşı ayrıştırıcının kendi sınırları
            try { f.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) } catch (e: Exception) { /* desteklenmiyor */ }
            for (feature in listOf(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
            )) {
                try { f.setFeature(feature, false) } catch (e: Exception) { /* ayrıştırıcı desteklemiyor */ }
            }
            val b = f.newDocumentBuilder()
            // Dış DTD ve varlıklar asla indirilmez.
            b.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            // Ayrıştırma hataları istisna olarak döner; konsola yazılmasın.
            b.setErrorHandler(object : org.xml.sax.ErrorHandler {
                override fun warning(e: org.xml.sax.SAXParseException) {}
                override fun error(e: org.xml.sax.SAXParseException) {}
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
            b.parse(ByteArrayInputStream(bytes)).documentElement
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException(tr("SVG dosyası okunamadı: %s", e.message ?: tr("geçersiz XML")))
        } catch (e: StackOverflowError) {
            throw ImportException(tr("SVG dosyası çok derin iç içe yapı içeriyor"))
        } catch (e: OutOfMemoryError) {
            throw ImportException(tr("SVG dosyası belleğe sığmayacak kadar büyük"))
        }
        if (root == null || root.tagName.substringAfter(':') != "svg") throw ImportException(tr("Dosya bir SVG değil"))
        try {
            return Reader(root, name).run()
        } catch (e: ImportException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw ImportException(tr("Dosya belleğe sığmayacak kadar büyük ya da karmaşık"))
        } catch (e: StackOverflowError) {
            throw ImportException(tr("Dosya çok derin iç içe yapı içeriyor"))
        } catch (e: RuntimeException) {
            throw ImportException(tr("SVG dosyası bozuk görünüyor ve okunamadı"))
        }
    }

    private class Style(
        val fill: String = "black",
        val stroke: String = "none",
        val strokeWidth: Double = 1.0,
        val cap: LineCap = LineCap.Butt,
        val join: LineJoin = LineJoin.Miter,
        val miter: Double = 4.0,
        val dash: List<Double> = emptyList(),
        val dashOffset: Double = 0.0,
        val fillOpacity: Double = 1.0,
        val strokeOpacity: Double = 1.0,
        val fillRule: FillRule = FillRule.NonZero,
        val clipRule: FillRule = FillRule.NonZero,
        val color: String = "black",
        val fontSize: Double = 16.0,
        val fontFamily: String = "sans-serif",
        val bold: Boolean = false,
        val italic: Boolean = false,
        val anchor: String = "start",
        val visible: Boolean = true,
    )

    private class Reader(val root: Element, val name: String) {
        val warnings = LinkedHashSet<String>()
        val ids = HashMap<String, Element>()
        val rules = ArrayList<Pair<String, Map<String, String>>>() // seçici -> bildirimler
        var viewW = 100.0
        var viewH = 100.0
        var useDepth = 0
        var nodeDepth = 0
        var nodeCount = 0

        fun run(): ImportResult {
            index(root)
            val vb = root.getAttribute("viewBox").split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
            val hasVb = vb.size == 4 && vb[2] > 0 && vb[3] > 0
            viewW = if (hasVb) vb[2] else 300.0
            viewH = if (hasVb) vb[3] else 150.0
            val w = length(root.getAttribute("width"), viewW).takeIf { root.hasAttribute("width") && it > 0 } ?: viewW
            val h = length(root.getAttribute("height"), viewH).takeIf { root.hasAttribute("height") && it > 0 } ?: viewH
            if (!hasVb) { viewW = w; viewH = h }
            // viewBox'ı sayfa boyutuna oturt (xMidYMid meet)
            var base = Matrix.Identity
            if (hasVb) {
                val s = minOf(w / vb[2], h / vb[3])
                base = Matrix.translate((w - vb[2] * s) / 2, (h - vb[3] * s) / 2) * Matrix.scale(s) * Matrix.translate(-vb[0], -vb[1])
            }
            val rootStyle = styleOf(root, Style())
            val layers = ArrayList<Layer>()
            var loose = ArrayList<Node>()
            var layerIndex = 0
            fun flushLoose() {
                if (loose.isNotEmpty()) {
                    layerIndex++
                    layers += Layer(name = "Katman $layerIndex", children = loose)
                    loose = ArrayList()
                }
            }
            for (child in elements(root)) {
                val tag = child.tagName.substringAfter(':')
                if (tag == "g" && looksLikeLayer(child)) {
                    flushLoose()
                    layerIndex++
                    val props = declarations(child)
                    val st = styleOf(child, rootStyle, props)
                    val kids = ArrayList<Node>()
                    val plain = !child.hasAttribute("transform") && props["clip-path"].let { it == null || it == "none" } && base.isIdentity
                    if (plain) {
                        for (c in elements(child)) node(c, st)?.let(kids::add)
                    } else {
                        // Dönüşümü ya da kırpması olan katman: içerik bir grupta korunur.
                        (group(child, st, props) as? GroupNode)?.let { g ->
                            kids += if (base.isIdentity) g.copy(opacity = 1.0) else g.copy(opacity = 1.0, transform = base * g.transform)
                        }
                    }
                    layers += Layer(
                        name = labelOf(child) ?: "Katman $layerIndex",
                        visible = st.visible && props["display"] != "none",
                        opacity = number(props["opacity"], 1.0).coerceIn(0.0, 1.0),
                        children = kids,
                    )
                } else {
                    val n = node(child, rootStyle) ?: continue
                    loose += if (base.isIdentity) n else n.withTransform(base * n.transform)
                }
            }
            flushLoose()
            if (layers.isEmpty()) layers += Layer(name = "Katman 1")
            val doc = Document(name = name, artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, w, h))), layers = layers)
            return ImportResult(doc, warnings.toList())
        }

        private fun looksLikeLayer(g: Element): Boolean =
            g.getAttribute("inkscape:groupmode") == "layer" || g.hasAttribute("data-name") || g.hasAttribute("id") || g.hasAttribute("inkscape:label")

        private fun labelOf(e: Element): String? {
            val raw = e.getAttribute("inkscape:label").ifEmpty { e.getAttribute("data-name") }.ifEmpty { e.getAttribute("id") }
            if (raw.isEmpty()) return null
            // Illustrator kimliklerde özel karakterleri _x20_ biçiminde kodlar.
            return Regex("_x([0-9A-Fa-f]{2,4})_").replace(raw) { it.groupValues[1].toInt(16).toChar().toString() }.replace('_', ' ').trim().ifEmpty { null }
        }

        private fun elements(e: Element): List<Element> {
            val out = ArrayList<Element>()
            var c = e.firstChild
            while (c != null) {
                if (c is Element) out += c
                c = c.nextSibling
            }
            return out
        }

        private fun index(e: Element) {
            e.getAttribute("id").takeIf { it.isNotEmpty() }?.let { ids[it] = e }
            if (e.tagName.substringAfter(':') == "style") parseCss(e.textContent ?: "")
            for (c in elements(e)) index(c)
        }

        private fun parseCss(css: String) {
            val clean = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            for (m in Regex("([^{}]+)\\{([^{}]*)\\}").findAll(clean)) {
                val decl = parseDeclarations(m.groupValues[2])
                for (sel in m.groupValues[1].split(',')) {
                    val s = sel.trim()
                    if (s.isNotEmpty() && !s.startsWith("@")) rules += s to decl
                }
            }
        }

        private fun parseDeclarations(s: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (part in s.split(';')) {
                val i = part.indexOf(':')
                if (i <= 0) continue
                out[part.substring(0, i).trim().lowercase()] = part.substring(i + 1).trim().removeSuffix("!important").trim()
            }
            return out
        }

        /** Sunum öznitelikleri < stil sayfası kuralları < satır içi stil. */
        private fun declarations(e: Element): Map<String, String> {
            val out = HashMap<String, String>()
            val attrs = e.attributes
            for (i in 0 until attrs.length) {
                val a = attrs.item(i)
                out[a.nodeName.lowercase()] = a.nodeValue.trim()
            }
            if (rules.isNotEmpty()) {
                val tag = e.tagName.substringAfter(':')
                val classes = e.getAttribute("class").split(Regex("\\s+")).filter { it.isNotEmpty() }
                val id = e.getAttribute("id")
                for ((sel, decl) in rules) {
                    val last = sel.substringAfterLast(' ')
                    val matches = when {
                        last.startsWith(".") -> last.substring(1) in classes
                        last.startsWith("#") -> last.substring(1) == id
                        last.contains('.') -> last.substringBefore('.') == tag && last.substringAfter('.') in classes
                        last == "*" -> true
                        else -> last == tag
                    }
                    if (matches) out.putAll(decl)
                }
            }
            e.getAttribute("style").takeIf { it.isNotEmpty() }?.let { out.putAll(parseDeclarations(it)) }
            return out
        }

        private fun styleOf(e: Element, p: Style, d: Map<String, String> = declarations(e)): Style {
            fun inh(key: String) = d[key]?.takeIf { it != "inherit" }
            val fontSize = inh("font-size")?.let { length(it, p.fontSize, em = p.fontSize) } ?: p.fontSize
            return Style(
                fill = inh("fill") ?: p.fill,
                stroke = inh("stroke") ?: p.stroke,
                strokeWidth = inh("stroke-width")?.let { length(it, viewW) } ?: p.strokeWidth,
                cap = when (inh("stroke-linecap")) { "round" -> LineCap.Round; "square" -> LineCap.Square; "butt" -> LineCap.Butt; else -> p.cap },
                join = when (inh("stroke-linejoin")) { "round" -> LineJoin.Round; "bevel" -> LineJoin.Bevel; "miter" -> LineJoin.Miter; else -> p.join },
                miter = inh("stroke-miterlimit")?.toDoubleOrNull() ?: p.miter,
                dash = inh("stroke-dasharray")?.let { v ->
                    if (v == "none") emptyList() else v.split(Regex("[\\s,]+")).mapNotNull { it.takeIf { s -> s.isNotEmpty() }?.let { s -> length(s, viewW) } }
                } ?: p.dash,
                dashOffset = inh("stroke-dashoffset")?.let { length(it, viewW) } ?: p.dashOffset,
                fillOpacity = inh("fill-opacity")?.let { number(it, 1.0) } ?: p.fillOpacity,
                strokeOpacity = inh("stroke-opacity")?.let { number(it, 1.0) } ?: p.strokeOpacity,
                fillRule = when (inh("fill-rule")) { "evenodd" -> FillRule.EvenOdd; "nonzero" -> FillRule.NonZero; else -> p.fillRule },
                clipRule = when (inh("clip-rule")) { "evenodd" -> FillRule.EvenOdd; "nonzero" -> FillRule.NonZero; else -> p.clipRule },
                color = inh("color") ?: p.color,
                fontSize = fontSize,
                fontFamily = inh("font-family")?.let(::genericFamily) ?: p.fontFamily,
                bold = inh("font-weight")?.let { it == "bold" || it == "bolder" || (it.toIntOrNull() ?: 400) >= 600 } ?: p.bold,
                italic = inh("font-style")?.let { it == "italic" || it == "oblique" } ?: p.italic,
                anchor = inh("text-anchor") ?: p.anchor,
                visible = when (inh("visibility")) { "hidden", "collapse" -> false; "visible" -> true; else -> p.visible },
            )
        }

        private fun genericFamily(v: String): String {
            val l = v.lowercase()
            return when {
                l.contains("mono") || l.contains("courier") || l.contains("consol") -> "monospace"
                (l.contains("serif") && !l.contains("sans")) || l.contains("times") || l.contains("georgia") || l.contains("garamond") -> "serif"
                else -> "sans-serif"
            }
        }

        private fun number(v: String?, def: Double): Double {
            v ?: return def
            val t = v.trim()
            if (t.endsWith("%")) return (t.dropLast(1).toDoubleOrNull() ?: return def) / 100
            return t.toDoubleOrNull() ?: def
        }

        private fun length(v: String?, percentOf: Double, em: Double = 16.0): Double {
            val t = v?.trim().orEmpty()
            if (t.isEmpty()) return 0.0
            val m = Regex("^([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)\\s*([a-zA-Z%]*)$").find(t) ?: return 0.0
            val n = m.groupValues[1].toDoubleOrNull() ?: return 0.0
            return when (m.groupValues[2].lowercase()) {
                "", "px" -> n
                "pt" -> n * 96 / 72
                "pc" -> n * 16
                "mm" -> n * 96 / 25.4
                "cm" -> n * 96 / 2.54
                "in" -> n * 96
                "em", "rem" -> n * em
                "ex" -> n * em / 2
                "%" -> n / 100 * percentOf
                else -> n
            }
        }

        private fun attr(e: Element, name: String, percentOf: Double = viewW) = length(e.getAttribute(name), percentOf)

        // ---- Düğümler -----------------------------------------------------

        private fun node(e: Element, parent: Style): Node? {
            if (nodeDepth > 256 || nodeCount > 400_000) {
                warnings += tr("Dosya çok büyük ya da çok derin; bir kısmı açıldı")
                return null
            }
            nodeCount++
            nodeDepth++
            try {
                return nodeInner(e, parent)
            } finally {
                nodeDepth--
            }
        }

        private fun nodeInner(e: Element, parent: Style): Node? {
            val tag = e.tagName.substringAfter(':')
            if (tag in Skipped) return null
            val props = declarations(e)
            if (props["display"] == "none") return null
            val st = styleOf(e, parent, props)
            val built: Node? = when (tag) {
                "g", "a", "svg", "switch" -> group(e, st, props)
                "use" -> use(e, st, props)
                "image" -> image(e, props)
                "text" -> text(e, st, props)
                else -> shape(e, tag)?.let { path(e, it, st, props) }
            }
            return built?.let { wrapClip(it, props, st) }
        }

        private fun common(n: Node, e: Element, props: Map<String, String>, st: Style): Node {
            val m = props["transform"]?.let(::parseTransform) ?: Matrix.Identity
            val op = number(props["opacity"], 1.0).coerceIn(0.0, 1.0)
            val nm = labelOf(e)
            val bm = BlendMode.fromCss(props["mix-blend-mode"]) ?: BlendMode.Normal
            return when (n) {
                is PathNode -> n.copy(transform = m, opacity = op, visible = st.visible, name = nm ?: n.name, blendMode = bm)
                is GroupNode -> n.copy(transform = m, opacity = op, name = nm ?: n.name, blendMode = bm)
                is ImageNode -> n.copy(transform = m * n.transform, opacity = op, visible = st.visible, name = nm ?: n.name, blendMode = bm)
                is TextNode -> n.copy(transform = m * n.transform, opacity = op, visible = st.visible, name = nm ?: n.name, blendMode = bm)
            }
        }

        private fun wrapClip(n: Node, props: Map<String, String>, st: Style): Node {
            val ref = props["clip-path"]?.let(::urlId) ?: return n
            val cp = ids[ref] ?: return n
            if (cp.getAttribute("clipPathUnits") == "objectBoundingBox") {
                warnings += tr("Nesne kutusuna göre tanımlı kırpma yolları yok sayıldı")
                return n
            }
            val subpaths = ArrayList<SubPath>()
            var rule = FillRule.NonZero
            val cpM = cp.getAttribute("transform").takeIf { it.isNotEmpty() }?.let(::parseTransform) ?: Matrix.Identity
            fun collect(el: Element, m: Matrix, depth: Int) {
                if (depth > 8) return
                for (c in elements(el)) {
                    val tag = c.tagName.substringAfter(':')
                    val d = declarations(c)
                    val cm = m * (d["transform"]?.let(::parseTransform) ?: Matrix.Identity)
                    if (d["clip-rule"] == "evenodd") rule = FillRule.EvenOdd
                    when (tag) {
                        "g" -> collect(c, cm, depth + 1)
                        "use" -> ids[hrefOf(c)]?.let { target ->
                            val t = cm * Matrix.translate(attr(c, "x"), attr(c, "y", viewH))
                            shape(target, target.tagName.substringAfter(':'))?.let { sp -> subpaths += sp.map { it.transformed(t) } }
                        }
                        else -> shape(c, tag)?.let { sp -> subpaths += sp.map { it.transformed(cm) } }
                    }
                }
            }
            collect(cp, cpM, 0)
            if (subpaths.isEmpty()) return n
            // Kırpma yolu, kırpılan öğenin kendi dönüşümünden önceki uzaydadır: öğeyi sarmalayan grupla ifade edilir.
            return when (n) {
                is GroupNode -> if (n.clip == null) {
                    n.copy(clip = ClipPath(subpaths, rule))
                } else {
                    GroupNode(name = "Kırpma grubu", children = listOf(n.copy(transform = Matrix.Identity)), clip = ClipPath(subpaths, rule), transform = n.transform)
                }
                is PathNode -> GroupNode(
                    name = "Kırpma grubu",
                    children = listOf(n.copy(transform = Matrix.Identity)),
                    clip = ClipPath(subpaths, rule),
                    transform = n.transform,
                )
                // Görsel ve metinlerde yerleştirme dönüşümü öğenin parçasıdır; kırpma dış uzayda bırakılır.
                else -> GroupNode(name = "Kırpma grubu", children = listOf(n), clip = ClipPath(subpaths, rule))
            }
        }

        private fun group(e: Element, st: Style, props: Map<String, String>): Node? {
            val kids = ArrayList<Node>()
            for (c in elements(e)) node(c, st)?.let(kids::add)
            if (kids.isEmpty()) return null
            return common(GroupNode(children = kids), e, props, st)
        }

        private fun hrefOf(e: Element): String =
            e.getAttribute("href").ifEmpty { e.getAttribute("xlink:href") }.removePrefix("#")

        private fun use(e: Element, st: Style, props: Map<String, String>): Node? {
            val target = ids[hrefOf(e)] ?: return null
            if (useDepth > 12) return null
            useDepth++
            val inner = try {
                val tag = target.tagName.substringAfter(':')
                if (tag == "symbol") group(target, styleOf(target, st), declarations(target)) else node(target, st)
            } finally {
                useDepth--
            } ?: return null
            val g = common(GroupNode(children = listOf(inner)), e, props, st) as GroupNode
            return g.copy(transform = g.transform * Matrix.translate(attr(e, "x"), attr(e, "y", viewH)))
        }

        private fun image(e: Element, props: Map<String, String>): Node? {
            val href = e.getAttribute("href").ifEmpty { e.getAttribute("xlink:href") }
            if (!href.startsWith("data:")) {
                warnings += tr("Dış dosyaya bağlı görseller atlandı")
                return null
            }
            val comma = href.indexOf(',')
            if (comma < 0) return null
            val meta = href.substring(5, comma)
            val mime = meta.substringBefore(';').lowercase()
            if (mime != "image/png" && mime != "image/jpeg" && mime != "image/jpg") {
                warnings += tr("Desteklenmeyen gömülü görsel türü: %s", mime)
                return null
            }
            val bytes = try {
                Base64.getMimeDecoder().decode(href.substring(comma + 1))
            } catch (e2: IllegalArgumentException) {
                return null
            }
            val size = imageSize(bytes) ?: return null
            val w = attr(e, "width").takeIf { it > 0 } ?: size.first.toDouble()
            val h = attr(e, "height", viewH).takeIf { it > 0 } ?: size.second.toDouble()
            val local = Matrix.translate(attr(e, "x"), attr(e, "y", viewH)) * Matrix.scale(w / size.first, h / size.second)
            val n = ImageNode(image = ImageData(bytes, if (mime == "image/png") "image/png" else "image/jpeg", size.first, size.second), transform = local)
            return common(n, e, props, Style())
        }

        private fun imageSize(b: ByteArray): Pair<Int, Int>? {
            fun u(i: Int) = b[i].toInt() and 0xFF
            if (b.size > 24 && u(0) == 0x89 && u(1) == 'P'.code) {
                return ((u(16) shl 24) or (u(17) shl 16) or (u(18) shl 8) or u(19)) to ((u(20) shl 24) or (u(21) shl 16) or (u(22) shl 8) or u(23))
            }
            var p = 2
            while (p + 9 < b.size) {
                if (u(p) != 0xFF) { p++; continue }
                val marker = u(p + 1)
                if (marker == 0xFF) { p++; continue }
                if (marker in 0xD0..0xD9 || marker == 0x01) { p += 2; continue }
                val len = (u(p + 2) shl 8) or u(p + 3)
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    return ((u(p + 7) shl 8) or u(p + 8)) to ((u(p + 5) shl 8) or u(p + 6))
                }
                p += 2 + len
            }
            return null
        }

        private fun text(e: Element, outer: Style, props: Map<String, String>): Node? {
            // Yazı tipi ve renk çoğu zaman ilk tspan'da tanımlıdır.
            val st = elements(e).firstOrNull { it.tagName.substringAfter(':') == "tspan" }?.let { styleOf(it, outer) } ?: outer
            val sb = StringBuilder()
            var x: Double? = null
            var y: Double? = null
            fun firstNumber(v: String, pct: Double) = v.trim().split(Regex("[\\s,]+")).firstOrNull()?.takeIf { it.isNotEmpty() }?.let { length(it, pct) }
            fun walk(el: Element) {
                if (x == null) x = firstNumber(el.getAttribute("x"), viewW)
                if (y == null) y = firstNumber(el.getAttribute("y"), viewH)
                var c = el.firstChild
                while (c != null) {
                    if (c is Element) {
                        if (c.tagName.substringAfter(':') in setOf("tspan", "textPath", "a")) walk(c)
                    } else if (c.nodeType == org.w3c.dom.Node.TEXT_NODE || c.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                        sb.append(c.nodeValue)
                    }
                    c = c.nextSibling
                }
            }
            walk(e)
            val content = if (e.getAttribute("xml:space") == "preserve") sb.toString().replace('\n', ' ') else sb.toString().replace(Regex("\\s+"), " ").trim()
            if (content.isEmpty()) return null
            if (elements(e).size > 1) warnings += tr("Çok parçalı metinler tek satır olarak açıldı")
            val approx = content.length * st.fontSize * 0.55
            val shift = when (st.anchor) { "middle" -> -approx / 2; "end" -> -approx; else -> 0.0 }
            val n = TextNode(
                text = content,
                fontSize = st.fontSize,
                fontFamily = st.fontFamily,
                bold = st.bold,
                italic = st.italic,
                fill = paint(st.fill, st.fillOpacity, st, null),
                stroke = stroke(st, null),
                transform = Matrix.translate((x ?: 0.0) + shift, y ?: 0.0),
                measuredWidth = e.getAttribute("textLength").takeIf { it.isNotEmpty() }?.let { length(it, viewW) }?.takeIf { it > 0 },
            )
            return common(n, e, props, st)
        }

        private fun shape(e: Element, tag: String): List<SubPath>? = when (tag) {
            "path" -> SvgPathParser.parse(e.getAttribute("d")).takeIf { it.isNotEmpty() }
            "rect" -> {
                val x = attr(e, "x"); val y = attr(e, "y", viewH)
                val w = attr(e, "width"); val h = attr(e, "height", viewH)
                var rx = if (e.hasAttribute("rx")) attr(e, "rx") else -1.0
                var ry = if (e.hasAttribute("ry")) attr(e, "ry", viewH) else -1.0
                if (rx < 0 && ry >= 0) rx = ry
                if (ry < 0 && rx >= 0) ry = rx
                rx = rx.coerceIn(0.0, w / 2)
                ry = ry.coerceIn(0.0, h / 2)
                if (w <= 0 || h <= 0) null else if (rx <= 0 || ry <= 0) listOf(Shapes.rect(Rect(x, y, x + w, y + h))) else listOf(roundRect(x, y, w, h, rx, ry))
            }
            "circle" -> {
                val cx = attr(e, "cx"); val cy = attr(e, "cy", viewH); val r = attr(e, "r")
                if (r <= 0) null else listOf(Shapes.ellipse(Rect(cx - r, cy - r, cx + r, cy + r)))
            }
            "ellipse" -> {
                val cx = attr(e, "cx"); val cy = attr(e, "cy", viewH); val rx = attr(e, "rx"); val ry = attr(e, "ry", viewH)
                if (rx <= 0 || ry <= 0) null else listOf(Shapes.ellipse(Rect(cx - rx, cy - ry, cx + rx, cy + ry)))
            }
            "line" -> listOf(Shapes.line(Vec2(attr(e, "x1"), attr(e, "y1", viewH)), Vec2(attr(e, "x2"), attr(e, "y2", viewH))))
            "polyline", "polygon" -> {
                val nums = e.getAttribute("points").trim().split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
                val pts = (0 until nums.size / 2).map { Anchor(Vec2(nums[it * 2], nums[it * 2 + 1])) }
                if (pts.size < 2) null else listOf(SubPath(pts, closed = tag == "polygon"))
            }
            else -> null
        }

        private fun roundRect(x: Double, y: Double, w: Double, h: Double, rx: Double, ry: Double): SubPath {
            val kx = rx * Shapes.KAPPA
            val ky = ry * Shapes.KAPPA
            val r = x + w
            val b = y + h
            return SubPath(
                listOf(
                    Anchor(Vec2(x + rx, y), handleIn = Vec2(x + rx - kx, y)),
                    Anchor(Vec2(r - rx, y), handleOut = Vec2(r - rx + kx, y)),
                    Anchor(Vec2(r, y + ry), handleIn = Vec2(r, y + ry - ky)),
                    Anchor(Vec2(r, b - ry), handleOut = Vec2(r, b - ry + ky)),
                    Anchor(Vec2(r - rx, b), handleIn = Vec2(r - rx + kx, b)),
                    Anchor(Vec2(x + rx, b), handleOut = Vec2(x + rx - kx, b)),
                    Anchor(Vec2(x, b - ry), handleIn = Vec2(x, b - ry + ky)),
                    Anchor(Vec2(x, y + ry), handleOut = Vec2(x, y + ry - ky)),
                ),
                closed = true,
            )
        }

        private fun path(e: Element, subpaths: List<SubPath>, st: Style, props: Map<String, String>): Node? {
            val box = subpaths.mapNotNull { it.bounds() }.reduceOrNull(Rect::union)
            val fill = paint(st.fill, st.fillOpacity, st, box)
            val stroke = stroke(st, box)
            // Ne dolgusu ne konturu olan yollar da korunur (kılavuz ya da maske olabilirler).
            val tag = e.tagName.substringAfter(':')
            val nm = when (tag) { "rect" -> "Dikdörtgen"; "circle", "ellipse" -> "Elips"; "line" -> "Çizgi"; else -> "Yol" }
            return common(PathNode(name = nm, subpaths = subpaths, fill = fill, stroke = stroke, fillRule = st.fillRule), e, props, st)
        }

        private fun stroke(st: Style, box: Rect?): Stroke? {
            val p = paint(st.stroke, st.strokeOpacity, st, box) ?: return null
            if (st.strokeWidth <= 0) return null
            return Stroke(p, st.strokeWidth, st.cap, st.join, st.miter, st.dash, st.dashOffset)
        }

        private fun urlId(v: String): String? =
            Regex("url\\(\\s*['\"]?#([^'\")\\s]+)['\"]?\\s*\\)").find(v)?.groupValues?.get(1)

        private fun paint(spec: String, opacity: Double, st: Style, box: Rect?): Paint? {
            val v = spec.trim()
            if (v.isEmpty() || v == "none" || v == "transparent") return null
            urlId(v)?.let { id ->
                gradient(id, opacity, st, box)?.let { return it }
                // Bulunamayan başvuru: varsa yedek renk
                val fallback = v.substringAfter(')').trim()
                if (fallback.isEmpty() || fallback == "none") return null
                return color(fallback, st)?.let { Paint.Solid(it.copy(a = it.a * opacity)) }
            }
            return color(v, st)?.let { Paint.Solid(it.copy(a = it.a * opacity)) }
        }

        private fun color(v: String, st: Style): Rgba? {
            val s = v.trim().lowercase()
            if (s == "currentcolor") return color(st.color, Style())
            if (s.startsWith("#")) {
                val h = s.substring(1)
                return when (h.length) {
                    3, 4 -> {
                        val d = h.map { it.digitToIntOrNull(16) ?: return null }
                        Rgba(d[0] * 17 / 255.0, d[1] * 17 / 255.0, d[2] * 17 / 255.0, if (d.size == 4) d[3] * 17 / 255.0 else 1.0)
                    }
                    6, 8 -> {
                        val n = h.toLongOrNull(16) ?: return null
                        if (h.length == 6) Rgba.rgb(n.toInt()) else Rgba.rgb((n shr 8).toInt()).copy(a = (n and 0xFF) / 255.0)
                    }
                    else -> null
                }
            }
            if (s.startsWith("rgb")) {
                val parts = s.substringAfter('(').substringBefore(')').split(Regex("[\\s,/]+")).filter { it.isNotEmpty() }
                if (parts.size < 3) return null
                fun ch(t: String) = if (t.endsWith("%")) (t.dropLast(1).toDoubleOrNull() ?: 0.0) / 100 else (t.toDoubleOrNull() ?: 0.0) / 255
                return Rgba(ch(parts[0]).coerceIn(0.0, 1.0), ch(parts[1]).coerceIn(0.0, 1.0), ch(parts[2]).coerceIn(0.0, 1.0), parts.getOrNull(3)?.let { number(it, 1.0) } ?: 1.0)
            }
            return NamedColors[s]?.let { Rgba.rgb(it) }
        }

        private fun gradient(id: String, opacity: Double, st: Style, box: Rect?): Paint? {
            // Öznitelikler href zinciri boyunca devralınır.
            val chain = ArrayList<Element>()
            var cur: Element? = ids[id]
            while (cur != null && chain.size < 8 && cur !in chain) {
                chain += cur
                cur = hrefOf(cur).takeIf { it.isNotEmpty() }?.let { ids[it] }
            }
            val first = chain.firstOrNull() ?: return null
            val tag = first.tagName.substringAfter(':')
            if (tag != "linearGradient" && tag != "radialGradient") {
                if (tag == "pattern") warnings += tr("Desen dolguları düz renk olarak gösterilir")
                return if (tag == "pattern") Paint.Solid(Rgba(0.6, 0.6, 0.6, opacity)) else null
            }
            fun a(name: String): String? = chain.firstOrNull { it.hasAttribute(name) }?.getAttribute(name)
            val stopsEl = chain.firstOrNull { el -> elements(el).any { it.tagName.substringAfter(':') == "stop" } } ?: return null
            val stops = elements(stopsEl).filter { it.tagName.substringAfter(':') == "stop" }.map { s ->
                val d = declarations(s)
                val c = color(d["stop-color"] ?: "black", st) ?: Rgba.Black
                GradientStop(number(d["offset"], 0.0).coerceIn(0.0, 1.0), c.copy(a = c.a * number(d["stop-opacity"], 1.0) * opacity))
            }
            if (stops.isEmpty()) return null
            if (stops.size == 1) return Paint.Solid(stops[0].color)
            val bbox = a("gradientUnits") != "userSpaceOnUse"
            var m = a("gradientTransform")?.let(::parseTransform) ?: Matrix.Identity
            if (bbox) {
                val b = box ?: return Paint.Solid(stops.last().color)
                if (b.width <= 0 || b.height <= 0) return Paint.Solid(stops.last().color)
                m = Matrix.translate(b.left, b.top) * Matrix.scale(b.width, b.height) * m
            }
            fun coord(name: String, def: String, pct: Double): Double {
                val v = a(name) ?: def
                return if (bbox) number(v, 0.0) else length(v, pct)
            }
            return if (tag == "linearGradient") {
                Paint.LinearGradient(
                    Vec2(coord("x1", "0%", viewW), coord("y1", "0%", viewH)),
                    Vec2(coord("x2", "100%", viewW), coord("y2", "0%", viewH)),
                    stops, m,
                )
            } else {
                Paint.RadialGradient(Vec2(coord("cx", "50%", viewW), coord("cy", "50%", viewH)), coord("r", "50%", viewW), stops, m)
            }
        }
    }

    internal fun parseTransform(v: String): Matrix {
        var m = Matrix.Identity
        for (t in Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)").findAll(v)) {
            val a = t.groupValues[2].trim().split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
            val next = when (t.groupValues[1]) {
                "matrix" -> if (a.size >= 6) Matrix(a[0], a[1], a[2], a[3], a[4], a[5]) else Matrix.Identity
                "translate" -> Matrix.translate(a.getOrElse(0) { 0.0 }, a.getOrElse(1) { 0.0 })
                "scale" -> Matrix.scale(a.getOrElse(0) { 1.0 }, a.getOrElse(1) { a.getOrElse(0) { 1.0 } })
                "rotate" -> {
                    val r = Matrix.rotate(Math.toRadians(a.getOrElse(0) { 0.0 }))
                    if (a.size >= 3) Matrix.translate(a[1], a[2]) * r * Matrix.translate(-a[1], -a[2]) else r
                }
                "skewX" -> Matrix(c = kotlin.math.tan(Math.toRadians(a.getOrElse(0) { 0.0 })))
                "skewY" -> Matrix(b = kotlin.math.tan(Math.toRadians(a.getOrElse(0) { 0.0 })))
                else -> Matrix.Identity
            }
            m = m * next
        }
        return m
    }

    private val Skipped = setOf(
        "defs", "style", "title", "desc", "metadata", "clipPath", "mask", "linearGradient", "radialGradient", "pattern",
        "symbol", "marker", "filter", "script", "namedview", "foreignObject",
    )

    private val NamedColors = mapOf(
        "black" to 0x000000, "white" to 0xFFFFFF, "red" to 0xFF0000, "green" to 0x008000, "lime" to 0x00FF00, "blue" to 0x0000FF,
        "yellow" to 0xFFFF00, "cyan" to 0x00FFFF, "aqua" to 0x00FFFF, "magenta" to 0xFF00FF, "fuchsia" to 0xFF00FF,
        "gray" to 0x808080, "grey" to 0x808080, "silver" to 0xC0C0C0, "maroon" to 0x800000, "olive" to 0x808000,
        "navy" to 0x000080, "purple" to 0x800080, "teal" to 0x008080, "orange" to 0xFFA500, "pink" to 0xFFC0CB,
        "brown" to 0xA52A2A, "gold" to 0xFFD700, "indigo" to 0x4B0082, "violet" to 0xEE82EE, "darkgray" to 0xA9A9A9,
        "darkgrey" to 0xA9A9A9, "lightgray" to 0xD3D3D3, "lightgrey" to 0xD3D3D3, "dimgray" to 0x696969, "whitesmoke" to 0xF5F5F5,
        "darkblue" to 0x00008B, "darkgreen" to 0x006400, "darkred" to 0x8B0000, "skyblue" to 0x87CEEB, "steelblue" to 0x4682B4,
        "tomato" to 0xFF6347, "coral" to 0xFF7F50, "salmon" to 0xFA8072, "crimson" to 0xDC143C, "beige" to 0xF5F5DC,
        "ivory" to 0xFFFFF0, "khaki" to 0xF0E68C, "tan" to 0xD2B48C, "turquoise" to 0x40E0D0, "royalblue" to 0x4169E1,
    )
}
