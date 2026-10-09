package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    fun distanceTo(o: Vec2): Double = hypot(x - o.x, y - o.y)

    companion object {
        val Zero = Vec2(0.0, 0.0)
    }
}

data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val center: Vec2 get() = Vec2((left + right) / 2, (top + bottom) / 2)

    fun contains(p: Vec2): Boolean = p.x in left..right && p.y in top..bottom

    fun union(o: Rect) = Rect(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun inflate(d: Double) = Rect(left - d, top - d, right + d, bottom + d)

    val corners: List<Vec2>
        get() = listOf(Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom))

    companion object {
        /** Köşeler hangi sırada verilirse verilsin düzgün (normalize) bir dikdörtgen üretir. */
        fun of(a: Vec2, b: Vec2) = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

        fun bounding(points: Iterable<Vec2>): Rect? {
            val it = points.iterator()
            if (!it.hasNext()) return null
            val first = it.next()
            var l = first.x
            var t = first.y
            var r = first.x
            var b = first.y
            while (it.hasNext()) {
                val p = it.next()
                l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
            }
            return Rect(l, t, r, b)
        }
    }
}

/**
 * 2B afin dönüşüm:
 * ```
 * | a c e |
 * | b d f |
 * | 0 0 1 |
 * ```
 * SVG ve PDF ile aynı düzen.
 */
data class Matrix(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val e: Double = 0.0,
    val f: Double = 0.0,
) {
    val isIdentity: Boolean get() = this == Identity

    fun apply(p: Vec2) = Vec2(a * p.x + c * p.y + e, b * p.x + d * p.y + f)

    /** `this * o`: önce [o], sonra `this` uygulanır. */
    operator fun times(o: Matrix) = Matrix(
        a = a * o.a + c * o.b,
        b = b * o.a + d * o.b,
        c = a * o.c + c * o.d,
        d = b * o.c + d * o.d,
        e = a * o.e + c * o.f + e,
        f = b * o.e + d * o.f + f,
    )

    val determinant: Double get() = a * d - b * c

    /** Tersi alınamıyorsa (dejenere ölçek) `null`. */
    fun inverse(): Matrix? {
        val det = determinant
        if (det == 0.0 || det.isNaN()) return null
        return Matrix(
            a = d / det,
            b = -b / det,
            c = -c / det,
            d = a / det,
            e = (c * f - d * e) / det,
            f = (b * e - a * f) / det,
        )
    }

    /** Kontur kalınlığı gibi uzunlukların yaklaşık ölçek katsayısı. */
    val meanScale: Double get() = kotlin.math.sqrt(kotlin.math.abs(determinant))

    companion object {
        val Identity = Matrix()
        fun translate(dx: Double, dy: Double) = Matrix(e = dx, f = dy)
        fun scale(sx: Double, sy: Double = sx) = Matrix(a = sx, d = sy)
        fun rotate(radians: Double): Matrix {
            val cs = cos(radians)
            val sn = sin(radians)
            return Matrix(cs, sn, -sn, cs, 0.0, 0.0)
        }
    }
}
