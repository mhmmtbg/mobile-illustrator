package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class Align { Left, CenterH, Right, Top, CenterV, Bottom }

/**
 * Nesneleri hizalar. Birden çok nesne seçiliyse ortak sınır kutularına, tek nesne seçiliyse
 * [artboard] kutusuna göre hizalanır (Illustrator'daki "Seçime / Çalışma yüzeyine hizala").
 */
fun Document.align(ids: Set<String>, mode: Align, artboard: Rect? = null): Document {
    val boxes = ids.mapNotNull { id -> boundsOf(id)?.let { id to it } }
    if (boxes.isEmpty()) return this
    val key = if (boxes.size >= 2) boxes.map { it.second }.reduce(Rect::union) else artboard ?: return this
    var doc = this
    for ((id, b) in boxes) {
        val dx = when (mode) {
            Align.Left -> key.left - b.left
            Align.CenterH -> key.center.x - b.center.x
            Align.Right -> key.right - b.right
            else -> 0.0
        }
        val dy = when (mode) {
            Align.Top -> key.top - b.top
            Align.CenterV -> key.center.y - b.center.y
            Align.Bottom -> key.bottom - b.bottom
            else -> 0.0
        }
        if (dx != 0.0 || dy != 0.0) doc = doc.transformNode(id, Matrix.translate(dx, dy))
    }
    return doc
}

/** En dıştaki iki nesne yerinde kalır; aradakiler eşit boşlukla dizilir. En az üç nesne gerekir. */
fun Document.distribute(ids: Set<String>, horizontal: Boolean): Document {
    val boxes = ids.mapNotNull { id -> boundsOf(id)?.let { id to it } }
        .sortedBy { if (horizontal) it.second.center.x else it.second.center.y }
    if (boxes.size < 3) return this
    val first = boxes.first().second
    val last = boxes.last().second
    val total = boxes.sumOf { if (horizontal) it.second.width else it.second.height }
    val span = if (horizontal) last.right - first.left else last.bottom - first.top
    val gap = (span - total) / (boxes.size - 1)
    var cursor = if (horizontal) first.left else first.top
    var doc = this
    for ((id, b) in boxes) {
        val d = cursor - if (horizontal) b.left else b.top
        if (abs(d) > 1e-9) doc = doc.transformNode(id, if (horizontal) Matrix.translate(d, 0.0) else Matrix.translate(0.0, d))
        cursor += (if (horizontal) b.width else b.height) + gap
    }
    return doc
}

/**
 * Kırpma maskesi: seçimdeki en üstteki yol maske olur, diğerleri onun içine kırpılır.
 * @return yeni belge ve kırpma grubunun kimliği.
 */
fun Document.makeClippingMask(ids: Set<String>): Pair<Document, String>? {
    val picked = ArrayList<Node>()
    var layerId: String? = null
    for (layer in layers) for (n in layer.children) if (n.id in ids) { picked += n; layerId = layer.id }
    if (picked.size < 2 || layerId == null) return null
    val mask = picked.last() as? PathNode ?: return null
    val rest = picked.dropLast(1)
    val clip = ClipPath(mask.subpaths.map { it.transformed(mask.transform) }, mask.fillRule)
    val group = GroupNode(name = "Kırpma grubu", children = rest, clip = clip)
    val doc = copy(
        layers = layers.map { layer ->
            val kids = ArrayList<Node>()
            for (n in layer.children) {
                if (n.id == mask.id) kids += group else if (n.id !in ids) kids += n
            }
            if (kids.size == layer.children.size && layer.children.none { it.id in ids }) layer else layer.copy(children = kids)
        },
    )
    return doc to group.id
}

/** Kırpmayı kaldırır; maske, dolgusuz ince konturlu bir yol olarak grubun en üstüne geri gelir. */
fun Document.releaseClippingMask(groupId: String): Document {
    val g = findNode(groupId) as? GroupNode ?: return this
    val clip = g.clip ?: return this
    val mask = PathNode(name = "Maske", subpaths = clip.subpaths, fillRule = clip.rule, stroke = Stroke(Paint.Solid(Rgba.Black), 1.0))
    return updateNode(groupId) { g.copy(name = "Grup", clip = null, children = g.children + mask) }
}

// ---- Gradyan tarifi ------------------------------------------------------------

/** Arayüzde düzenlenen gradyan: nesnenin kutusuna göre yerleştirilir. */
data class GradientSpec(val radial: Boolean, val angleDegrees: Double, val stops: List<GradientStop>) {
    /** [box] kutusunu kaplayan boya. Doğrusal gradyan kutunun merkezinden geçer ve kenarlara kadar uzanır. */
    fun paintFor(box: Rect): Paint {
        val ordered = stops.sortedBy { it.offset }
        val c = box.center
        if (radial) return Paint.RadialGradient(c, maxOf(box.width, box.height) / 2, ordered)
        val a = Math.toRadians(angleDegrees)
        val dx = cos(a)
        val dy = sin(a)
        // Kutunun bu yöndeki izdüşümünün yarısı
        val half = (abs(dx) * box.width + abs(dy) * box.height) / 2
        return Paint.LinearGradient(Vec2(c.x - dx * half, c.y - dy * half), Vec2(c.x + dx * half, c.y + dy * half), ordered)
    }

    companion object {
        val Default = GradientSpec(false, 0.0, listOf(GradientStop(0.0, Rgba.rgb(0xFF9A3C)), GradientStop(1.0, Rgba.rgb(0x7950F2))))

        /** Var olan bir gradyan boyasından düzenlenebilir tarif çıkarır. */
        fun from(paint: Paint?): GradientSpec? = when (paint) {
            is Paint.LinearGradient -> {
                val s = paint.transform.apply(paint.start)
                val e = paint.transform.apply(paint.end)
                var deg = Math.toDegrees(atan2(e.y - s.y, e.x - s.x))
                if (deg < 0) deg += 360.0
                GradientSpec(false, deg, paint.stops)
            }
            is Paint.RadialGradient -> GradientSpec(true, 0.0, paint.stops)
            else -> null
        }
    }
}

/** Gradyanı düğümdeki (grupsa içindeki) her yol ve metne, kendi kutusuna göre uygular. */
fun Node.withGradient(spec: GradientSpec, stroke: Boolean = false, strokeWidth: Double = 1.0): Node = when (this) {
    is PathNode -> {
        val box = subpaths.mapNotNull { it.bounds() }.reduceOrNull(Rect::union)
        if (box == null) this else if (stroke) copy(stroke = (this.stroke ?: Stroke(Paint.Solid(Rgba.Black), strokeWidth)).copy(paint = spec.paintFor(box))) else copy(fill = spec.paintFor(box))
    }
    is TextNode -> if (stroke) this else copy(fill = spec.paintFor(localBounds()))
    is GroupNode -> copy(children = children.map { it.withGradient(spec, stroke, strokeWidth) })
    is ImageNode -> this
}

// ---- Yakalama ---------------------------------------------------------------------

/** Yakalamanın sonucu: uygulanacak kaydırma ve gösterilecek kılavuz çizgileri (belge koordinatı). */
class SnapResult(val dx: Double, val dy: Double, val guidesX: List<Double>, val guidesY: List<Double>) {
    companion object {
        val None = SnapResult(0.0, 0.0, emptyList(), emptyList())
    }
}

object Snap {
    private fun xs(r: Rect) = doubleArrayOf(r.left, r.center.x, r.right)
    private fun ys(r: Rect) = doubleArrayOf(r.top, r.center.y, r.bottom)

    /** Taşınan kutunun kenarlarını ve ortasını, [targets] kutularının kenar ve ortalarına [threshold] yakınlıkta yapıştırır. */
    fun box(moving: Rect, targets: List<Rect>, threshold: Double): SnapResult {
        var bestX = Double.MAX_VALUE
        var bestY = Double.MAX_VALUE
        val mx = xs(moving)
        val my = ys(moving)
        for (t in targets) {
            for (a in mx) for (b in xs(t)) { val d = b - a; if (abs(d) < abs(bestX)) bestX = d }
            for (a in my) for (b in ys(t)) { val d = b - a; if (abs(d) < abs(bestY)) bestY = d }
        }
        val dx = if (abs(bestX) <= threshold) bestX else 0.0
        val dy = if (abs(bestY) <= threshold) bestY else 0.0
        val gx = ArrayList<Double>()
        val gy = ArrayList<Double>()
        if (abs(bestX) <= threshold) for (t in targets) for (b in xs(t)) for (a in mx) if (abs(a + dx - b) < 1e-6 && b !in gx) gx += b
        if (abs(bestY) <= threshold) for (t in targets) for (b in ys(t)) for (a in my) if (abs(a + dy - b) < 1e-6 && b !in gy) gy += b
        return SnapResult(dx, dy, gx, gy)
    }

    /** Tek bir noktayı (çizim ya da ölçekleme sırasında parmak) hedeflerin kenar ve ortalarına yapıştırır. */
    fun point(p: Vec2, targets: List<Rect>, threshold: Double): SnapResult = box(Rect(p.x, p.y, p.x, p.y), targets, threshold)
}
