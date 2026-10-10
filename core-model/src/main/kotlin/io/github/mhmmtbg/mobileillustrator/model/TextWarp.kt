package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Metin eğme biçimleri. */
enum class WarpStyle {
    /** Yay: metin bir çember yayı boyunca dizilir. */
    Arc,

    /** Kemer: harfler dik kalır, satır ortada yükselir (ya da alçalır). */
    Arch,

    /** Dalga: satır bir dalga boyunca inip çıkar. */
    Wave,

    /** Şişkin: ortadaki harfler büyür (ya da daralır). */
    Bulge,

    /** Yükselen: satır bir uçtan diğerine doğru yükselir. */
    Rise,
}

/**
 * Metnin eğilmesi. [bend] -1 ile 1 arasındadır; 0'da metin düzdür, işareti yönü belirler.
 *
 * Eğme, harflerin dış hatlarına uygulanan bir koordinat dönüşümüdür: metin düzenlenebilir kalır, çizilirken
 * harf biçimleri alınır ve [apply] ile bükülür.
 */
data class TextWarp(val style: WarpStyle, val bend: Double) {

    /** [box] metnin eğilmemiş kutusudur; [p] o kutuya göre bükülür. */
    fun map(p: Vec2, box: Rect): Vec2 {
        val b = bend.coerceIn(-1.0, 1.0)
        val w = box.width
        val h = box.height
        if (b == 0.0 || w <= 1e-9) return p
        val cx = (box.left + box.right) / 2
        val cy = (box.top + box.bottom) / 2
        // u: yatayda -0,5 (sol kenar) ile 0,5 (sağ kenar) arası
        val u = (p.x - cx) / w
        val dome = 1 - 4 * u * u
        return when (style) {
            WarpStyle.Arc -> {
                // Kutunun alt kenarı yay olur; tam bükümde yarım çember. Yarıçap işaretlidir: eksi bükümde merkez üstte kalır.
                val theta = b * PI
                val r = w / theta
                val angle = u * theta
                val reach = r + (box.bottom - p.y)
                Vec2(cx + reach * sin(angle), box.bottom + r - reach * cos(angle))
            }
            WarpStyle.Arch -> Vec2(p.x, p.y - b * w * 0.25 * dome)
            WarpStyle.Wave -> Vec2(p.x, p.y - b * maxOf(h, w * 0.08) * 0.6 * sin(2 * PI * (u + 0.5)))
            WarpStyle.Bulge -> Vec2(p.x, cy + (p.y - cy) * (1 + b * 0.8 * dome))
            WarpStyle.Rise -> Vec2(p.x, p.y - b * w * 0.5 * u)
        }
    }

    /** Eğilmiş kutuyu kaplayan dikdörtgen (kutunun kenarları örneklenerek). */
    fun bounds(box: Rect): Rect {
        var left = Double.MAX_VALUE
        var top = Double.MAX_VALUE
        var right = -Double.MAX_VALUE
        var bottom = -Double.MAX_VALUE
        val steps = 24
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val x = box.left + box.width * t
            val y = box.top + box.height * t
            for (q in arrayOf(Vec2(x, box.top), Vec2(x, box.bottom), Vec2(box.left, y), Vec2(box.right, y), Vec2(x, (box.top + box.bottom) / 2))) {
                val m = map(q, box)
                if (m.x < left) left = m.x
                if (m.x > right) right = m.x
                if (m.y < top) top = m.y
                if (m.y > bottom) bottom = m.y
            }
        }
        return Rect(left, top, right, bottom)
    }

    /** Harf biçimlerini büker. Kutu, biçimlerin tamamını kaplayan dikdörtgendir. */
    fun apply(shapes: List<SubPath>): List<SubPath> {
        val box = shapes.mapNotNull { it.bounds() }.reduceOrNull(Rect::union) ?: return shapes
        return apply(shapes, box)
    }

    /**
     * Dönüşüm doğrusal olmadığından her parça önce kısa parçalara bölünür, sonra denetim noktaları taşınır;
     * düz çizgiler de eğri olarak bükülür.
     */
    fun apply(shapes: List<SubPath>, box: Rect): List<SubPath> {
        if (bend == 0.0) return shapes
        val step = maxOf(box.width, box.height) / 40
        return shapes.map { sp ->
            val anchors = sp.anchors
            val n = anchors.size
            if (n < 2) return@map SubPath(anchors.map { Anchor(map(it.point, box)) }, sp.closed)
            val xs = ArrayList<Vec2>() // art arda kübik parçalar: p0 c1 c2 p3 | c1 c2 p3 | …
            val segments = if (sp.closed) n else n - 1
            xs += map(anchors[0].point, box)
            for (i in 0 until segments) {
                val a = anchors[i]
                val b = anchors[(i + 1) % n]
                val p0 = a.point
                val p3 = b.point
                val c1 = a.handleOut ?: Vec2(p0.x + (p3.x - p0.x) / 3, p0.y + (p3.y - p0.y) / 3)
                val c2 = b.handleIn ?: Vec2(p3.x + (p0.x - p3.x) / 3, p3.y + (p0.y - p3.y) / 3)
                val length = hypot(c1.x - p0.x, c1.y - p0.y) + hypot(c2.x - c1.x, c2.y - c1.y) + hypot(p3.x - c2.x, p3.y - c2.y)
                val pieces = if (step <= 1e-9) 1 else ceil(length / step).toInt().coerceIn(1, 24)
                // de Casteljau: [t0, t1] aralığındaki alt eğrinin denetim noktaları
                var q0 = p0
                var q1 = c1
                var q2 = c2
                val q3 = p3
                for (k in 0 until pieces) {
                    // Kalan eğriyi (q0..q3) baştan 1/(pieces-k) oranında böl.
                    val t = 1.0 / (pieces - k)
                    val ab = lerp(q0, q1, t)
                    val bc = lerp(q1, q2, t)
                    val cd = lerp(q2, q3, t)
                    val abc = lerp(ab, bc, t)
                    val bcd = lerp(bc, cd, t)
                    val mid = lerp(abc, bcd, t)
                    xs += map(ab, box)
                    xs += map(abc, box)
                    xs += map(mid, box)
                    q0 = mid
                    q1 = bcd
                    q2 = cd
                }
            }
            // xs: [p0, (c1, c2, p3) × m]
            val m = (xs.size - 1) / 3
            val out = ArrayList<Anchor>(m + 1)
            for (j in 0..m) {
                if (sp.closed && j == m) break
                val point = xs[j * 3]
                val handleOut = if (j < m) xs[j * 3 + 1] else null
                val handleIn = when {
                    j > 0 -> xs[j * 3 - 1]
                    sp.closed -> xs[xs.size - 2]
                    else -> null
                }
                out += Anchor(point, handleIn = handleIn?.takeIf { !same(it, point) }, handleOut = handleOut?.takeIf { !same(it, point) })
            }
            SubPath(out, sp.closed)
        }
    }

    private fun lerp(a: Vec2, b: Vec2, t: Double) = Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun same(a: Vec2, b: Vec2) = abs(a.x - b.x) < 1e-9 && abs(a.y - b.y) < 1e-9
}
