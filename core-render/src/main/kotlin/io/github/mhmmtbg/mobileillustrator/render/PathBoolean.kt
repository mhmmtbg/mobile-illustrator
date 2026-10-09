package io.github.mhmmtbg.mobileillustrator.render

import androidx.graphics.path.PathIterator
import androidx.graphics.path.PathSegment
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import kotlin.math.abs
import android.graphics.Path as AndroidPath

/** Pathfinder işlemleri. */
enum class BooleanOp { Unite, MinusFront, Intersect, Exclude }

/**
 * Şekilleri birleştirir, çıkarır, kesiştirir. Hesap Skia'nın yol işlemleriyle yapılır; eğriler
 * doğru parçalarına bölünmeden, eğri olarak korunur.
 */
object PathBoolean {

    class Result(val subpaths: List<SubPath>, val rule: FillRule)

    /**
     * @param nodes alttan üste doğru sıralı yollar; koordinatlar düğümün kendi dönüşümüyle belge uzayına taşınır.
     * @return belge uzayındaki sonuç; şekiller hiç kesişmiyorsa boş olabilir. İşlem başarısızsa `null`.
     */
    fun combine(nodes: List<PathNode>, op: BooleanOp): Result? {
        if (nodes.size < 2) return null
        val paths = nodes.map { n ->
            AndroidPath().also { DocumentRenderer.fillPath(it, n.subpaths.map { sp -> sp.transformed(n.transform) }, n.fillRule) }
        }
        val acc = AndroidPath(paths[0])
        val skOp = when (op) {
            BooleanOp.Unite -> AndroidPath.Op.UNION
            BooleanOp.MinusFront -> AndroidPath.Op.DIFFERENCE
            BooleanOp.Intersect -> AndroidPath.Op.INTERSECT
            BooleanOp.Exclude -> AndroidPath.Op.XOR
        }
        for (i in 1 until paths.size) {
            if (!acc.op(paths[i], skOp)) return null
        }
        val rule = if (acc.fillType == AndroidPath.FillType.EVEN_ODD || acc.fillType == AndroidPath.FillType.INVERSE_EVEN_ODD) FillRule.EvenOdd else FillRule.NonZero
        return Result(toSubPaths(acc), rule)
    }

    /** Android yolunu Bezier alt yollarına çevirir; kuadratik ve konik parçalar kübiğe yükseltilir. */
    fun toSubPaths(path: AndroidPath): List<SubPath> {
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

        val it = PathIterator(path, PathIterator.ConicEvaluation.AsQuadratics, 0.05f)
        while (it.hasNext()) {
            val seg = it.next()
            val p = seg.points
            when (seg.type) {
                PathSegment.Type.Move -> {
                    flush()
                    px = p[0].x.toDouble(); py = p[0].y.toDouble()
                    cur = arrayListOf(Anchor(Vec2(px, py)))
                }
                PathSegment.Type.Line -> {
                    open() += Anchor(Vec2(p[1].x.toDouble(), p[1].y.toDouble()))
                    px = p[1].x.toDouble(); py = p[1].y.toDouble()
                }
                PathSegment.Type.Quadratic -> {
                    val qx = p[1].x.toDouble(); val qy = p[1].y.toDouble()
                    val ex = p[2].x.toDouble(); val ey = p[2].y.toDouble()
                    cubic(px + 2.0 / 3 * (qx - px), py + 2.0 / 3 * (qy - py), ex + 2.0 / 3 * (qx - ex), ey + 2.0 / 3 * (qy - ey), ex, ey)
                }
                PathSegment.Type.Cubic -> cubic(
                    p[1].x.toDouble(), p[1].y.toDouble(), p[2].x.toDouble(), p[2].y.toDouble(), p[3].x.toDouble(), p[3].y.toDouble(),
                )
                PathSegment.Type.Conic -> {
                    // AsQuadratics seçiliyken beklenmez; yine de düz çizgiyle kapatılır.
                    val last = p[p.size - 1]
                    open() += Anchor(Vec2(last.x.toDouble(), last.y.toDouble()))
                    px = last.x.toDouble(); py = last.y.toDouble()
                }
                PathSegment.Type.Close -> {
                    closed = true
                    flush()
                }
                PathSegment.Type.Done -> {}
            }
        }
        flush()
        return out
    }
}
