package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Bir Bezier düğümü. Tutamaçlar mutlak koordinattır; `null` ise o taraf düzdür
 * (tutamaç düğümün üzerindedir).
 */
data class Anchor(
    val point: Vec2,
    val handleIn: Vec2? = null,
    val handleOut: Vec2? = null,
) {
    fun transformed(m: Matrix) = Anchor(m.apply(point), handleIn?.let(m::apply), handleOut?.let(m::apply))
}

/** Bir kübik Bezier parçası. Düz çizgiler de bu biçimde taşınır ([isLine]). */
data class Segment(val p0: Vec2, val p1: Vec2, val p2: Vec2, val p3: Vec2, val isLine: Boolean) {
    fun pointAt(t: Double): Vec2 {
        val u = 1 - t
        val w0 = u * u * u
        val w1 = 3 * u * u * t
        val w2 = 3 * u * t * t
        val w3 = t * t * t
        return Vec2(
            w0 * p0.x + w1 * p1.x + w2 * p2.x + w3 * p3.x,
            w0 * p0.y + w1 * p1.y + w2 * p2.y + w3 * p3.y,
        )
    }

    /** Eğrinin gerçek sınır kutusu (kontrol noktalarının değil). */
    fun bounds(): Rect {
        val pts = ArrayList<Vec2>(6)
        pts += p0
        pts += p3
        if (!isLine) {
            for (t in extrema(p0.x, p1.x, p2.x, p3.x)) pts += pointAt(t)
            for (t in extrema(p0.y, p1.y, p2.y, p3.y)) pts += pointAt(t)
        }
        return Rect.bounding(pts)!!
    }

    private fun extrema(a0: Double, a1: Double, a2: Double, a3: Double): List<Double> {
        // Türev: 3[(1-t)^2 (a1-a0) + 2(1-t)t (a2-a1) + t^2 (a3-a2)] = A t^2 + B t + C
        val d0 = a1 - a0
        val d1 = a2 - a1
        val d2 = a3 - a2
        val qa = d0 - 2 * d1 + d2
        val qb = 2 * (d1 - d0)
        val qc = d0
        val out = ArrayList<Double>(2)
        if (abs(qa) < 1e-12) {
            if (abs(qb) > 1e-12) out += -qc / qb
        } else {
            val disc = qb * qb - 4 * qa * qc
            if (disc >= 0) {
                val s = sqrt(disc)
                out += (-qb + s) / (2 * qa)
                out += (-qb - s) / (2 * qa)
            }
        }
        return out.filter { it > 0.0 && it < 1.0 }
    }
}

data class SubPath(val anchors: List<Anchor>, val closed: Boolean = false) {

    fun segments(): List<Segment> {
        if (anchors.size < 2) return emptyList()
        val out = ArrayList<Segment>(anchors.size)
        val last = if (closed) anchors.size else anchors.size - 1
        for (i in 0 until last) {
            val a = anchors[i]
            val b = anchors[(i + 1) % anchors.size]
            out += Segment(
                p0 = a.point,
                p1 = a.handleOut ?: a.point,
                p2 = b.handleIn ?: b.point,
                p3 = b.point,
                isLine = a.handleOut == null && b.handleIn == null,
            )
        }
        return out
    }

    fun bounds(): Rect? {
        if (anchors.isEmpty()) return null
        if (anchors.size == 1) return anchors[0].point.let { Rect(it.x, it.y, it.x, it.y) }
        return segments().map { it.bounds() }.reduce(Rect::union)
    }

    /** Eğrileri kısa doğru parçalarına böler; isabet testi için. */
    fun flatten(stepsPerCurve: Int = 16): List<Vec2> {
        if (anchors.isEmpty()) return emptyList()
        val out = ArrayList<Vec2>()
        out += anchors[0].point
        for (s in segments()) {
            if (s.isLine) {
                out += s.p3
            } else {
                for (i in 1..stepsPerCurve) out += s.pointAt(i.toDouble() / stepsPerCurve)
            }
        }
        return out
    }

    fun transformed(m: Matrix) = copy(anchors = anchors.map { it.transformed(m) })
}
