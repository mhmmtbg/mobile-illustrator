package io.github.mhmmtbg.mobileillustrator.render

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.PathOp
import org.jetbrains.skia.PathVerb
import kotlin.math.abs
import org.jetbrains.skia.Path as SkiaPath

/** Pathfinder işlemleri. */
enum class BooleanOp { Unite, MinusFront, Intersect, Exclude }

/** Şekilleri birleştirir, çıkarır, kesiştirir. Hesap Skia'nın yol işlemleriyle yapılır; eğriler eğri olarak korunur. */
object PathBoolean {

    class Result(val subpaths: List<SubPath>, val rule: FillRule)

    /**
     * @param nodes alttan üste doğru sıralı yollar; koordinatlar düğümün kendi dönüşümüyle belge uzayına taşınır.
     * @return belge uzayındaki sonuç; şekiller hiç kesişmiyorsa boş olabilir. İşlem başarısızsa `null`.
     */
    fun combine(nodes: List<PathNode>, op: BooleanOp): Result? {
        if (nodes.size < 2) return null
        val paths = nodes.map { n ->
            SkiaPath().also { DocumentRenderer.fillPath(it, n.subpaths.map { sp -> sp.transformed(n.transform) }, n.fillRule) }
        }
        val skOp = when (op) {
            BooleanOp.Unite -> PathOp.UNION
            BooleanOp.MinusFront -> PathOp.DIFFERENCE
            BooleanOp.Intersect -> PathOp.INTERSECT
            BooleanOp.Exclude -> PathOp.XOR
        }
        var acc = paths[0]
        for (i in 1 until paths.size) {
            acc = SkiaPath.makeCombining(acc, paths[i], skOp) ?: return null
        }
        val rule = if (acc.fillMode == PathFillMode.EVEN_ODD || acc.fillMode == PathFillMode.INVERSE_EVEN_ODD) FillRule.EvenOdd else FillRule.NonZero
        return Result(toSubPaths(acc), rule)
    }

    /** Skia yolunu Bezier alt yollarına çevirir; kuadratik ve konik parçalar kübiğe yükseltilir. */
    fun toSubPaths(path: SkiaPath): List<SubPath> {
        val out = ArrayList<SubPath>()
        var cur: ArrayList<Anchor>? = null
        var closed = false
        var px = 0.0
        var py = 0.0

        fun flush() {
            val c = cur
            if (c != null) {
                if (closed && c.size > 2) {
                    val first = c[0]
                    val last = c[c.size - 1]
                    if (abs(first.point.x - last.point.x) < 1e-4 && abs(first.point.y - last.point.y) < 1e-4) {
                        c[0] = first.copy(handleIn = last.handleIn)
                        c.removeAt(c.size - 1)
                    }
                }
                if (c.size >= 2) out += SubPath(c, closed)
            }
            cur = null
            closed = false
        }

        fun open(): ArrayList<Anchor> = cur ?: arrayListOf(Anchor(Vec2(px, py))).also { cur = it }

        fun cubic(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) {
            val c = open()
            val prev = c[c.size - 1]
            val h1 = Vec2(x1, y1)
            val h2 = Vec2(x2, y2)
            val end = Vec2(x, y)
            c[c.size - 1] = prev.copy(handleOut = h1.takeIf { it != prev.point })
            c += Anchor(end, handleIn = h2.takeIf { it != end })
            px = x; py = y
        }

        fun quad(qx: Double, qy: Double, ex: Double, ey: Double) =
            cubic(px + 2.0 / 3 * (qx - px), py + 2.0 / 3 * (qy - py), ex + 2.0 / 3 * (qx - ex), ey + 2.0 / 3 * (qy - ey), ex, ey)

        val it = path.iterator()
        while (it.hasNext()) {
            val seg = it.next() ?: break
            when (seg.verb) {
                PathVerb.MOVE -> {
                    flush()
                    val p = seg.p0 ?: continue
                    px = p.x.toDouble(); py = p.y.toDouble()
                    cur = arrayListOf(Anchor(Vec2(px, py)))
                }
                PathVerb.LINE -> {
                    val p = seg.p1 ?: continue
                    open() += Anchor(Vec2(p.x.toDouble(), p.y.toDouble()))
                    px = p.x.toDouble(); py = p.y.toDouble()
                }
                PathVerb.QUAD -> {
                    val q = seg.p1 ?: continue
                    val e = seg.p2 ?: continue
                    quad(q.x.toDouble(), q.y.toDouble(), e.x.toDouble(), e.y.toDouble())
                }
                PathVerb.CONIC -> {
                    // Konik (çember yayı) parça: ağırlığa göre kübik yaklaşımı.
                    val q = seg.p1 ?: continue
                    val e = seg.p2 ?: continue
                    val w = seg.conicWeight.toDouble()
                    val k = 4.0 * w / (3.0 * (1.0 + w))
                    cubic(
                        px + k * (q.x - px), py + k * (q.y - py),
                        e.x + k * (q.x - e.x), e.y + k * (q.y - e.y),
                        e.x.toDouble(), e.y.toDouble(),
                    )
                }
                PathVerb.CUBIC -> {
                    val a = seg.p1 ?: continue
                    val b = seg.p2 ?: continue
                    val e = seg.p3 ?: continue
                    cubic(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(), e.x.toDouble(), e.y.toDouble())
                }
                PathVerb.CLOSE -> {
                    closed = true
                    flush()
                }
                PathVerb.DONE -> {}
            }
        }
        flush()
        return out
    }

}
