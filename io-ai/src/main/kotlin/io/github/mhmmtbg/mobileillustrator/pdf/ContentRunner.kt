package io.github.mhmmtbg.mobileillustrator.pdf

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.BlendMode
import io.github.mhmmtbg.mobileillustrator.model.ClipPath
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
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
import io.github.mhmmtbg.mobileillustrator.model.transformedBy
import kotlin.math.abs

/** Kırpma yığınının bir halkası. Kimlik karşılaştırılır: aynı `W` işleminden gelen nesneler aynı halkayı paylaşır. */
internal class ClipLink(val parent: ClipLink?, val clip: ClipPath)

internal sealed class Item {
    abstract val clip: ClipLink?
}

internal class Leaf(val node: Node, override val clip: ClipLink?) : Item()

internal class Container(
    val name: String,
    override val clip: ClipLink?,
    val opacity: Double = 1.0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    /** Tek çocuk üretirse grup açılmaz, ad çocuğa verilir. */
    val unwrapSingle: Boolean = false,
    /** Bu uygulamanın yazdığı metin bloğu: içerik ne olursa olsun metin bu özelliklerden kurulur. */
    val text: TextProps? = null,
    val blend: BlendMode = BlendMode.Normal,
) : Item() {
    val items = ArrayList<Item>()
}

internal class TextProps(
    val text: String,
    val fontFamily: String,
    val fontSize: Double,
    val bold: Boolean,
    val italic: Boolean,
    /** Metnin yerel uzayından belge uzayına. */
    val matrix: Matrix,
)

internal class LayerInfo(val name: String, val visible: Boolean, val locked: Boolean)

/** İçerik akışının çıktısını toplayan taraf (katmanlara dağıtım burada yapılır). */
internal interface ContentSink {
    fun emit(item: Item)
    fun beginLayer(ref: PdfRef?, info: LayerInfo): Boolean
    fun endLayer()
    fun layerInfo(ocg: PdfObj?): Pair<PdfRef?, LayerInfo>?
    fun warn(message: String)
}

/**
 * PDF içerik akışı yorumlayıcısı. Çizim işleçlerini belge koordinatlarına (y aşağı) çevrilmiş
 * düğümlere dönüştürür; dönüşüm matrisi koordinatlara işlenir, düğümlerin kendi dönüşümü birim kalır.
 */
internal class ContentRunner(private val file: PdfFile, private val sink: ContentSink, private val pageRect: Rect) {

    private class GState {
        var ctm = Matrix.Identity
        var fillCs: ColorSpace = ColorSpace.Gray
        var fillColor = doubleArrayOf(0.0)
        var fillPattern: Paint? = null
        var strokeCs: ColorSpace = ColorSpace.Gray
        var strokeColor = doubleArrayOf(0.0)
        var strokePattern: Paint? = null
        var lineWidth = 1.0
        var cap = LineCap.Butt
        var join = LineJoin.Miter
        var miter = 10.0
        var dash: List<Double> = emptyList()
        var dashPhase = 0.0
        var fillAlpha = 1.0
        var strokeAlpha = 1.0
        var clip: ClipLink? = null
        var blend = BlendMode.Normal
        var font: PdfFont? = null
        var fontSize = 12.0
        var charSpace = 0.0
        var wordSpace = 0.0
        var hScale = 1.0
        var leading = 0.0
        var rise = 0.0
        var renderMode = 0

        fun copy(): GState {
            val g = GState()
            g.ctm = ctm
            g.fillCs = fillCs; g.fillColor = fillColor; g.fillPattern = fillPattern
            g.strokeCs = strokeCs; g.strokeColor = strokeColor; g.strokePattern = strokePattern
            g.lineWidth = lineWidth; g.cap = cap; g.join = join; g.miter = miter
            g.dash = dash; g.dashPhase = dashPhase
            g.fillAlpha = fillAlpha; g.strokeAlpha = strokeAlpha
            g.clip = clip
            g.blend = blend
            g.font = font; g.fontSize = fontSize; g.charSpace = charSpace; g.wordSpace = wordSpace
            g.hScale = hScale; g.leading = leading; g.rise = rise; g.renderMode = renderMode
            return g
        }
    }

    private var gs = GState()
    private val stack = ArrayList<GState>()
    private val containers = ArrayList<Container>()
    private val fonts = HashMap<PdfDict, PdfFont>()

    // Yol kurucu
    private val done = ArrayList<SubPath>()
    private var cur: ArrayList<Anchor>? = null
    private var curClosed = false
    private var startUser = Vec2.Zero
    private var lastUser = Vec2.Zero
    private var pendingClip: FillRule? = null

    // Metin
    private var tm = Matrix.Identity
    private var tlm = Matrix.Identity
    private var pendingText: TextRun? = null

    private class TextRun(
        val font: PdfFont,
        val fontSize: Double,
        val matrix: Matrix,
        val text: StringBuilder,
        var width: Double,
        val fill: Paint?,
        val stroke: Stroke?,
        val opacity: Double,
        val clip: ClipLink?,
        val blend: BlendMode,
    )

    fun start(base: Matrix) {
        gs = GState()
        gs.ctm = base
        stack.clear()
    }

    private fun emit(item: Item) {
        flushText()
        if (containers.isNotEmpty()) containers.last().items += item else sink.emit(item)
    }

    // ---- Ana döngü --------------------------------------------------------

    fun run(bytes: ByteArray, resources: PdfDict?, patternBase: Matrix, depth: Int = 0) {
        val lx = PdfLexer(bytes)
        val args = ArrayList<PdfObj>()
        /** Bu akışta açılan işaretli içerik blokları: 0 diğer, 1 üst katman, 2 iç içe kap. */
        val marks = ArrayList<Int>()
        val stackBase = stack.size
        while (true) {
            val o = lx.next() ?: break
            if (o !is PdfOp) {
                args += o
                if (args.size > 4096) args.clear()
                continue
            }
            try {
                op(o.name, args, resources, patternBase, depth, lx, marks)
            } catch (e: PdfException) {
                sink.warn(e.message ?: "İçerik hatası")
            } catch (e: IndexOutOfBoundsException) {
                // Eksik işlenen: işleci atla
            }
            args.clear()
        }
        flushText()
        // Kapatılmamış bloklar ve dengelenmemiş q'lar bu akışın dışına taşmasın.
        while (marks.isNotEmpty()) closeMark(marks)
        while (stack.size > stackBase) gs = stack.removeAt(stack.size - 1)
    }

    private fun n(args: List<PdfObj>, i: Int): Double = (args[i] as? PdfNum)?.value ?: (file.num(args[i]) ?: 0.0)

    private fun op(
        name: String,
        a: ArrayList<PdfObj>,
        res: PdfDict?,
        patternBase: Matrix,
        depth: Int,
        lx: PdfLexer,
        marks: ArrayList<Int>,
    ) {
        when (name) {
            "q" -> stack += gs.copy()
            "Q" -> { flushText(); if (stack.isNotEmpty()) gs = stack.removeAt(stack.size - 1) }
            "cm" -> gs.ctm = gs.ctm * Matrix(n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5))
            "w" -> gs.lineWidth = n(a, 0)
            "J" -> gs.cap = when (n(a, 0).toInt()) { 1 -> LineCap.Round; 2 -> LineCap.Square; else -> LineCap.Butt }
            "j" -> gs.join = when (n(a, 0).toInt()) { 1 -> LineJoin.Round; 2 -> LineJoin.Bevel; else -> LineJoin.Miter }
            "M" -> gs.miter = n(a, 0)
            "d" -> {
                gs.dash = file.numbers(a[0])?.toList() ?: emptyList()
                gs.dashPhase = n(a, 1)
            }
            "gs" -> extGState(file.dict(file.dict(res?.get("ExtGState"))?.get((a[0] as PdfName).name)))

            "m" -> moveTo(n(a, 0), n(a, 1))
            "l" -> lineTo(n(a, 0), n(a, 1))
            "c" -> curveTo(Vec2(n(a, 0), n(a, 1)), Vec2(n(a, 2), n(a, 3)), Vec2(n(a, 4), n(a, 5)))
            "v" -> curveTo(lastUser, Vec2(n(a, 0), n(a, 1)), Vec2(n(a, 2), n(a, 3)))
            "y" -> Vec2(n(a, 2), n(a, 3)).let { curveTo(Vec2(n(a, 0), n(a, 1)), it, it) }
            "h" -> closePath()
            "re" -> {
                val x = n(a, 0); val y = n(a, 1); val w = n(a, 2); val h = n(a, 3)
                moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); closePath()
            }

            "S" -> paint(fill = false, stroke = true, FillRule.NonZero)
            "s" -> { closePath(); paint(fill = false, stroke = true, FillRule.NonZero) }
            "f", "F" -> paint(fill = true, stroke = false, FillRule.NonZero)
            "f*" -> paint(fill = true, stroke = false, FillRule.EvenOdd)
            "B" -> paint(fill = true, stroke = true, FillRule.NonZero)
            "B*" -> paint(fill = true, stroke = true, FillRule.EvenOdd)
            "b" -> { closePath(); paint(fill = true, stroke = true, FillRule.NonZero) }
            "b*" -> { closePath(); paint(fill = true, stroke = true, FillRule.EvenOdd) }
            "n" -> paint(fill = false, stroke = false, FillRule.NonZero)
            "W" -> pendingClip = FillRule.NonZero
            "W*" -> pendingClip = FillRule.EvenOdd

            "g" -> { gs.fillCs = ColorSpace.Gray; gs.fillColor = doubleArrayOf(n(a, 0)); gs.fillPattern = null }
            "G" -> { gs.strokeCs = ColorSpace.Gray; gs.strokeColor = doubleArrayOf(n(a, 0)); gs.strokePattern = null }
            "rg" -> { gs.fillCs = ColorSpace.Rgb; gs.fillColor = doubleArrayOf(n(a, 0), n(a, 1), n(a, 2)); gs.fillPattern = null }
            "RG" -> { gs.strokeCs = ColorSpace.Rgb; gs.strokeColor = doubleArrayOf(n(a, 0), n(a, 1), n(a, 2)); gs.strokePattern = null }
            "k" -> { gs.fillCs = ColorSpace.Cmyk; gs.fillColor = doubleArrayOf(n(a, 0), n(a, 1), n(a, 2), n(a, 3)); gs.fillPattern = null }
            "K" -> { gs.strokeCs = ColorSpace.Cmyk; gs.strokeColor = doubleArrayOf(n(a, 0), n(a, 1), n(a, 2), n(a, 3)); gs.strokePattern = null }
            "cs" -> {
                val cs = ColorSpace.parse(file, a[0], res) ?: ColorSpace.Gray.also { sink.warn("Bilinmeyen renk uzayı") }
                gs.fillCs = cs; gs.fillColor = cs.initial(); gs.fillPattern = null
            }
            "CS" -> {
                val cs = ColorSpace.parse(file, a[0], res) ?: ColorSpace.Gray.also { sink.warn("Bilinmeyen renk uzayı") }
                gs.strokeCs = cs; gs.strokeColor = cs.initial(); gs.strokePattern = null
            }
            "sc", "scn" -> {
                val nums = a.filterIsInstance<PdfNum>()
                if (nums.isNotEmpty()) gs.fillColor = DoubleArray(nums.size) { nums[it].value }
                gs.fillPattern = (a.lastOrNull() as? PdfName)?.let { pattern(it.name, res, patternBase) }
            }
            "SC", "SCN" -> {
                val nums = a.filterIsInstance<PdfNum>()
                if (nums.isNotEmpty()) gs.strokeColor = DoubleArray(nums.size) { nums[it].value }
                gs.strokePattern = (a.lastOrNull() as? PdfName)?.let { pattern(it.name, res, patternBase) }
            }

            "sh" -> shadingFill((a[0] as PdfName).name, res)
            "Do" -> xobject((a[0] as PdfName).name, res, depth)
            "BI" -> skipInlineImage(lx)

            "BT" -> { tm = Matrix.Identity; tlm = Matrix.Identity }
            "ET" -> flushText()
            "Tc" -> gs.charSpace = n(a, 0)
            "Tw" -> gs.wordSpace = n(a, 0)
            "Tz" -> gs.hScale = n(a, 0) / 100.0
            "TL" -> gs.leading = n(a, 0)
            "Ts" -> gs.rise = n(a, 0)
            "Tr" -> gs.renderMode = n(a, 0).toInt()
            "Tf" -> {
                gs.fontSize = n(a, 1)
                val d = file.dict(file.dict(res?.get("Font"))?.get((a[0] as PdfName).name))
                gs.font = d?.let { fonts.getOrPut(it) { PdfFont.parse(file, it) } }
            }
            "Td" -> { tlm = tlm * Matrix.translate(n(a, 0), n(a, 1)); tm = tlm }
            "TD" -> { gs.leading = -n(a, 1); tlm = tlm * Matrix.translate(n(a, 0), n(a, 1)); tm = tlm }
            "Tm" -> { tlm = Matrix(n(a, 0), n(a, 1), n(a, 2), n(a, 3), n(a, 4), n(a, 5)); tm = tlm }
            "T*" -> nextLine()
            "Tj" -> showText(listOf(a[0]))
            "TJ" -> showText((a[0] as? PdfArr)?.items ?: emptyList())
            "'" -> { nextLine(); showText(listOf(a[0])) }
            "\"" -> { gs.wordSpace = n(a, 0); gs.charSpace = n(a, 1); nextLine(); showText(listOf(a[2])) }

            "BMC" -> marks += 0
            "BDC" -> marks += beginMarked(a, res)
            "EMC" -> closeMark(marks)
        }
    }

    // ---- İşaretli içerik (katmanlar) --------------------------------------

    private fun beginMarked(a: List<PdfObj>, res: PdfDict?): Int {
        val tag = (a.getOrNull(0) as? PdfName)?.name ?: return 0
        val propsObj = a.getOrNull(1)
        val props: PdfObj? = if (propsObj is PdfName) file.dict(res?.get("Properties"))?.get(propsObj.name) else propsObj
        if (tag == "OC") {
            val (ref, info) = sink.layerInfo(props) ?: return 0
            flushText()
            if (containers.isEmpty() && sink.beginLayer(ref, info)) return 1
            containers += Container(info.name, gs.clip, visible = info.visible, locked = info.locked, unwrapSingle = true)
            return 2
        }
        if (tag == "MI") {
            // Bu uygulamanın kendi yazdığı dosyalardaki nesne adları
            val d = file.dict(props) ?: return 0
            val nm = file.string(d["N"])?.text() ?: return 0
            flushText()
            val kind = file.name(d["T"])
            var text: TextProps? = null
            if (kind == "Text") {
                val m = file.numbers(d["M"])?.takeIf { it.size >= 6 }?.let { Matrix(it[0], it[1], it[2], it[3], it[4], it[5]) }
                    ?: Matrix.Identity
                text = TextProps(
                    text = file.string(d["S"])?.text() ?: "",
                    fontFamily = file.string(d["F"])?.text() ?: "sans-serif",
                    fontSize = file.num(d["Z"]) ?: 12.0,
                    bold = file.bool(d["B"]) == true,
                    italic = file.bool(d["I"]) == true,
                    matrix = gs.ctm * m,
                )
            }
            containers += Container(nm, gs.clip, unwrapSingle = kind != "Group", text = text)
            return 2
        }
        return 0
    }

    private fun closeMark(marks: ArrayList<Int>) {
        if (marks.isEmpty()) return
        when (marks.removeAt(marks.size - 1)) {
            1 -> { flushText(); sink.endLayer() }
            2 -> {
                flushText()
                if (containers.isNotEmpty()) {
                    val c = containers.removeAt(containers.size - 1)
                    if (c.items.isNotEmpty() || c.text != null) emit(c)
                }
            }
        }
    }

    // ---- Grafik durumu ----------------------------------------------------

    private fun extGState(d: PdfDict?) {
        d ?: return
        file.num(d["LW"])?.let { gs.lineWidth = it }
        file.int(d["LC"])?.let { gs.cap = when (it) { 1 -> LineCap.Round; 2 -> LineCap.Square; else -> LineCap.Butt } }
        file.int(d["LJ"])?.let { gs.join = when (it) { 1 -> LineJoin.Round; 2 -> LineJoin.Bevel; else -> LineJoin.Miter } }
        file.num(d["ML"])?.let { gs.miter = it }
        file.array(d["D"])?.let { arr ->
            gs.dash = file.numbers(arr.getOrNull(0))?.toList() ?: emptyList()
            gs.dashPhase = file.num(arr.getOrNull(1)) ?: 0.0
        }
        file.num(d["ca"])?.let { gs.fillAlpha = it.coerceIn(0.0, 1.0) }
        file.num(d["CA"])?.let { gs.strokeAlpha = it.coerceIn(0.0, 1.0) }
        val bm = file.resolve(d["BM"])
        val mode = (bm as? PdfName)?.name ?: file.name((bm as? PdfArr)?.items?.firstOrNull())
        if (mode != null) {
            gs.blend = BlendMode.fromPdf(mode) ?: BlendMode.Normal.also { sink.warn("Bilinmeyen karışım modu: $mode") }
        }
        val smask = file.resolve(d["SMask"])
        if (smask != null && (smask as? PdfName)?.name != "None") sink.warn("Opaklık maskeleri yok sayıldı")
    }

    // ---- Yol kurma --------------------------------------------------------

    private fun flushSub() {
        val c = cur
        if (c != null && c.size >= 2) done += SubPath(c, curClosed)
        cur = null
        curClosed = false
    }

    private fun moveTo(x: Double, y: Double) {
        flushSub()
        startUser = Vec2(x, y)
        lastUser = startUser
        cur = arrayListOf(Anchor(gs.ctm.apply(startUser)))
    }

    private fun ensureOpen(): ArrayList<Anchor> {
        cur?.let { if (!curClosed) return it }
        // "h" sonrasında yeni alt yol, kapatılanın başlangıcından devam eder.
        flushSub()
        lastUser = startUser
        return arrayListOf(Anchor(gs.ctm.apply(startUser))).also { cur = it }
    }

    private fun lineTo(x: Double, y: Double) {
        val c = ensureOpen()
        lastUser = Vec2(x, y)
        c += Anchor(gs.ctm.apply(lastUser))
    }

    private fun curveTo(c1: Vec2, c2: Vec2, p: Vec2) {
        val c = ensureOpen()
        val prev = c[c.size - 1]
        val h1 = gs.ctm.apply(c1)
        val h2 = gs.ctm.apply(c2)
        val end = gs.ctm.apply(p)
        c[c.size - 1] = prev.copy(handleOut = h1.takeIf { !same(it, prev.point) })
        c += Anchor(end, handleIn = h2.takeIf { !same(it, end) })
        lastUser = p
    }

    private fun same(a: Vec2, b: Vec2) = abs(a.x - b.x) < 1e-9 && abs(a.y - b.y) < 1e-9

    private fun closePath() {
        val c = cur ?: return
        if (curClosed) return
        if (c.size > 2) {
            val first = c[0]
            val last = c[c.size - 1]
            if (abs(first.point.x - last.point.x) < 1e-6 && abs(first.point.y - last.point.y) < 1e-6) {
                // Son düğüm başlangıcın üzerine geliyorsa ikisini birleştir.
                c[0] = first.copy(handleIn = last.handleIn)
                c.removeAt(c.size - 1)
            }
        }
        curClosed = true
        lastUser = startUser
    }

    private fun takePath(): List<SubPath> {
        flushSub()
        val out = ArrayList(done)
        done.clear()
        return out
    }

    // ---- Boyama -----------------------------------------------------------

    private fun fillPaint(): Paint? = paintOf(gs.fillCs, gs.fillColor, gs.fillPattern)
    private fun strokePaint(): Paint? = paintOf(gs.strokeCs, gs.strokeColor, gs.strokePattern)

    private fun paintOf(cs: ColorSpace, color: DoubleArray, pattern: Paint?): Paint? = when {
        cs is ColorSpace.Pattern -> pattern ?: cs.under?.let { Paint.Solid(it.toRgb(color)) }
        cs is ColorSpace.Tint && cs.isNone -> null
        else -> Paint.Solid(cs.toRgb(color))
    }

    private fun withAlpha(p: Paint?, alpha: Double): Paint? =
        if (p is Paint.Solid && alpha < 1.0) Paint.Solid(p.color.copy(a = p.color.a * alpha)) else p

    private fun makeStroke(p: Paint): Stroke {
        val k = gs.ctm.meanScale
        val w = if (gs.lineWidth <= 0.0) 0.25 else gs.lineWidth * k
        return Stroke(p, w, gs.cap, gs.join, gs.miter, gs.dash.map { it * k }, gs.dashPhase * k)
    }

    private fun paint(fill: Boolean, stroke: Boolean, rule: FillRule) {
        val subpaths = takePath()
        val clipRule = pendingClip
        pendingClip = null
        if (subpaths.isNotEmpty() && (fill || stroke)) {
            var f = if (fill) fillPaint() else null
            val sp = if (stroke) strokePaint() else null
            var s = sp?.let(::makeStroke)
            var opacity = 1.0
            val fa = gs.fillAlpha
            val sa = gs.strokeAlpha
            when {
                f != null && s != null && fa == sa -> opacity = fa
                // Tek boyalı nesnede saydamlık nesnenin opaklığıdır (Illustrator'daki gibi), renge işlenmez.
                f != null && s == null -> opacity = fa
                f == null && s != null -> opacity = sa
                else -> {
                    f = withAlpha(f, fa)
                    s = s?.let { it.copy(paint = withAlpha(it.paint, sa)!!) }
                }
            }
            if (f != null || s != null) {
                emit(Leaf(PathNode(subpaths = subpaths, fill = f, stroke = s, fillRule = rule, opacity = opacity, blendMode = gs.blend), gs.clip))
            }
        }
        if (clipRule != null && subpaths.isNotEmpty()) {
            gs.clip = ClipLink(gs.clip, ClipPath(subpaths, clipRule))
        }
    }

    // ---- Desenler ve gradyanlar -------------------------------------------

    private fun pattern(name: String, res: PdfDict?, patternBase: Matrix): Paint? {
        val obj = file.resolve(file.dict(res?.get("Pattern"))?.get(name))
        val d = file.dict(obj) ?: return null
        if (file.int(d["PatternType"]) == 2) {
            val m = file.numbers(d["Matrix"])?.takeIf { it.size >= 6 }?.let { Matrix(it[0], it[1], it[2], it[3], it[4], it[5]) }
                ?: Matrix.Identity
            return shadingPaint(file.resolve(d["Shading"]), patternBase * m)
        }
        sink.warn("Desen dolguları düz renk olarak gösterilir")
        return Paint.Solid(Rgba(0.6, 0.6, 0.6))
    }

    private fun shadingPaint(shObj: PdfObj?, transform: Matrix): Paint? {
        val sh = file.dict(shObj) ?: return null
        val type = file.int(sh["ShadingType"])
        val cs = ColorSpace.parse(file, sh["ColorSpace"], null) ?: ColorSpace.Rgb
        val fn = PdfFunction.parse(file, sh["Function"])
        val coords = file.numbers(sh["Coords"])
        if (fn == null || coords == null || (type != 2 && type != 3)) {
            sink.warn("Ağ (mesh) gradyanlar düz renk olarak gösterilir")
            return Paint.Solid(Rgba(0.6, 0.6, 0.6))
        }
        val domain = file.numbers(sh["Domain"])?.takeIf { it.size >= 2 } ?: doubleArrayOf(0.0, 1.0)
        val stops = gradientStops(fn, cs, domain[0], domain[1])
        if (type == 2 && coords.size >= 4) {
            return Paint.LinearGradient(Vec2(coords[0], coords[1]), Vec2(coords[2], coords[3]), stops, transform)
        }
        if (coords.size < 6) return null
        val r0 = coords[2]
        val r1 = coords[5]
        return if (r1 >= r0) {
            val rr = if (r1 <= 0.0) 1e-6 else r1
            Paint.RadialGradient(Vec2(coords[3], coords[4]), rr, stops.map { GradientStop((r0 + it.offset * (r1 - r0)) / rr, it.color) }, transform)
        } else {
            Paint.RadialGradient(
                Vec2(coords[0], coords[1]), r0,
                stops.asReversed().map { GradientStop((r1 + (1 - it.offset) * (r0 - r1)) / r0, it.color) },
                transform,
            )
        }
    }

    private fun gradientStops(fn: PdfFunction, cs: ColorSpace, t0: Double, t1: Double): List<GradientStop> {
        val span = if (t1 == t0) 1.0 else t1 - t0
        val out = ArrayList<GradientStop>()
        fun add(t: Double, at: Double = t) {
            out += GradientStop(((t - t0) / span).coerceIn(0.0, 1.0), cs.toRgb(fn.eval(doubleArrayOf(at))))
        }
        fun samples(f: PdfFunction): Int = when (f) {
            is PdfFunction.Exponential -> if (f.n == 1.0) 1 else 8
            is PdfFunction.Sampled -> (f.sampleCount - 1).coerceIn(1, 32)
            else -> 16
        }
        if (fn is PdfFunction.Stitching) {
            val eps = span * 1e-6
            for (k in fn.functions.indices) {
                val (lo, hi) = fn.span(k)
                if (hi <= lo) continue
                val nseg = samples(fn.functions[k])
                // Sınırlarda iki taraftan ayrı örnek al: keskin renk geçişleri korunur.
                add(lo, lo + if (k > 0) eps else 0.0)
                for (i in 1 until nseg) add(lo + (hi - lo) * i / nseg)
                add(hi, hi - if (k < fn.functions.size - 1) eps else 0.0)
            }
        } else {
            val nseg = samples(fn)
            for (i in 0..nseg) add(t0 + span * i / nseg)
        }
        if (out.size < 2) {
            val c = out.firstOrNull()?.color ?: Rgba.Black
            return listOf(GradientStop(0.0, c), GradientStop(1.0, c))
        }
        return out
    }

    private fun shadingFill(name: String, res: PdfDict?) {
        val paint = shadingPaint(file.resolve(file.dict(res?.get("Shading"))?.get(name)), gs.ctm) ?: return
        // "sh" geçerli kırpma bölgesini boyar: en içteki kırpma yolu şeklin kendisi olur.
        val link = gs.clip
        val shape = link?.clip?.subpaths ?: listOf(Shapes.rect(pageRect))
        val rule = link?.clip?.rule ?: FillRule.NonZero
        emit(Leaf(PathNode(subpaths = shape, fill = paint, fillRule = rule, opacity = gs.fillAlpha, blendMode = gs.blend), link?.parent))
    }

    // ---- XObject'ler ------------------------------------------------------

    private fun xobject(name: String, res: PdfDict?, depth: Int) {
        val xo = file.stream(file.dict(res?.get("XObject"))?.get(name)) ?: return
        when (file.name(xo.dict["Subtype"])) {
            "Form" -> {
                if (depth > 24) return
                val m = file.numbers(xo.dict["Matrix"])?.takeIf { it.size >= 6 }?.let { Matrix(it[0], it[1], it[2], it[3], it[4], it[5]) }
                    ?: Matrix.Identity
                val formRes = file.dict(xo.dict["Resources"]) ?: res
                val bytes = file.decodedBytes(xo)
                flushText()
                val saved = gs
                gs = saved.copy()
                gs.ctm = saved.ctm * m
                val savedStack = ArrayList(stack)
                val savedPath = Triple(ArrayList(done), cur, curClosed)
                done.clear(); cur = null; curClosed = false
                val isolated = xo.dict["Group"] != null && (saved.fillAlpha < 1.0 || saved.blend != BlendMode.Normal)
                if (isolated) {
                    // Saydamlık grubu: opaklık ve karışım modu içeriğe tek tek değil gruba bir bütün olarak uygulanır.
                    containers += Container("Grup", saved.clip, opacity = saved.fillAlpha, blend = saved.blend)
                    gs.fillAlpha = 1.0
                    gs.strokeAlpha = 1.0
                    gs.blend = BlendMode.Normal
                }
                run(bytes, formRes, gs.ctm, depth + 1)
                if (isolated) {
                    val c = containers.removeAt(containers.size - 1)
                    if (c.items.isNotEmpty()) emit(c)
                }
                gs = saved
                stack.clear(); stack.addAll(savedStack)
                done.clear(); done.addAll(savedPath.first); cur = savedPath.second; curClosed = savedPath.third
            }
            "Image" -> image(xo)
        }
    }

    private fun skipInlineImage(lx: PdfLexer) {
        // Satır içi görsel: "ID" sonrasındaki ikili veriyi "EI" işlecine kadar atla.
        while (true) {
            val o = lx.next() ?: return
            if (o is PdfOp && o.name == "ID") break
        }
        var p = lx.pos + 1
        val d = lx.data
        while (p + 2 < d.size) {
            if (d[p] == 'E'.code.toByte() && d[p + 1] == 'I'.code.toByte() && isWs(d[p - 1]) && isWs(d[p + 2])) {
                lx.pos = p + 2
                sink.warn("Satır içi görseller atlandı")
                return
            }
            p++
        }
        lx.pos = d.size
    }

    private fun isWs(b: Byte) = b == 32.toByte() || b == 10.toByte() || b == 13.toByte() || b == 9.toByte() || b == 0.toByte()

    private fun image(xo: PdfStream) {
        val w = file.int(xo.dict["Width"]) ?: return
        val h = file.int(xo.dict["Height"]) ?: return
        if (w <= 0 || h <= 0) return
        if (w.toLong() * h > 60_000_000L) {
            sink.warn("Çok büyük bir görsel atlandı (${w}x$h)")
            return
        }
        val data = try { ImageDecoder.decode(file, xo, w, h, gs.fillCs.toRgb(gs.fillColor), sink) } catch (e: Exception) { null }
        if (data == null) {
            sink.warn("Bir görsel okunamadı")
            return
        }
        val m = gs.ctm * Matrix(1.0 / w, 0.0, 0.0, -1.0 / h, 0.0, 1.0)
        emit(Leaf(ImageNode(image = data, transform = m, opacity = gs.fillAlpha, blendMode = gs.blend), gs.clip))
    }

    // ---- Metin ------------------------------------------------------------

    private fun nextLine() {
        tlm = tlm * Matrix.translate(0.0, -gs.leading)
        tm = tlm
    }

    private fun showText(parts: List<PdfObj>) {
        val font = gs.font
        val fs = gs.fontSize
        val sb = StringBuilder()
        val startTm = tm
        var advance = 0.0 // metin uzayında, yatay ölçekten önce
        var undecodable = false
        for (p in parts) {
            when (val r = if (p is PdfRef) file.resolve(p) else p) {
                is PdfNum -> {
                    val shift = -r.value / 1000.0 * fs
                    advance += shift
                    if (r.value < -180 && sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(' ')
                }
                is PdfStr -> {
                    if (font == null) {
                        advance += r.bytes.size * fs * 0.5
                        undecodable = true
                    } else {
                        for (g in font.decode(r.bytes)) {
                            if (g.text != null) sb.append(g.text) else undecodable = true
                            advance += g.width / 1000.0 * fs + gs.charSpace + if (g.isSpace) gs.wordSpace else 0.0
                        }
                    }
                }
                else -> {}
            }
        }
        tm = tm * Matrix.translate(advance * gs.hScale, 0.0)
        if (font == null || gs.renderMode == 3 || gs.renderMode == 7 || fs == 0.0) return
        if (undecodable && sb.isBlank()) {
            sink.warn("Bazı metinler okunamadı (font Unicode bilgisi içermiyor)")
            return
        }
        if (sb.isEmpty()) return
        if (undecodable) sink.warn("Bazı karakterler okunamadı")

        val mode = gs.renderMode
        val fill = if (mode == 0 || mode == 2 || mode == 4 || mode == 6) fillPaint() else null
        val strokeP = if (mode == 1 || mode == 2 || mode == 5 || mode == 6) strokePaint() else null
        // Yerel uzay: y aşağı, başlangıç taban çizgisi. Negatif font boyutu metni çevirir.
        val size = abs(fs)
        val sign = if (fs < 0) -1.0 else 1.0
        val matrix = gs.ctm * startTm * Matrix(gs.hScale * sign, 0.0, 0.0, -sign, 0.0, gs.rise)
        val width = abs(advance)
        val stroke = strokeP?.let { Stroke(it, (if (gs.lineWidth <= 0) 0.25 else gs.lineWidth) / maxOf(startTm.meanScale, 1e-9)) }
        val opacity = gs.fillAlpha

        val prev = pendingText
        if (prev != null && prev.font === font && prev.fontSize == size && prev.fill == fill && prev.stroke == stroke &&
            prev.opacity == opacity && prev.clip === gs.clip && prev.blend == gs.blend && sameLinear(prev.matrix, matrix)
        ) {
            val local = prev.matrix.inverse()?.apply(matrix.apply(Vec2.Zero))
            if (local != null && abs(local.y) < size * 0.02 && local.x > prev.width - size * 0.08 && local.x < prev.width + size * 1.2) {
                if (local.x - prev.width > size * 0.18 && !prev.text.endsWith(" ") && !sb.startsWith(" ")) prev.text.append(' ')
                prev.text.append(sb)
                prev.width = local.x + width
                return
            }
        }
        flushText()
        pendingText = TextRun(font, size, matrix, sb, width, fill, stroke, opacity, gs.clip, gs.blend)
    }

    private fun sameLinear(a: Matrix, b: Matrix): Boolean {
        val eps = 1e-6 * maxOf(1.0, abs(a.a) + abs(a.d))
        return abs(a.a - b.a) < eps && abs(a.b - b.b) < eps && abs(a.c - b.c) < eps && abs(a.d - b.d) < eps
    }

    private fun flushText() {
        val t = pendingText ?: return
        pendingText = null
        val text = t.text.toString()
        if (text.isBlank()) return
        // Illustrator metni çoğu zaman "1 punto font x 30 kat dönüşüm" olarak yazar. Ölçek font boyutuna
        // taşınır: boyut anlamlı bir sayı olur ve küçük boyutta harf aralıkları bozulmaz.
        val k = kotlin.math.hypot(t.matrix.c, t.matrix.d).takeIf { it > 1e-9 && it.isFinite() } ?: 1.0
        val node = TextNode(
            name = text.trim().take(24),
            text = text,
            fontSize = t.fontSize * k,
            fontFamily = if (t.font.monospace) "monospace" else if (t.font.serif) "serif" else "sans-serif",
            bold = t.font.bold,
            italic = t.font.italic,
            fill = t.fill?.transformedBy(Matrix.scale(k)),
            stroke = t.stroke?.let { it.copy(width = it.width * k, paint = it.paint.transformedBy(Matrix.scale(k))) },
            transform = t.matrix * Matrix.scale(1 / k),
            opacity = t.opacity,
            blendMode = t.blend,
            measuredWidth = t.width * k,
        )
        val leaf = Leaf(node, t.clip)
        if (containers.isNotEmpty()) containers.last().items += leaf else sink.emit(leaf)
    }
}
