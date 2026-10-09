package io.github.mhmmtbg.mobileillustrator.svg

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** SVG `d` özniteliğini Bezier alt yollarına çevirir. Yaylar kübik eğrilere dönüştürülür. */
object SvgPathParser {

    fun parse(d: String): List<SubPath> = Builder().also { Scanner(d, it).run() }.finish()

    class Builder {
        private val done = ArrayList<SubPath>()
        private var cur: ArrayList<Anchor>? = null
        private var closed = false
        var start = Vec2.Zero
        var pos = Vec2.Zero

        private fun flush() {
            val c = cur
            if (c != null && c.size >= 2) done += SubPath(c, closed)
            cur = null
            closed = false
        }

        fun moveTo(p: Vec2) {
            flush()
            start = p
            pos = p
            cur = arrayListOf(Anchor(p))
        }

        private fun open(): ArrayList<Anchor> {
            val c = cur
            if (c != null && !closed) return c
            flush()
            pos = start
            return arrayListOf(Anchor(start)).also { cur = it }
        }

        fun lineTo(p: Vec2) {
            open() += Anchor(p)
            pos = p
        }

        fun curveTo(c1: Vec2, c2: Vec2, p: Vec2) {
            val c = open()
            val prev = c[c.size - 1]
            c[c.size - 1] = prev.copy(handleOut = c1.takeIf { it != prev.point })
            c += Anchor(p, handleIn = c2.takeIf { it != p })
            pos = p
        }

        fun close() {
            val c = cur ?: return
            if (closed) return
            if (c.size > 2) {
                val first = c[0]
                val last = c[c.size - 1]
                if (abs(first.point.x - last.point.x) < 1e-6 && abs(first.point.y - last.point.y) < 1e-6) {
                    c[0] = first.copy(handleIn = last.handleIn)
                    c.removeAt(c.size - 1)
                }
            }
            closed = true
            pos = start
        }

        fun finish(): List<SubPath> {
            flush()
            return done
        }

        /** SVG eliptik yayı (uç nokta parametreleriyle) en çok 90 derecelik kübik parçalara böler. */
        fun arcTo(rxIn: Double, ryIn: Double, rotationDeg: Double, largeArc: Boolean, sweep: Boolean, end: Vec2) {
            val p0 = pos
            var rx = abs(rxIn)
            var ry = abs(ryIn)
            if (rx == 0.0 || ry == 0.0 || p0 == end) {
                lineTo(end)
                return
            }
            val phi = Math.toRadians(rotationDeg)
            val cosP = cos(phi)
            val sinP = sin(phi)
            val dx = (p0.x - end.x) / 2
            val dy = (p0.y - end.y) / 2
            val x1 = cosP * dx + sinP * dy
            val y1 = -sinP * dx + cosP * dy
            val lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
            if (lambda > 1) {
                val s = sqrt(lambda)
                rx *= s
                ry *= s
            }
            val num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
            var coef = if (den == 0.0) 0.0 else sqrt(maxOf(0.0, num / den))
            if (largeArc == sweep) coef = -coef
            val cxp = coef * rx * y1 / ry
            val cyp = -coef * ry * x1 / rx
            val cx = cosP * cxp - sinP * cyp + (p0.x + end.x) / 2
            val cy = sinP * cxp + cosP * cyp + (p0.y + end.y) / 2
            val theta1 = atan2((y1 - cyp) / ry, (x1 - cxp) / rx)
            var delta = atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx) - theta1
            if (sweep && delta < 0) delta += 2 * PI
            if (!sweep && delta > 0) delta -= 2 * PI
            val n = maxOf(1, ceil(abs(delta) / (PI / 2) - 1e-9).toInt())
            val step = delta / n
            val t = 4.0 / 3.0 * tan(step / 4)
            fun at(a: Double) = Vec2(cx + rx * cos(a) * cosP - ry * sin(a) * sinP, cy + rx * cos(a) * sinP + ry * sin(a) * cosP)
            fun deriv(a: Double) = Vec2(-rx * sin(a) * cosP - ry * cos(a) * sinP, -rx * sin(a) * sinP + ry * cos(a) * cosP)
            var a = theta1
            for (i in 0 until n) {
                val b = a + step
                val pa = at(a)
                val pb = if (i == n - 1) end else at(b)
                val da = deriv(a)
                val db = deriv(b)
                curveTo(Vec2(pa.x + t * da.x, pa.y + t * da.y), Vec2(pb.x - t * db.x, pb.y - t * db.y), pb)
                a = b
            }
        }
    }

    private class Scanner(val s: String, val b: Builder) {
        var i = 0

        fun skip() {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
        }

        fun hasNumber(): Boolean {
            skip()
            if (i >= s.length) return false
            val c = s[i]
            return c == '-' || c == '+' || c == '.' || c.isDigit()
        }

        fun num(): Double {
            skip()
            val st = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                val save = i
                i++
                if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                if (i < s.length && s[i].isDigit()) {
                    while (i < s.length && s[i].isDigit()) i++
                } else {
                    i = save
                }
            }
            if (i == st) { i++; return 0.0 }
            return s.substring(st, i).toDoubleOrNull() ?: 0.0
        }

        /** Yay bayrakları tek hanedir ve ayraçsız yazılabilir ("a1 1 0 01.5.5"). */
        fun flag(): Boolean {
            skip()
            if (i < s.length && (s[i] == '0' || s[i] == '1')) return s[i++] == '1'
            return num() != 0.0
        }

        fun run() {
            var cmd = ' '
            var lastC2: Vec2? = null // önceki kübik kontrol noktası (S için)
            var lastQ: Vec2? = null // önceki kuadratik kontrol noktası (T için)
            while (true) {
                skip()
                if (i >= s.length) break
                val c = s[i]
                if (c.isLetter()) {
                    cmd = c
                    i++
                } else if (cmd == ' ' || !hasNumber()) {
                    i++
                    continue
                } else if (cmd == 'Z' || cmd == 'z') {
                    i++ // kapatmadan sonra komutsuz sayı: geçersiz, atla
                    continue
                } else if (cmd == 'M') {
                    cmd = 'L' // M'den sonraki ek koordinat çiftleri çizgidir
                } else if (cmd == 'm') {
                    cmd = 'l'
                }
                // Sayı bekleyen komutun ardından sayı gelmiyorsa komut boştur.
                if (cmd != 'Z' && cmd != 'z' && !hasNumber()) continue
                val rel = cmd.isLowerCase()
                val o = if (rel) b.pos else Vec2.Zero
                var c2: Vec2? = null
                var q: Vec2? = null
                when (cmd.uppercaseChar()) {
                    'M' -> b.moveTo(Vec2(o.x + num(), o.y + num()))
                    'L' -> b.lineTo(Vec2(o.x + num(), o.y + num()))
                    'H' -> b.lineTo(Vec2((if (rel) b.pos.x else 0.0) + num(), b.pos.y))
                    'V' -> b.lineTo(Vec2(b.pos.x, (if (rel) b.pos.y else 0.0) + num()))
                    'C' -> {
                        val c1 = Vec2(o.x + num(), o.y + num())
                        c2 = Vec2(o.x + num(), o.y + num())
                        b.curveTo(c1, c2, Vec2(o.x + num(), o.y + num()))
                    }
                    'S' -> {
                        val c1 = lastC2?.let { Vec2(2 * b.pos.x - it.x, 2 * b.pos.y - it.y) } ?: b.pos
                        c2 = Vec2(o.x + num(), o.y + num())
                        b.curveTo(c1, c2, Vec2(o.x + num(), o.y + num()))
                    }
                    'Q' -> {
                        q = Vec2(o.x + num(), o.y + num())
                        quad(q, Vec2(o.x + num(), o.y + num()))
                    }
                    'T' -> {
                        q = lastQ?.let { Vec2(2 * b.pos.x - it.x, 2 * b.pos.y - it.y) } ?: b.pos
                        quad(q, Vec2(o.x + num(), o.y + num()))
                    }
                    'A' -> {
                        val rx = num(); val ry = num(); val rot = num()
                        val large = flag(); val sweep = flag()
                        b.arcTo(rx, ry, rot, large, sweep, Vec2(o.x + num(), o.y + num()))
                    }
                    'Z' -> b.close()
                    else -> {}
                }
                lastC2 = c2
                lastQ = q
            }
        }

        private fun quad(q: Vec2, p: Vec2) {
            val p0 = b.pos
            b.curveTo(
                Vec2(p0.x + 2.0 / 3 * (q.x - p0.x), p0.y + 2.0 / 3 * (q.y - p0.y)),
                Vec2(p.x + 2.0 / 3 * (q.x - p.x), p.y + 2.0 / 3 * (q.y - p.y)),
                p,
            )
        }
    }
}
