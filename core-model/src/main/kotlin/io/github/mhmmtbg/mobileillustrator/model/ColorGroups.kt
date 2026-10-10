package io.github.mhmmtbg.mobileillustrator.model

/**
 * Belgede aynı rengi kullanan nesneler. [rgb] `0xRRGGBB` biçimindedir (saydamlık ayırt edilmez);
 * [nodeIds] o rengi dolgusunda, konturunda ya da gradyanında kullanan yol ve metinlerdir.
 */
class ColorGroup(val rgb: Int, val color: Rgba, val nodeIds: List<String>, val fills: Int, val strokes: Int)

private fun Rgba.rgbKey(): Int = toArgb() and 0xFFFFFF

private fun Paint.colors(): List<Rgba> = when (this) {
    is Paint.Solid -> listOf(color)
    is Paint.LinearGradient -> stops.map { it.color }
    is Paint.RadialGradient -> stops.map { it.color }
}

/**
 * Belgedeki renkler, en çok kullanılandan aza doğru. Kilitli katmanlar ve kilitli nesneler sayılmaz
 * (renkleri toplu değiştirilmez); gizli olanlar sayılır. Renk lejantı katmanı da sayılmaz.
 */
fun Document.colorGroups(): List<ColorGroup> {
    class Acc(val color: Rgba) {
        val ids = LinkedHashSet<String>()
        var fills = 0
        var strokes = 0
    }
    val groups = LinkedHashMap<Int, Acc>()
    fun note(id: String, paint: Paint?, stroke: Boolean) {
        paint ?: return
        for (c in paint.colors()) {
            val acc = groups.getOrPut(c.rgbKey()) { Acc(c.copy(a = 1.0)) }
            acc.ids += id
            if (stroke) acc.strokes++ else acc.fills++
        }
    }
    fun walk(nodes: List<Node>) {
        for (n in nodes) {
            if (n.locked) continue
            when (n) {
                is PathNode -> { note(n.id, n.fill, false); note(n.id, n.stroke?.paint, true) }
                is TextNode -> { note(n.id, n.fill, false); note(n.id, n.stroke?.paint, true) }
                is GroupNode -> walk(n.children)
                is ImageNode -> {}
            }
        }
    }
    // Renk lejantı tasarımın parçası sayılmaz: kendi örnek kareleri ve yazıları listeyi şişirmesin.
    for (l in layers) if (!l.locked && l.name != LEGEND_LAYER) walk(l.children)
    return groups.entries
        .map { (rgb, acc) -> ColorGroup(rgb, acc.color, acc.ids.toList(), acc.fills, acc.strokes) }
        .sortedByDescending { it.nodeIds.size }
}

/**
 * [rgb] rengini kullanan bütün dolgu, kontur ve gradyan duraklarını [to] rengine çevirir; her birinin kendi
 * saydamlığı korunur. Kilitli katmanlara ve nesnelere dokunulmaz. Değişmeyen dallar aynı nesne olarak kalır.
 */
fun Document.recolored(rgb: Int, to: Rgba): Document {
    fun swap(c: Rgba): Rgba = if (c.rgbKey() == rgb) to.copy(a = c.a) else c
    fun swap(p: Paint): Paint = when (p) {
        is Paint.Solid -> if (p.color.rgbKey() == rgb) Paint.Solid(swap(p.color)) else p
        is Paint.LinearGradient -> if (p.stops.any { it.color.rgbKey() == rgb }) p.copy(stops = p.stops.map { it.copy(color = swap(it.color)) }) else p
        is Paint.RadialGradient -> if (p.stops.any { it.color.rgbKey() == rgb }) p.copy(stops = p.stops.map { it.copy(color = swap(it.color)) }) else p
    }
    fun swap(s: Stroke): Stroke = swap(s.paint).let { if (it === s.paint) s else s.copy(paint = it) }
    fun walk(nodes: List<Node>): List<Node> {
        var changed = false
        val out = nodes.map { n ->
            val next: Node = if (n.locked) n else when (n) {
                is PathNode -> {
                    val f = n.fill?.let(::swap)
                    val s = n.stroke?.let(::swap)
                    if (f === n.fill && s === n.stroke) n else n.copy(fill = f, stroke = s)
                }
                is TextNode -> {
                    val f = n.fill?.let(::swap)
                    val s = n.stroke?.let(::swap)
                    if (f === n.fill && s === n.stroke) n else n.copy(fill = f, stroke = s)
                }
                is GroupNode -> walk(n.children).let { if (it === n.children) n else n.copy(children = it) }
                is ImageNode -> n
            }
            if (next !== n) changed = true
            next
        }
        return if (changed) out else nodes
    }
    var changed = false
    val newLayers = layers.map { l ->
        if (l.locked || l.name == LEGEND_LAYER) l else walk(l.children).let { if (it === l.children) l else { changed = true; l.copy(children = it) } }
    }
    return if (changed) copy(layers = newLayers) else this
}
