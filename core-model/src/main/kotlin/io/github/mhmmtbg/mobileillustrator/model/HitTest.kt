package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.max

/**
 * [point] (belge koordinatı) altındaki en üstteki seçilebilir nesne.
 * Gizli ya da kilitli katman ve nesneler atlanır. Grup içindeki bir yola
 * dokunulduğunda katmanın doğrudan çocuğu olan en dış grup döner.
 *
 * @param tolerance belge biriminde dokunma payı (ekran payı / yakınlaştırma).
 */
fun Document.hitTest(point: Vec2, tolerance: Double): Node? {
    for (layer in layers.asReversed()) {
        if (!layer.visible || layer.locked) continue
        for (node in layer.children.asReversed()) {
            if (node.hit(point, tolerance, Matrix.Identity)) return node
        }
    }
    return null
}

private fun Node.hit(point: Vec2, tolerance: Double, parent: Matrix): Boolean {
    if (!visible || locked) return false
    val m = parent * transform
    return when (this) {
        is GroupNode -> {
            val c = clip
            if (c != null && !insideRings(point, c.subpaths.map { it.transformed(m).flatten() }, c.rule)) {
                false
            } else {
                children.asReversed().any { it.hit(point, tolerance, m) }
            }
        }
        is PathNode -> hitPath(point, tolerance, m)
        is ImageNode -> {
            val local = m.inverse()?.apply(point)
            local != null && local.x >= 0 && local.y >= 0 && local.x <= image.width && local.y <= image.height
        }
        is TextNode -> {
            val local = m.inverse()?.apply(point)
            local != null && localBounds().contains(local)
        }
    }
}

private fun insideRings(point: Vec2, rings: List<List<Vec2>>, rule: FillRule): Boolean = when (rule) {
    FillRule.NonZero -> rings.sumOf { windingNumber(point, it) } != 0
    FillRule.EvenOdd -> rings.sumOf { crossings(point, it) } % 2 != 0
}

private fun PathNode.hitPath(point: Vec2, tolerance: Double, m: Matrix): Boolean {
    val polys = subpaths.map { it.transformed(m).flatten() to it.closed }
    if (fill != null) {
        // Açık yollar da dolgu için kapalıymış gibi değerlendirilir (çizimle aynı).
        if (insideRings(point, polys.map { it.first }, fillRule)) return true
    }
    val reach = max(tolerance, (stroke?.let { it.width * m.meanScale / 2 } ?: 0.0) + tolerance)
    if (stroke == null && fill != null) return false
    for ((pts, closed) in polys) {
        if (distanceToPolyline(point, pts, closed) <= reach) return true
    }
    return false
}

private fun windingNumber(p: Vec2, ring: List<Vec2>): Int {
    var wn = 0
    val n = ring.size
    if (n < 3) return 0
    for (i in 0 until n) {
        val a = ring[i]
        val b = ring[(i + 1) % n]
        val cross = (b.x - a.x) * (p.y - a.y) - (p.x - a.x) * (b.y - a.y)
        if (a.y <= p.y) {
            if (b.y > p.y && cross > 0) wn++
        } else {
            if (b.y <= p.y && cross < 0) wn--
        }
    }
    return wn
}

private fun crossings(p: Vec2, ring: List<Vec2>): Int {
    var count = 0
    val n = ring.size
    if (n < 3) return 0
    for (i in 0 until n) {
        val a = ring[i]
        val b = ring[(i + 1) % n]
        if ((a.y > p.y) != (b.y > p.y)) {
            val x = a.x + (p.y - a.y) / (b.y - a.y) * (b.x - a.x)
            if (x > p.x) count++
        }
    }
    return count
}

private fun distanceToPolyline(p: Vec2, pts: List<Vec2>, closed: Boolean): Double {
    if (pts.isEmpty()) return Double.POSITIVE_INFINITY
    if (pts.size == 1) return p.distanceTo(pts[0])
    var best = Double.POSITIVE_INFINITY
    val last = if (closed) pts.size else pts.size - 1
    for (i in 0 until last) {
        val d = distanceToSegment(p, pts[i], pts[(i + 1) % pts.size])
        if (d < best) best = d
    }
    return best
}

private fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0.0) return p.distanceTo(a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    return p.distanceTo(Vec2(a.x + t * dx, a.y + t * dy))
}
