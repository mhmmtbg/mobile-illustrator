package io.github.mhmmtbg.mobileillustrator.trace

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Piksel kenarlarından oluşan basamaklı sınırı düzgün Bezier eğrilerine çevirir.
 *
 * 1. Her basamak kenarının orta noktası alınır (eğik kenarlar böylece düz bir çizgiye dizilir).
 * 2. Bu noktalar, sınırdan en çok yarım tolerans sapan bir çokgene indirgenir; neredeyse düz kenarların ortasında
 *    kalan köşeler çıkarılır (eğik kenarlar piksel gürültüsüne rağmen tek doğru olur).
 * 3. Çokgenin her köşesi için karar verilir: komşularını birleştiren doğrudan yeterince uzaksa gerçek bir
 *    köşedir ve keskin kalır; değilse köşe, komşu kenarların orta noktaları arasında bir eğriyle yuvarlanır.
 *    Böylece düz kenarlar düz, köşeler keskin, yaylar yumuşak çıkar.
 * 4. Art arda gelen eğri parçaları, en küçük kareler yöntemiyle daha az sayıda kübik eğriye birleştirilir.
 */
internal object CurveFit {
    /** Bu uzunluktaki (piksel) iki düz kenarın birleştiği nokta gerçek bir köşedir (dikdörtgen köşesi gibi). */
    private const val LONG_EDGE = 3

    /**
     * @param loop kapalı döngünün köşe noktaları (x0, y0, x1, y1, …), kenarları yatay ya da dikey.
     * @param tolerance eğrinin piksel sınırından sapabileceği yaklaşık en büyük uzaklık (piksel).
     * @param width görselin genişliği; [height] ile birlikte görselin kenarını belirler. Kenar boyunca giden sınırlar düz
     * kalır ve sınırın kenardan ayrıldığı noktalar keskin köşe olur (komşu şekiller kenarda boşluk bırakmasın).
     * @param cornerThreshold köşe eşiği: 0'da her dönüş köşe sayılır (çokgen), 1 olağan değerdir, 1,34'te hiç köşe kalmaz.
     */
    fun fitLoop(loop: IntArray, tolerance: Double, cornerThreshold: Double, width: Int = Int.MAX_VALUE, height: Int = Int.MAX_VALUE): SubPath? {
        val n = loop.size / 2
        if (n < 4) return null
        // 1) Örnek noktalar
        val xs = ArrayList<Double>(n * 2)
        val ys = ArrayList<Double>(n * 2)
        var firstHard = -1
        val forcedAt = ArrayList<Int>() // görselin kenarından ayrılma noktaları: her zaman keskin köşe
        fun onFrame(i: Int): Boolean {
            val a = ((i % n) + n) % n
            val b = (a + 1) % n
            val x0 = loop[a * 2]; val y0 = loop[a * 2 + 1]; val x1 = loop[b * 2]; val y1 = loop[b * 2 + 1]
            return (x0 == x1 && (x0 == 0 || x0 == width)) || (y0 == y1 && (y0 == 0 || y0 == height))
        }
        fun len(i: Int): Int {
            val a = ((i % n) + n) % n
            val b = (a + 1) % n
            return abs(loop[b * 2] - loop[a * 2]) + abs(loop[b * 2 + 1] - loop[a * 2 + 1])
        }
        for (i in 0 until n) {
            val ax = loop[i * 2].toDouble()
            val ay = loop[i * 2 + 1].toDouble()
            val j = (i + 1) % n
            val bx = loop[j * 2].toDouble()
            val by = loop[j * 2 + 1].toDouble()
            val l = len(i)
            if (l == 0) continue
            val framePrev = onFrame(i - 1)
            val frameThis = onFrame(i)
            if (framePrev != frameThis || (framePrev && frameThis)) {
                // Kenara giriş/çıkış noktası ya da görselin köşesi
                if (firstHard < 0) firstHard = xs.size
                forcedAt += xs.size
                xs += ax; ys += ay
            } else if (len(i - 1) >= LONG_EDGE && l >= LONG_EDGE) {
                if (firstHard < 0) firstHard = xs.size
                xs += ax; ys += ay
            }
            // Her kenarın orta noktası: düzgün bir eğik çizginin basamaklarında bu noktalar aynı doğru üzerine düşer.
            xs += (ax + bx) / 2; ys += (ay + by) / 2
        }
        val m = xs.size
        if (m < 4) return null
        val px = DoubleArray(m) { xs[it] }
        val py = DoubleArray(m) { ys[it] }

        // 2) Çokgen (kapalı Douglas-Peucker): bir başlangıç noktası ve ona en uzak nokta sabit tutulur.
        val eps = tolerance * 0.5
        val keep = BooleanArray(m)
        val a0 = if (firstHard >= 0) firstHard else 0
        var b0 = a0
        var far = -1.0
        for (i in 0 until m) {
            val d = hypot(px[i] - px[a0], py[i] - py[a0])
            if (d > far) { far = d; b0 = i }
        }
        keep[a0] = true
        keep[b0] = true
        val forced = BooleanArray(m)
        for (f in forcedAt) { forced[f] = true; keep[f] = true }
        if (forcedAt.size >= 2) {
            // Zorunlu köşeler arasındaki her parça ayrı sadeleştirilir.
            for (q in forcedAt.indices) {
                val from = forcedAt[q]
                val to = forcedAt[(q + 1) % forcedAt.size]
                simplify(px, py, m, from, if (to > from) to else to + m, eps, keep)
            }
        } else {
            simplify(px, py, m, a0, if (b0 >= a0) b0 else b0 + m, eps, keep)
            simplify(px, py, m, b0, if (a0 > b0) a0 else a0 + m, eps, keep)
        }
        // Düzleştirme: bir köşeden başlayıp, aradaki tüm noktalar birleştiren doğruya yeterince yakın kaldıkça ileriye
        // uzanılır ve aradaki köşeler çıkarılır. Eğik kenarların piksel basamakları (düzenli küçük zikzaklar) böylece
        // tek doğruya iner; çember gibi gerçek eğrilerde doğru hemen sınırdan uzaklaştığı için köşeler yerinde kalır.
        val straightness = tolerance * 0.8
        fun straight(from: Int, to: Int): Boolean {
            val dx = px[to] - px[from]
            val dy = py[to] - py[from]
            val len = hypot(dx, dy)
            if (len < 1e-9) return false
            var i = (from + 1) % m
            while (i != to) {
                if (abs(dy * (px[i] - px[from]) - dx * (py[i] - py[from])) / len > straightness) return false
                i = (i + 1) % m
            }
            return true
        }
        val order = ArrayList<Int>()
        for (q in 0 until m) {
            val i = (a0 + q) % m
            if (keep[i]) order += i
        }
        if (order.size > 3) {
            var cur = 0
            var remaining = order.size
            while (cur < order.size) {
                var next = cur + 1
                // order[order.size] başlangıç köşesidir (döngü kapanır).
                while (next < order.size && remaining > 3 && !forced[order[next]] && straight(order[cur], order[(next + 1) % order.size])) {
                    keep[order[next]] = false
                    remaining--
                    next++
                }
                cur = next
            }
            // Başlangıç köşesi keyfi seçilmiş olabilir; düz bir kenarın ortasındaysa o da çıkarılır.
            if (remaining > 3 && !forced[a0]) {
                var before = a0
                do { before = (before - 1 + m) % m } while (!keep[before])
                var after = a0
                do { after = (after + 1) % m } while (!keep[after])
                if (before != after && straight(before, after)) keep[a0] = false
            }
        }
        var vx = DoubleArray(m)
        var vy = DoubleArray(m)
        var at = IntArray(m) // çokgen köşesinin örnek noktalar içindeki sırası
        var nv = 0
        for (i in 0 until m) if (keep[i]) { vx[nv] = px[i]; vy[nv] = py[i]; at[nv] = i; nv++ }
        if (nv < 3) {
            // Çok küçük şekil: örnek noktaların kendisi çokgen olur.
            vx = px; vy = py; nv = m
            at = IntArray(m) { it }
        }

        // Sivri köşeler: basamaklı sınırda gerçek bir köşe çoğu zaman birbirine çok yakın iki-üç köşeye bölünür ve
        // yuvarlanmış görünür. Böyle bir kümenin iki yanındaki uzun kenarlar uzatılıp kesiştirilir; kesişim kümeye
        // yeterince yakınsa küme tek bir keskin köşeyle değiştirilir.
        var sharp = BooleanArray(nv) { forced[at[it]] }
        if (nv >= 4) {
            val shortEdge = 5.0 * tolerance
            val longEdge = 8.0 * tolerance
            fun edge(i: Int) = hypot(vx[(i + 1) % nv] - vx[i % nv], vy[(i + 1) % nv] - vy[i % nv])
            // Kümeler döngünün başından taşabilir; uzun bir kenarın ucundan başlanır.
            val origin = (0 until nv).firstOrNull { edge(it) >= longEdge } ?: -1
            if (origin >= 0) {
                val ox = ArrayList<Double>(nv)
                val oy = ArrayList<Double>(nv)
                val oat = ArrayList<Int>(nv)
                val osharp = ArrayList<Boolean>(nv)
                var step = 1
                while (step <= nv) {
                    val j = (origin + step) % nv // kümenin ilk köşesi: önceki kenar (j-1 -> j)
                    var c = 0
                    var length = 0.0
                    while (step + c < nv && edge(j + c) <= shortEdge && !sharp[(j + c) % nv] && !sharp[(j + c + 1) % nv]) {
                        length += edge(j + c)
                        c++
                    }
                    val p = (j - 1 + nv) % nv
                    val last = (j + c) % nv
                    val nextV = (j + c + 1) % nv
                    var merged = false
                    if (c >= 1 && length <= 10.0 * tolerance && edge(p) >= longEdge && edge(last) >= longEdge && nv - c >= 3) {
                        val d1x = vx[j] - vx[p]
                        val d1y = vy[j] - vy[p]
                        val d2x = vx[nextV] - vx[last]
                        val d2y = vy[nextV] - vy[last]
                        val l1 = hypot(d1x, d1y)
                        val l2 = hypot(d2x, d2y)
                        val cross = d1x * d2y - d1y * d2x
                        // En az ~25 derecelik dönüş: daha küçük açılarda kesişim güvenilmezdir.
                        if (abs(cross) > 0.42 * l1 * l2) {
                            val t = ((vx[last] - vx[j]) * d2y - (vy[last] - vy[j]) * d2x) / cross
                            val ix = vx[j] + d1x * t
                            val iy = vy[j] + d1y * t
                            var nearest = Double.MAX_VALUE
                            for (q in 0..c) nearest = minOf(nearest, hypot(ix - vx[(j + q) % nv], iy - vy[(j + q) % nv]))
                            // Kesişim, kenarların uzantısında (geride değil) ve kümenin yakınında olmalı.
                            val ahead = (ix - vx[j]) * d1x + (iy - vy[j]) * d1y >= -0.75 * l1 * tolerance &&
                                (ix - vx[last]) * d2x + (iy - vy[last]) * d2y <= 0.75 * l2 * tolerance
                            if (nearest <= 2.5 * tolerance && ahead) {
                                ox += ix; oy += iy; oat += at[(j + c / 2) % nv]; osharp += true
                                merged = true
                            }
                        }
                    }
                    if (merged) {
                        step += c + 1
                    } else {
                        ox += vx[j]; oy += vy[j]; oat += at[j]; osharp += sharp[j]
                        step++
                    }
                }
                if (ox.size >= 3 && ox.size < nv) {
                    nv = ox.size
                    vx = DoubleArray(nv) { ox[it] }
                    vy = DoubleArray(nv) { oy[it] }
                    at = IntArray(nv) { oat[it] }
                    sharp = BooleanArray(nv) { osharp[it] }
                }
            }
        }

        // 3) Köşe mi, yumuşak mı
        val pieces = ArrayList<Cubic>(nv * 2)
        for (j in 0 until nv) {
            val i = (j - 1 + nv) % nv
            val k = (j + 1) % nv
            val mx0 = (vx[i] + vx[j]) / 2
            val my0 = (vy[i] + vy[j]) / 2
            val mx1 = (vx[j] + vx[k]) / 2
            val my1 = (vy[j] + vy[k]) / 2
            val cx = vx[k] - vx[i]
            val cy = vy[k] - vy[i]
            val chord = hypot(cx, cy)
            val deviation = if (chord < 1e-9) hypot(vx[j] - vx[i], vy[j] - vy[i]) else abs((vx[j] - vx[i]) * cy - (vy[j] - vy[i]) * cx) / chord
            val dd = deviation / tolerance
            var alpha = if (dd > 1) (1 - 1 / dd) / 0.75 else 0.0
            if (alpha >= cornerThreshold || sharp[j]) {
                pieces += line(mx0, my0, vx[j], vy[j])
                pieces += line(vx[j], vy[j], mx1, my1)
            } else {
                alpha = alpha.coerceIn(0.55, 1.0)
                val t = 0.5 + 0.5 * alpha
                pieces += Cubic(
                    mx0, my0,
                    vx[i] + (vx[j] - vx[i]) * t, vy[i] + (vy[j] - vy[i]) * t,
                    vx[k] + (vx[j] - vx[k]) * t, vy[k] + (vy[j] - vy[k]) * t,
                    mx1, my1, line = false,
                    vertex = j,
                )
            }
        }

        // 4) Sadeleştirme: aynı doğru üzerindeki ardışık doğru parçaları tek parça olur, ardışık eğriler birleştirilir.
        val merged = mergeRuns(mergeLines(pieces), tolerance, px, py, vx, vy, at, nv)
        if (merged.size < 2) return null
        // Bir pikselden küçük kalan (alanı olmayan) artıklar atılır.
        if (merged.maxOf { it.x0 } - merged.minOf { it.x0 } < 1.5 && merged.maxOf { it.y0 } - merged.minOf { it.y0 } < 1.5) return null
        val anchors = ArrayList<Anchor>(merged.size)
        for ((i, c) in merged.withIndex()) {
            val prev = merged[(i - 1 + merged.size) % merged.size]
            anchors += Anchor(
                Vec2(c.x0, c.y0),
                handleIn = if (prev.line) null else Vec2(prev.x2, prev.y2),
                handleOut = if (c.line) null else Vec2(c.x1, c.y1),
            )
        }
        return SubPath(anchors, closed = true)
    }

    private fun line(x0: Double, y0: Double, x1: Double, y1: Double) = Cubic(x0, y0, 0.0, 0.0, 0.0, 0.0, x1, y1, line = true)

    /** Douglas-Peucker; dizinler [m]'ye göre sarılır ([from] < [to] ≤ from + m). */
    private fun simplify(px: DoubleArray, py: DoubleArray, m: Int, from: Int, to: Int, eps: Double, keep: BooleanArray) {
        val stack = ArrayList<IntArray>()
        stack += intArrayOf(from, to)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeAt(stack.size - 1)
            if (b - a < 2) continue
            val ax = px[a % m]
            val ay = py[a % m]
            val dx = px[b % m] - ax
            val dy = py[b % m] - ay
            val len = hypot(dx, dy)
            var maxD = 0.0
            var idx = -1
            for (i in a + 1 until b) {
                val x = px[i % m] - ax
                val y = py[i % m] - ay
                val d = if (len < 1e-12) hypot(x, y) else abs(dy * x - dx * y) / len
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > eps) {
                keep[idx % m] = true
                stack += intArrayOf(a, idx)
                stack += intArrayOf(idx, b)
            }
        }
    }

    /** Aynı doğrultuda art arda gelen doğru parçalarını birleştirir (düz kenarın ortasında gereksiz düğüm kalmasın). */
    private fun mergeLines(pieces: List<Cubic>): List<Cubic> {
        if (pieces.size < 3) return pieces
        // Birleştirme döngünün başından taşabilir; bir köşeden (yön değişiminden) başlanır.
        val count = pieces.size
        fun collinear(a: Cubic, b: Cubic): Boolean {
            if (!a.line || !b.line) return false
            val ux = a.x3 - a.x0
            val uy = a.y3 - a.y0
            val wx = b.x3 - b.x0
            val wy = b.y3 - b.y0
            val cross = abs(ux * wy - uy * wx)
            return cross <= 1e-6 * maxOf(1.0, hypot(ux, uy) * hypot(wx, wy)) && ux * wx + uy * wy > 0
        }
        var start = 0
        while (start < count && collinear(pieces[(start - 1 + count) % count], pieces[start])) start++
        if (start == count) return pieces
        val out = ArrayList<Cubic>(count)
        var i = 0
        while (i < count) {
            var cur = pieces[(start + i) % count]
            var j = i + 1
            while (j < count && collinear(cur, pieces[(start + j) % count])) {
                val next = pieces[(start + j) % count]
                cur = line(cur.x0, cur.y0, next.x3, next.y3)
                j++
            }
            out += cur
            i = j
        }
        return out
    }

    /**
     * Art arda gelen eğri parçalarını daha az sayıda eğriyle değiştirir: parçaların kapsadığı özgün sınır
     * noktalarına, uçlardaki teğetler korunarak yeniden eğri uydurulur. Sonuç daha az düğümlü değilse özgün parçalar kalır.
     */
    private fun mergeRuns(
        pieces: List<Cubic>, tolerance: Double, px: DoubleArray, py: DoubleArray,
        vx: DoubleArray, vy: DoubleArray, at: IntArray, nv: Int,
    ): List<Cubic> {
        val count = pieces.size
        val m = px.size
        val error = (tolerance * 0.6) * (tolerance * 0.6)
        val firstLine = pieces.indexOfFirst { it.line }
        val out = ArrayList<Cubic>(count)
        fun fitRun(run: List<Cubic>) {
            if (run.size < 2) {
                out += run
                return
            }
            val first = run[0]
            val last = run[run.size - 1]
            // Parça dizisi, ilk köşeden önceki kenarın ortasından son köşeden sonraki kenarın ortasına uzanır.
            val jf = first.vertex
            val jl = last.vertex
            val i = (jf - 1 + nv) % nv
            val k = (jl + 1) % nv
            val xs = ArrayList<Double>()
            val ys = ArrayList<Double>()
            xs += first.x0; ys += first.y0
            // İlk kenarda orta noktadan sonraki, son kenarda orta noktadan önceki örnekler alınır.
            fun beyondHalf(s: Int, from: Int, to: Int): Boolean {
                val ex = vx[to] - vx[from]
                val ey = vy[to] - vy[from]
                return (px[s] - vx[from]) * ex + (py[s] - vy[from]) * ey > 0.5 * (ex * ex + ey * ey)
            }
            var s = (at[i] + 1) % m
            while (s != at[jf]) {
                if (beyondHalf(s, i, jf)) { xs += px[s]; ys += py[s] }
                s = (s + 1) % m
            }
            var guard = 0
            while (s != at[jl] && guard++ < m) {
                xs += px[s]; ys += py[s]
                s = (s + 1) % m
            }
            xs += px[s]; ys += py[s]
            s = (s + 1) % m
            while (s != at[k] && guard++ < m) {
                if (!beyondHalf(s, jl, k)) { xs += px[s]; ys += py[s] }
                s = (s + 1) % m
            }
            xs += last.x3; ys += last.y3
            val qx = DoubleArray(xs.size) { xs[it] }
            val qy = DoubleArray(ys.size) { ys[it] }
            var t1x = first.x1 - first.x0
            var t1y = first.y1 - first.y0
            var l = hypot(t1x, t1y)
            if (l < 1e-9) { t1x = first.x3 - first.x0; t1y = first.y3 - first.y0; l = maxOf(hypot(t1x, t1y), 1e-9) }
            t1x /= l; t1y /= l
            var t2x = last.x2 - last.x3
            var t2y = last.y2 - last.y3
            l = hypot(t2x, t2y)
            if (l < 1e-9) { t2x = last.x0 - last.x3; t2y = last.y0 - last.y3; l = maxOf(hypot(t2x, t2y), 1e-9) }
            t2x /= l; t2y /= l
            val before = out.size
            fitCubic(qx, qy, 0, qx.size - 1, t1x, t1y, t2x, t2y, error, out, 0)
            if (out.size - before >= run.size) {
                while (out.size > before) out.removeAt(out.size - 1)
                out += run
            }
        }
        if (firstLine < 0) {
            // Köşesiz kapalı eğri: tek parça olarak uydurulamaz (iki ucu aynı noktadır), ikiye bölünür.
            if (count < 4) return pieces
            val half = count / 2
            fitRun(pieces.subList(0, half))
            fitRun(pieces.subList(half, count))
            return out
        }
        var i = 0
        while (i < count) {
            val piece = pieces[(firstLine + i) % count]
            if (piece.line) {
                out += piece
                i++
                continue
            }
            val run = ArrayList<Cubic>()
            while (i < count && !pieces[(firstLine + i) % count].line) {
                run += pieces[(firstLine + i) % count]
                i++
            }
            fitRun(run)
        }
        return out
    }

    /** Bir doğru ya da kübik eğri parçası. [vertex]: eğrinin yuvarladığı çokgen köşesi (yalnızca eğrilerde). */
    private class Cubic(
        val x0: Double, val y0: Double, val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x3: Double, val y3: Double,
        val line: Boolean, val vertex: Int = -1,
    )

    private fun fitCubic(
        qx: DoubleArray, qy: DoubleArray, first: Int, last: Int,
        t1x: Double, t1y: Double, t2x: Double, t2y: Double, error: Double, out: ArrayList<Cubic>, depth: Int,
    ) {
        val count = last - first + 1
        if (count == 2) {
            val d = hypot(qx[last] - qx[first], qy[last] - qy[first]) / 3.0
            out += Cubic(qx[first], qy[first], qx[first] + t1x * d, qy[first] + t1y * d, qx[last] + t2x * d, qy[last] + t2y * d, qx[last], qy[last], line = false)
            return
        }
        var u = chordLength(qx, qy, first, last)
        var bez = generate(qx, qy, first, last, u, t1x, t1y, t2x, t2y)
        val split = IntArray(1)
        var maxError = maxError(qx, qy, first, last, bez, u, split)
        if (maxError < error) {
            out += bez.toCubic()
            return
        }
        if (maxError < error * 16) {
            repeat(4) {
                u = reparameterize(qx, qy, first, last, u, bez)
                bez = generate(qx, qy, first, last, u, t1x, t1y, t2x, t2y)
                maxError = maxError(qx, qy, first, last, bez, u, split)
                if (maxError < error) {
                    out += bez.toCubic()
                    return
                }
            }
        }
        if (depth > 40) {
            out += bez.toCubic()
            return
        }
        // Uymadı: en çok sapan noktadan ikiye bölünür.
        val s = split[0].coerceIn(first + 1, last - 1)
        var cx = qx[s - 1] - qx[s + 1]
        var cy = qy[s - 1] - qy[s + 1]
        val cl = hypot(cx, cy)
        if (cl < 1e-9) {
            cx = qx[s - 1] - qx[s]
            cy = qy[s - 1] - qy[s]
            val l2 = hypot(cx, cy)
            if (l2 < 1e-9) { cx = -t1x; cy = -t1y } else { cx /= l2; cy /= l2 }
        } else {
            cx /= cl; cy /= cl
        }
        fitCubic(qx, qy, first, s, t1x, t1y, cx, cy, error, out, depth + 1)
        fitCubic(qx, qy, s, last, -cx, -cy, t2x, t2y, error, out, depth + 1)
    }

    private class Bez(val x: DoubleArray, val y: DoubleArray) {
        fun toCubic() = Cubic(x[0], y[0], x[1], y[1], x[2], y[2], x[3], y[3], line = false)
    }

    private fun chordLength(qx: DoubleArray, qy: DoubleArray, first: Int, last: Int): DoubleArray {
        val u = DoubleArray(last - first + 1)
        for (i in first + 1..last) u[i - first] = u[i - first - 1] + hypot(qx[i] - qx[i - 1], qy[i] - qy[i - 1])
        val total = u[last - first]
        if (total > 0) for (i in 1..last - first) u[i] /= total
        return u
    }

    private fun generate(qx: DoubleArray, qy: DoubleArray, first: Int, last: Int, u: DoubleArray, t1x: Double, t1y: Double, t2x: Double, t2y: Double): Bez {
        val n = last - first + 1
        var c00 = 0.0
        var c01 = 0.0
        var c11 = 0.0
        var x0 = 0.0
        var x1 = 0.0
        val fx = qx[first]
        val fy = qy[first]
        val lx = qx[last]
        val ly = qy[last]
        for (i in 0 until n) {
            val t = u[i]
            val mt = 1 - t
            val b0 = mt * mt * mt
            val b1 = 3 * t * mt * mt
            val b2 = 3 * t * t * mt
            val b3 = t * t * t
            val a1x = t1x * b1
            val a1y = t1y * b1
            val a2x = t2x * b2
            val a2y = t2y * b2
            c00 += a1x * a1x + a1y * a1y
            c01 += a1x * a2x + a1y * a2y
            c11 += a2x * a2x + a2y * a2y
            val tx = qx[first + i] - (fx * (b0 + b1) + lx * (b2 + b3))
            val ty = qy[first + i] - (fy * (b0 + b1) + ly * (b2 + b3))
            x0 += a1x * tx + a1y * ty
            x1 += a2x * tx + a2y * ty
        }
        val det = c00 * c11 - c01 * c01
        var alpha1 = if (abs(det) < 1e-12) 0.0 else (x0 * c11 - x1 * c01) / det
        var alpha2 = if (abs(det) < 1e-12) 0.0 else (c00 * x1 - c01 * x0) / det
        val seg = hypot(lx - fx, ly - fy)
        val eps = 1e-6 * seg
        // Çözüm anlamsızsa (negatif ya da aşırı uzun tutamaç) güvenli bir tahmine dönülür.
        if (alpha1 < eps || alpha2 < eps || alpha1 > seg * 4 || alpha2 > seg * 4) {
            alpha1 = seg / 3
            alpha2 = seg / 3
        }
        return Bez(
            doubleArrayOf(fx, fx + t1x * alpha1, lx + t2x * alpha2, lx),
            doubleArrayOf(fy, fy + t1y * alpha1, ly + t2y * alpha2, ly),
        )
    }

    private fun eval(c: DoubleArray, t: Double): Double {
        val mt = 1 - t
        return mt * mt * mt * c[0] + 3 * t * mt * mt * c[1] + 3 * t * t * mt * c[2] + t * t * t * c[3]
    }

    private fun maxError(qx: DoubleArray, qy: DoubleArray, first: Int, last: Int, bez: Bez, u: DoubleArray, split: IntArray): Double {
        var max = 0.0
        split[0] = (first + last) / 2
        for (i in first + 1 until last) {
            val dx = eval(bez.x, u[i - first]) - qx[i]
            val dy = eval(bez.y, u[i - first]) - qy[i]
            val d = dx * dx + dy * dy
            if (d >= max) {
                max = d
                split[0] = i
            }
        }
        return max
    }

    /** Newton-Raphson ile her noktanın eğri üzerindeki parametresi iyileştirilir. */
    private fun reparameterize(qx: DoubleArray, qy: DoubleArray, first: Int, last: Int, u: DoubleArray, bez: Bez): DoubleArray {
        val out = DoubleArray(u.size)
        val d1x = DoubleArray(3) { 3 * (bez.x[it + 1] - bez.x[it]) }
        val d1y = DoubleArray(3) { 3 * (bez.y[it + 1] - bez.y[it]) }
        val d2x = DoubleArray(2) { 2 * (d1x[it + 1] - d1x[it]) }
        val d2y = DoubleArray(2) { 2 * (d1y[it + 1] - d1y[it]) }
        for (i in first..last) {
            val t = u[i - first]
            val mt = 1 - t
            val x = eval(bez.x, t) - qx[i]
            val y = eval(bez.y, t) - qy[i]
            val q1x = mt * mt * d1x[0] + 2 * t * mt * d1x[1] + t * t * d1x[2]
            val q1y = mt * mt * d1y[0] + 2 * t * mt * d1y[1] + t * t * d1y[2]
            val q2x = mt * d2x[0] + t * d2x[1]
            val q2y = mt * d2y[0] + t * d2y[1]
            val numerator = x * q1x + y * q1y
            val denominator = q1x * q1x + q1y * q1y + x * q2x + y * q2y
            out[i - first] = if (abs(denominator) < 1e-12) t else (t - numerator / denominator).coerceIn(0.0, 1.0)
        }
        return out
    }

}
