package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.max
import kotlin.math.roundToInt

/** Renk lejantının durduğu katmanın adı; lejant yeniden eklenince bu katman yenilenir. */
const val LEGEND_LAYER = "Renk lejantı"

/** Lejantta ve paylaşılan listede her rengin hangi kodlarının yazılacağı. */
data class LegendOptions(val hex: Boolean = true, val rgb: Boolean = true, val cmyk: Boolean = true)

/**
 * Rengin CMYK karşılığı. Renk dosyada CMYK olarak tanımlıysa o değerler aynen verilir; değilse sRGB'den
 * hesaplanır (profilsiz, yaklaşık dönüşüm: siyah bileşen en parlak kanaldan çıkarılır).
 */
fun Rgba.toCmyk(): Ink.Cmyk {
    (ink as? Ink.Cmyk)?.let { return it }
    val k = 1.0 - max(r, max(g, b)).coerceIn(0.0, 1.0)
    if (k >= 1.0 - 1e-9) return Ink.Cmyk(0.0, 0.0, 0.0, 1.0)
    fun ch(v: Double) = ((1.0 - v.coerceIn(0.0, 1.0) - k) / (1.0 - k)).coerceIn(0.0, 1.0)
    return Ink.Cmyk(ch(r), ch(g), ch(b), k)
}

/** Rengin kod satırları: "#RRGGBB", "RGB r g b", "CMYK c m y k" (yüzde). */
fun Rgba.codeLines(options: LegendOptions = LegendOptions()): List<String> {
    val argb = toArgb()
    val out = ArrayList<String>(3)
    if (options.hex) out += "#%06X".format(argb and 0xFFFFFF)
    if (options.rgb) out += "RGB %d %d %d".format((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    if (options.cmyk) {
        val c = toCmyk()
        fun p(v: Double) = (v * 100).roundToInt()
        out += "CMYK %d %d %d %d".format(p(c.c), p(c.m), p(c.y), p(c.k))
    }
    return out
}

private fun Document.withoutLegend(): Document = copy(layers = layers.filter { it.name != LEGEND_LAYER }.ifEmpty { layers })

/** Tasarımda kullanılan renkler (lejantın kendisi hariç), en çok kullanılandan aza. */
fun Document.designColors(): List<Rgba> = withoutLegend().colorGroups().map { it.color }

/** Tasarımdaki renklerin kodları, paylaşmak için düz metin olarak (her renk bir satır). */
fun Document.colorCodesText(options: LegendOptions = LegendOptions()): String =
    designColors().joinToString("\n") { it.codeLines(options).joinToString("   ") }

/** Köşeleri [r] yarıçapıyla yuvarlatılmış kare (saat yönünde, sekiz düğüm). */
private fun roundedSquare(x: Double, y: Double, s: Double, r: Double): SubPath {
    val k = r * 0.5523
    val e = x + s
    val f = y + s
    return SubPath(
        listOf(
            Anchor(Vec2(x + r, y), handleIn = Vec2(x + r - k, y)),
            Anchor(Vec2(e - r, y), handleOut = Vec2(e - r + k, y)),
            Anchor(Vec2(e, y + r), handleIn = Vec2(e, y + r - k)),
            Anchor(Vec2(e, f - r), handleOut = Vec2(e, f - r + k)),
            Anchor(Vec2(e - r, f), handleIn = Vec2(e - r + k, f)),
            Anchor(Vec2(x + r, f), handleOut = Vec2(x + r - k, f)),
            Anchor(Vec2(x, f - r), handleIn = Vec2(x, f - r + k)),
            Anchor(Vec2(x, y + r), handleOut = Vec2(x, y + r - k)),
        ),
        closed = true,
    )
}

/** Belgede lejant varsa hangi kodlarla eklendiği (etiketlerinden okunur); yoksa `null`. */
fun Document.legendOptions(): LegendOptions? {
    val layer = layers.firstOrNull { it.name == LEGEND_LAYER } ?: return null
    fun find(nodes: List<Node>): TextNode? = nodes.firstNotNullOfOrNull { n -> (n as? TextNode) ?: (n as? GroupNode)?.let { find(it.children) } }
    val lines = find(layer.children)?.text?.split('\n') ?: return null
    return LegendOptions(hex = lines.any { it.startsWith("#") }, rgb = lines.any { it.startsWith("RGB") }, cmyk = lines.any { it.startsWith("CMYK") })
}

/** Lejant eklemenin sonucu: yeni belge ve lejant grubunun kimliği. */
class LegendResult(val document: Document, val groupId: String, val colors: Int)

/**
 * Tasarımda kullanılan renklerin lejantını ekler: her renk için köşeleri yuvarlak bir örnek kare ve yanında kodları.
 * Lejant ilk çalışma yüzeyinin alt kenarına, kendi katmanında tek bir grup olarak yerleşir (taşınıp ölçeklenebilir).
 * Önceden eklenmiş lejant varsa yenisiyle değiştirilir. Belgede renk yoksa `null` döner.
 */
fun Document.withColorLegend(options: LegendOptions = LegendOptions()): LegendResult? {
    val base = withoutLegend()
    val colors = base.designColors().take(60)
    val lines = colors.map { it.codeLines(options) }
    val perItem = lines.firstOrNull()?.size ?: 0
    if (colors.isEmpty() || perItem == 0) return null
    val board = artboards.firstOrNull()?.bounds ?: base.layers.flatMap { it.children }.mapNotNull { it.bounds() }.reduceOrNull(Rect::union) ?: return null

    val s = board.width * 0.045
    val margin = s * 0.8
    val font = minOf(s * 0.42, s / (perItem * 1.25))
    val leading = 1.25
    val textGap = s * 0.3
    val textW = (lines.maxOf { l -> l.maxOf { it.length } }) * font * 0.62
    val itemW = s + textGap + textW + s * 0.9
    val perRow = max(1, ((board.width - 2 * margin + s * 0.9) / itemW).toInt())
    val rows = (colors.size + perRow - 1) / perRow
    val rowH = s * 1.35
    val top = board.bottom - margin - rows * rowH + (rowH - s)
    val dark = Rgba.rgb(0x14182B)

    val children = ArrayList<Node>(colors.size * 2)
    for ((i, color) in colors.withIndex()) {
        val x = board.left + margin + (i % perRow) * itemW
        val y = top + (i / perRow) * rowH
        val label = lines[i]
        children += PathNode(
            name = label.first(), subpaths = listOf(roundedSquare(x, y, s, s * 0.14)),
            fill = Paint.Solid(color), stroke = Stroke(Paint.Solid(dark), s * 0.035, join = LineJoin.Round),
        )
        val block = (perItem - 1) * font * leading + font * 0.72
        children += TextNode(
            name = label.first(), text = label.joinToString("\n"), fontSize = font, lineHeight = leading,
            fill = Paint.Solid(Rgba.Black), transform = Matrix.translate(x + s + textGap, y + (s - block) / 2 + font * 0.72),
        )
    }
    val group = GroupNode(name = LEGEND_LAYER, children = children)
    val layer = Layer(name = LEGEND_LAYER, children = listOf(group))
    return LegendResult(base.copy(layers = base.layers + layer), group.id, colors.size)
}
