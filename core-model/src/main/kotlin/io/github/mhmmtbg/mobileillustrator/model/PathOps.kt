package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Yol üzerindeki bir düğümün adresi. */
data class AnchorRef(val subpath: Int, val index: Int)

/** Yol üzerindeki bir noktanın adresi: [segment]. parçanın [t] konumu. */
data class PathLocation(val subpath: Int, val segment: Int, val t: Double, val point: Vec2, val distance: Double)

private fun lerp(a: Vec2, b: Vec2, t: Double) = Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** [point] noktasına en yakın yol konumu (yolun yerel uzayında). */
fun List<SubPath>.nearest(point: Vec2): PathLocation? {
    var best: PathLocation? = null
    for ((si, sp) in withIndex()) {
        for ((gi, seg) in sp.segments().withIndex()) {
            val steps = if (seg.isLine) 1 else 24
            var prev = seg.p0
            for (k in 1..steps) {
                val t1 = k.toDouble() / steps
                val cur = seg.pointAt(t1)
                val dx = cur.x - prev.x
                val dy = cur.y - prev.y
                val len2 = dx * dx + dy * dy
                val u = if (len2 == 0.0) 0.0 else (((point.x - prev.x) * dx + (point.y - prev.y) * dy) / len2).coerceIn(0.0, 1.0)
                val t = (k - 1 + u) / steps
                val p = seg.pointAt(t)
                val d = p.distanceTo(point)
                if (best == null || d < best.distance) best = PathLocation(si, gi, t, p, d)
                prev = cur
            }
        }
    }
    return best
}

/** Parçayı [loc] konumundan bölerek araya yeni bir düğüm ekler; eğrinin şekli değişmez. */
fun List<SubPath>.insertAnchor(loc: PathLocation): Pair<List<SubPath>, AnchorRef>? {
    val sp = getOrNull(loc.subpath) ?: return null
    val n = sp.anchors.size
    val seg = sp.segments().getOrNull(loc.segment) ?: return null
    val ai = loc.segment
    val bi = (loc.segment + 1) % n
    val t = loc.t.coerceIn(0.001, 0.999)
    val anchors = ArrayList(sp.anchors)
    val mid: Anchor
    if (seg.isLine) {
        mid = Anchor(lerp(seg.p0, seg.p3, t))
    } else {
        // de Casteljau
        val p01 = lerp(seg.p0, seg.p1, t)
        val p12 = lerp(seg.p1, seg.p2, t)
        val p23 = lerp(seg.p2, seg.p3, t)
        val p012 = lerp(p01, p12, t)
        val p123 = lerp(p12, p23, t)
        val p = lerp(p012, p123, t)
        anchors[ai] = anchors[ai].copy(handleOut = p01)
        anchors[bi] = anchors[bi].copy(handleIn = p23)
        mid = Anchor(p, handleIn = p012, handleOut = p123)
    }
    anchors.add(ai + 1, mid)
    val out = ArrayList(this)
    out[loc.subpath] = sp.copy(anchors = anchors)
    return out to AnchorRef(loc.subpath, ai + 1)
}

/** Düğümü siler. İki düğümden az kalan alt yol tümüyle kalkar. */
fun List<SubPath>.deleteAnchor(ref: AnchorRef): List<SubPath> {
    val sp = getOrNull(ref.subpath) ?: return this
    if (ref.index !in sp.anchors.indices) return this
    val anchors = sp.anchors.filterIndexed { i, _ -> i != ref.index }
    val out = ArrayList(this)
    if (anchors.size < 2) out.removeAt(ref.subpath) else out[ref.subpath] = sp.copy(anchors = anchors, closed = sp.closed && anchors.size > 2)
    return out
}

fun List<SubPath>.anchorAt(ref: AnchorRef): Anchor? = getOrNull(ref.subpath)?.anchors?.getOrNull(ref.index)

fun List<SubPath>.replaceAnchor(ref: AnchorRef, anchor: Anchor): List<SubPath> {
    val sp = getOrNull(ref.subpath) ?: return this
    if (ref.index !in sp.anchors.indices) return this
    val out = ArrayList(this)
    out[ref.subpath] = sp.copy(anchors = ArrayList(sp.anchors).also { it[ref.index] = anchor })
    return out
}

/** Düğümü tutamaçlarıyla birlikte taşır. */
fun Anchor.movedBy(d: Vec2) = Anchor(point + d, handleIn?.plus(d), handleOut?.plus(d))

/** Tutamaçlar düğümün iki yanında aynı doğru üzerinde mi (yumuşak düğüm)? */
fun Anchor.isSmooth(): Boolean {
    val a = handleIn ?: return false
    val b = handleOut ?: return false
    val ax = a.x - point.x; val ay = a.y - point.y
    val bx = b.x - point.x; val by = b.y - point.y
    val la = hypot(ax, ay); val lb = hypot(bx, by)
    if (la < 1e-9 || lb < 1e-9) return false
    val cross = abs(ax * by - ay * bx) / (la * lb)
    val dot = (ax * bx + ay * by) / (la * lb)
    return cross < 0.02 && dot < 0
}

/**
 * Bir tutamacı taşır. Düğüm yumuşaksa karşı tutamaç aynı doğru üzerinde kalacak biçimde döner
 * (uzunluğu korunur).
 */
fun Anchor.withHandle(outgoing: Boolean, position: Vec2): Anchor {
    val smooth = isSmooth()
    val moved = if (outgoing) copy(handleOut = position) else copy(handleIn = position)
    if (!smooth) return moved
    val other = (if (outgoing) handleIn else handleOut) ?: return moved
    val len = other.distanceTo(point)
    val dx = point.x - position.x
    val dy = point.y - position.y
    val d = hypot(dx, dy)
    if (d < 1e-9) return moved
    val mirrored = Vec2(point.x + dx / d * len, point.y + dy / d * len)
    return if (outgoing) moved.copy(handleIn = mirrored) else moved.copy(handleOut = mirrored)
}

/** Köşe düğümü yumuşak yapar (komşulara göre tutamaç üretir) ya da yumuşak düğümü köşeye çevirir. */
fun List<SubPath>.toggleSmooth(ref: AnchorRef): List<SubPath> {
    val sp = getOrNull(ref.subpath) ?: return this
    val a = sp.anchors.getOrNull(ref.index) ?: return this
    if (a.handleIn != null || a.handleOut != null) return replaceAnchor(ref, Anchor(a.point))
    val n = sp.anchors.size
    val prev = if (ref.index > 0) sp.anchors[ref.index - 1] else if (sp.closed) sp.anchors[n - 1] else null
    val next = if (ref.index < n - 1) sp.anchors[ref.index + 1] else if (sp.closed) sp.anchors[0] else null
    val from = prev?.point ?: a.point
    val to = next?.point ?: a.point
    val dx = to.x - from.x
    val dy = to.y - from.y
    val d = hypot(dx, dy)
    if (d < 1e-9) return this
    val ux = dx / d
    val uy = dy / d
    val lin = prev?.let { it.point.distanceTo(a.point) / 3 } ?: 0.0
    val lout = next?.let { it.point.distanceTo(a.point) / 3 } ?: 0.0
    return replaceAnchor(
        ref,
        Anchor(
            a.point,
            handleIn = if (prev != null) Vec2(a.point.x - ux * lin, a.point.y - uy * lin) else null,
            handleOut = if (next != null) Vec2(a.point.x + ux * lout, a.point.y + uy * lout) else null,
        ),
    )
}

/** Serbest el çizgisini az sayıda yumuşak düğüme indirger. */
object PathFit {
    /**
     * @param tolerance noktaların eğriden sapabileceği en büyük uzaklık (belge birimi).
     */
    fun fit(points: List<Vec2>, tolerance: Double): SubPath? {
        if (points.size < 2) return null
        val simplified = simplify(points, tolerance)
        if (simplified.size < 2) return null
        if (simplified.size == 2) return SubPath(listOf(Anchor(simplified[0]), Anchor(simplified[1])))
        // Catmull-Rom: her düğümde teğet, komşularını birleştiren doğruya paraleldir.
        val n = simplified.size
        val anchors = ArrayList<Anchor>(n)
        for (i in 0 until n) {
            val p = simplified[i]
            val prev = simplified[maxOf(i - 1, 0)]
            val next = simplified[minOf(i + 1, n - 1)]
            val tx = next.x - prev.x
            val ty = next.y - prev.y
            val tl = hypot(tx, ty)
            if (tl < 1e-9) {
                anchors += Anchor(p)
                continue
            }
            val ux = tx / tl
            val uy = ty / tl
            val lin = p.distanceTo(prev) / 3
            val lout = p.distanceTo(next) / 3
            anchors += Anchor(
                p,
                handleIn = if (i > 0) Vec2(p.x - ux * lin, p.y - uy * lin) else null,
                handleOut = if (i < n - 1) Vec2(p.x + ux * lout, p.y + uy * lout) else null,
            )
        }
        return SubPath(anchors)
    }

    /** Ramer-Douglas-Peucker (özyinelemesiz). */
    fun simplify(points: List<Vec2>, tolerance: Double): List<Vec2> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayList<IntArray>()
        stack += intArrayOf(0, points.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeAt(stack.size - 1)
            var maxD = 0.0
            var idx = -1
            val pa = points[a]
            val pb = points[b]
            val dx = pb.x - pa.x
            val dy = pb.y - pa.y
            val len = sqrt(dx * dx + dy * dy)
            for (i in a + 1 until b) {
                val p = points[i]
                val d = if (len < 1e-12) p.distanceTo(pa) else abs(dy * (p.x - pa.x) - dx * (p.y - pa.y)) / len
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > tolerance) {
                keep[idx] = true
                stack += intArrayOf(a, idx)
                stack += intArrayOf(idx, b)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }
}
