package io.github.mhmmtbg.mobileillustrator.trace

import io.github.mhmmtbg.mobileillustrator.model.Rgba
import kotlin.math.sqrt

/** Renk etiketleri üzerinde bölge temizliği. */
internal object Regions {

    /**
     * Küçük lekeleri ve (istenirse) kenar yumuşatmasından kalan ince ara renk şeritlerini komşu renge katar.
     *
     * İnce şerit: bir pikselden pek geniş olmayan ve rengi iki komşusunun arasında kalan bölge. Bunlar özgün
     * görselde iki rengin sınırındaki geçiş pikselleridir; bırakılırsa her şeklin çevresinde gereksiz bir kontur oluşur.
     */
    fun clean(labels: IntArray, pixels: IntArray, width: Int, height: Int, palette: List<Rgba>, speckle: Int, fringe: Boolean, check: () -> Unit) {
        if (speckle <= 0 && !fringe) return
        val lab = FloatArray(palette.size * 3)
        for ((i, c) in palette.withIndex()) Quantizer.oklab(c.toArgb(), lab, i * 3)
        repeat(2) {
            check()
            if (!pass(labels, pixels, width, height, lab, speckle, fringe)) return
        }
    }

    private fun distance(lab: FloatArray, a: Int, b: Int): Float {
        val dl = lab[a * 3] - lab[b * 3]
        val da = lab[a * 3 + 1] - lab[b * 3 + 1]
        val db = lab[a * 3 + 2] - lab[b * 3 + 2]
        return sqrt(dl * dl + da * da + db * db)
    }

    /** @return bir şey değiştiyse `true`. */
    private fun pass(labels: IntArray, pixels: IntArray, width: Int, height: Int, lab: FloatArray, speckle: Int, fringe: Boolean): Boolean {
        val n = width * height
        // Bağlı bölgeler (4 komşuluk)
        val comp = IntArray(n) { -1 }
        val compLabel = ArrayList<Int>()
        val compArea = ArrayList<Int>()
        val stack = IntArray(n)
        for (start in 0 until n) {
            if (comp[start] >= 0) continue
            val id = compLabel.size
            val label = labels[start]
            var top = 0
            stack[top++] = start
            comp[start] = id
            var area = 0
            while (top > 0) {
                val p = stack[--top]
                area++
                val x = p % width
                if (x > 0 && comp[p - 1] < 0 && labels[p - 1] == label) { comp[p - 1] = id; stack[top++] = p - 1 }
                if (x < width - 1 && comp[p + 1] < 0 && labels[p + 1] == label) { comp[p + 1] = id; stack[top++] = p + 1 }
                if (p >= width && comp[p - width] < 0 && labels[p - width] == label) { comp[p - width] = id; stack[top++] = p - width }
                if (p < n - width && comp[p + width] < 0 && labels[p + width] == label) { comp[p + width] = id; stack[top++] = p + width }
            }
            compLabel += label
            compArea += area
        }
        val count = compLabel.size
        if (count <= 1) return false

        // Yalnızca aday bölgelerin (küçük ya da ince) komşuluk sınırları sayılır.
        val perimeter = IntArray(count)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = comp[y * width + x]
                if (x == 0 || x == width - 1) perimeter[c]++
                if (y == 0 || y == height - 1) perimeter[c]++
                if (x < width - 1) {
                    val o = comp[y * width + x + 1]
                    if (o != c) { perimeter[c]++; perimeter[o]++ }
                }
                if (y < height - 1) {
                    val o = comp[(y + 1) * width + x]
                    if (o != c) { perimeter[c]++; perimeter[o]++ }
                }
            }
        }
        val candidate = BooleanArray(count)
        var any = false
        for (c in 0 until count) {
            val small = compArea[c] < speckle
            val thin = fringe && compLabel[c] >= 0 && compArea[c] < 0.62 * perimeter[c]
            if (small || thin) {
                candidate[c] = true
                any = true
            }
        }
        if (!any) return false
        // Aday bölge -> (komşu etiket -> ortak kenar sayısı)
        val borders = HashMap<Long, Int>()
        fun add(c: Int, o: Int) {
            if (!candidate[c]) return
            val key = c.toLong() * 100 + (compLabel[o] + 1)
            borders[key] = (borders[key] ?: 0) + 1
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = comp[y * width + x]
                if (x < width - 1) {
                    val o = comp[y * width + x + 1]
                    if (o != c) { add(c, o); add(o, c) }
                }
                if (y < height - 1) {
                    val o = comp[(y + 1) * width + x]
                    if (o != c) { add(c, o); add(o, c) }
                }
            }
        }
        val best1 = IntArray(count) { Int.MIN_VALUE }
        val best1Count = IntArray(count)
        val best2 = IntArray(count) { Int.MIN_VALUE }
        val best2Count = IntArray(count)
        for ((key, shared) in borders) {
            val c = (key / 100).toInt()
            val label = (key % 100).toInt() - 1
            if (shared > best1Count[c]) {
                best2[c] = best1[c]; best2Count[c] = best1Count[c]
                best1[c] = label; best1Count[c] = shared
            } else if (shared > best2Count[c]) {
                best2[c] = label; best2Count[c] = shared
            }
        }
        // İnce bölgelerin özgün (indirgenmemiş) ortalama rengi: geçiş pikselleri palette ilgisiz bir renge atanmış
        // olabilir (siyah yazının gri kenarı, açıklığı benzer bir yeşile gibi); karar özgün renge göre verilir.
        val thinIndex = IntArray(count) { -1 }
        var thinCount = 0
        if (fringe) {
            for (c in 0 until count) {
                if (candidate[c] && compArea[c] >= speckle && compLabel[c] >= 0) thinIndex[c] = thinCount++
            }
        }
        val mean = FloatArray(thinCount * 3)
        if (thinCount > 0) {
            val sums = DoubleArray(thinCount * 3)
            val one = FloatArray(3)
            for (i in 0 until n) {
                val t = thinIndex[comp[i]]
                if (t < 0) continue
                Quantizer.oklab(pixels[i], one, 0)
                sums[t * 3] += one[0].toDouble(); sums[t * 3 + 1] += one[1].toDouble(); sums[t * 3 + 2] += one[2].toDouble()
            }
            for (c in 0 until count) {
                val t = thinIndex[c]
                if (t >= 0) for (ax in 0..2) mean[t * 3 + ax] = (sums[t * 3 + ax] / compArea[c]).toFloat()
            }
        }
        fun toPalette(t: Int, label: Int): Float {
            val dl = mean[t * 3] - lab[label * 3]
            val da = mean[t * 3 + 1] - lab[label * 3 + 1]
            val db = mean[t * 3 + 2] - lab[label * 3 + 2]
            return sqrt(dl * dl + da * da + db * db)
        }
        val target = IntArray(count) { Int.MIN_VALUE }
        val other = IntArray(count) { Int.MIN_VALUE }
        var changed = false
        for (c in 0 until count) {
            if (!candidate[c] || best1[c] == Int.MIN_VALUE) continue
            val label = compLabel[c]
            if (compArea[c] < speckle) {
                target[c] = best1[c]
                changed = true
                continue
            }
            // İnce şerit: rengi iki ana komşusunun arasındaysa geçiş pikselidir; kendisine yakın olana katılır.
            val a = best1[c]
            val b = best2[c]
            if (b == Int.MIN_VALUE || a < 0 || b < 0 || label < 0) continue
            if (best2Count[c] < 0.2 * perimeter[c]) continue
            val t = thinIndex[c]
            if (t < 0) continue
            val da = toPalette(t, a)
            val db = toPalette(t, b)
            if (da + db < 1.25f * distance(lab, a, b)) {
                target[c] = a
                other[c] = b
                changed = true
            }
        }
        if (!changed) return false
        val one = FloatArray(3)
        for (i in 0 until n) {
            val c = comp[i]
            val t = target[c]
            if (t == Int.MIN_VALUE) continue
            val o = other[c]
            if (o == Int.MIN_VALUE) {
                labels[i] = t
                continue
            }
            // Geçiş pikseli: iki komşu renkten kendi özgün rengine yakın olana katılır. Böylece sınır, iki rengin
            // yarı yarıya karıştığı yerden geçer ve kenar boyunca düzgün kalır.
            Quantizer.oklab(pixels[i], one, 0)
            fun d(label: Int): Float {
                val dl = one[0] - lab[label * 3]
                val da = one[1] - lab[label * 3 + 1]
                val db = one[2] - lab[label * 3 + 2]
                return dl * dl + da * da + db * db
            }
            labels[i] = if (d(t) <= d(o)) t else o
        }
        return true
    }

    /**
     * [r] sırasındaki rengin şeklinin maskesini kurar.
     *
     * Şekil, rengin kendi piksellerinden oluşur ve iki yerde üzerine boyanacak (sırası daha büyük) renklerin altına
     * uzanır: (1) tamamen o renklerle dolu delikler kapatılır (zemin, üzerindeki şekillerin altında kesintisiz devam
     * eder; üstteki şekil silinince altında delik kalmaz), (2) üstteki komşuların bir piksel altına girilir (iki
     * şeklin ortak sınırı ayrı ayrı eğrilere uydurulduğunda arada ince boşluk görünmesin).
     * Sırası daha küçük renklerin pikselleri hiçbir zaman kaplanmaz; böylece katmanlar sırayla boyandığında
     * her piksel kendi rengini alır.
     */
    fun layerMask(labels: IntArray, rank: IntArray, r: Int, width: Int, height: Int, mask: BooleanArray, scratch: IntArray) {
        val n = width * height
        // scratch: 0 = bakılmadı, 1 = kendi pikseli, 2 = dışarısı ya da korunacak delik, 3 = kapatılacak delik
        for (i in 0 until n) {
            val l = labels[i]
            scratch[i] = if (l >= 0 && rank[l] == r) 1 else 0
        }
        val stack = IntArray(n)
        val members = IntArray(n)
        for (start in 0 until n) {
            if (scratch[start] != 0) continue
            // Kendi rengi olmayan bağlı bölge: görselin kenarına değiyor mu, içinde alttaki bir renk (ya da saydamlık) var mı?
            var top = 0
            var count = 0
            var open = false
            stack[top++] = start
            scratch[start] = 2
            while (top > 0) {
                val p = stack[--top]
                members[count++] = p
                val l = labels[p]
                if (l < 0 || rank[l] < r) open = true
                val x = p % width
                if (x == 0 || x == width - 1 || p < width || p >= n - width) open = true
                if (x > 0 && scratch[p - 1] == 0) { scratch[p - 1] = 2; stack[top++] = p - 1 }
                if (x < width - 1 && scratch[p + 1] == 0) { scratch[p + 1] = 2; stack[top++] = p + 1 }
                if (p >= width && scratch[p - width] == 0) { scratch[p - width] = 2; stack[top++] = p - width }
                if (p < n - width && scratch[p + width] == 0) { scratch[p + width] = 2; stack[top++] = p + width }
            }
            if (!open) for (i in 0 until count) scratch[members[i]] = 3
        }
        for (i in 0 until n) {
            val s = scratch[i]
            if (s == 1 || s == 3) {
                mask[i] = true
                continue
            }
            // Üstteki komşunun bir piksel altına gir.
            val l = labels[i]
            var under = false
            if (l >= 0 && rank[l] > r) {
                val x = i % width
                under = (x > 0 && scratch[i - 1] == 1) || (x < width - 1 && scratch[i + 1] == 1) ||
                    (i >= width && scratch[i - width] == 1) || (i < n - width && scratch[i + width] == 1)
            }
            mask[i] = under
        }
    }

    /** Görselin kenarlarının çoğunu kaplayan renk zemin sayılır ve hiçbir yerde izlenmez (saydam kalır). */
    fun dropBackground(labels: IntArray, width: Int, height: Int, paletteSize: Int) {
        if (paletteSize == 0) return
        val onBorder = IntArray(paletteSize)
        var total = 0
        fun count(i: Int) {
            total++
            val l = labels[i]
            if (l >= 0) onBorder[l]++
        }
        for (x in 0 until width) { count(x); count((height - 1) * width + x) }
        for (y in 0 until height) { count(y * width); count(y * width + width - 1) }
        val background = onBorder.indices.maxByOrNull { onBorder[it] } ?: return
        if (onBorder[background] < total * 0.5) return
        for (i in labels.indices) if (labels[i] == background) labels[i] = -1
    }
}
