package io.github.mhmmtbg.mobileillustrator.trace

import io.github.mhmmtbg.mobileillustrator.model.Rgba
import kotlin.math.cbrt
import kotlin.math.pow

/** Piksellerin renk etiketleri: her piksel için palet sırası, saydam pikseller için -1. */
internal class Quantized(val labels: IntArray, val palette: List<Rgba>)

/** Görseli az sayıda renge indirger. */
internal object Quantizer {
    private const val OPAQUE = 128

    private fun luminance(argb: Int): Double {
        // Yarı saydam pikseller beyaz zemin üzerindeymiş gibi değerlendirilir.
        val a = (argb ushr 24) / 255.0
        val r = ((argb shr 16) and 0xFF) / 255.0 * a + (1 - a)
        val g = ((argb shr 8) and 0xFF) / 255.0 * a + (1 - a)
        val b = (argb and 0xFF) / 255.0 * a + (1 - a)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    /** Siyah-beyaz: koyu pikseller 0 (siyah), açık pikseller -1 (izlenmez). */
    fun blackWhite(pixels: IntArray, width: Int, height: Int, threshold: Double?): Quantized {
        val n = width * height
        val lum = IntArray(n) { (luminance(pixels[it]) * 255 + 0.5).toInt().coerceIn(0, 255) }
        val t = if (threshold != null) (threshold.coerceIn(0.0, 1.0) * 255).toInt() else otsu(lum)
        val labels = IntArray(n) { if (lum[it] <= t) 0 else -1 }
        return Quantized(labels, listOf(Rgba.rgb(0x000000)))
    }

    /** Otsu yöntemi: iki sınıfın arasındaki farkı en büyük yapan eşik. */
    private fun otsu(lum: IntArray): Int {
        val hist = IntArray(256)
        for (v in lum) hist[v]++
        val total = lum.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0.0
        var best = 127
        var bestVar = -1.0
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > bestVar) {
                bestVar = between
                best = i
            }
        }
        return best
    }

    private val linear = FloatArray(256) { i ->
        val c = i / 255.0
        (if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)).toFloat()
    }

    /** sRGB -> Oklab: bu uzayda uzaklık, gözün gördüğü renk farkına yakındır. */
    internal fun oklab(argb: Int, out: FloatArray, at: Int) {
        val r = linear[(argb shr 16) and 0xFF]
        val g = linear[(argb shr 8) and 0xFF]
        val b = linear[argb and 0xFF]
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        out[at] = (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat()
        out[at + 1] = (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat()
        out[at + 2] = (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat()
    }

    /**
     * Renkli: pikseller en çok [count] renge indirgenir.
     *
     * Palet, görseldeki farklı renklerin dökümünden kurulur: başlangıç renkleri birbirinden olabildiğince uzak
     * seçilir (büyük bir zemin rengi diğerlerini bastırmasın), sonra ağırlıklı k-ortalamalarla yerlerine oturtulur.
     */
    fun colors(pixels: IntArray, width: Int, height: Int, count: Int, check: () -> Unit): Quantized {
        val n = width * height
        // Renk dökümü: kanal başına 5 bit (32.768 kutu). Her kutunun ortalama rengi ve piksel sayısı tutulur.
        val binCount = IntArray(32768)
        val binR = LongArray(32768)
        val binG = LongArray(32768)
        val binB = LongArray(32768)
        var opaque = 0
        for (i in 0 until n) {
            val p = pixels[i]
            if ((p ushr 24) < OPAQUE) continue
            opaque++
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val bin = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            binCount[bin]++
            binR[bin] += r.toLong()
            binG[bin] += g.toLong()
            binB[bin] += b.toLong()
        }
        if (opaque == 0) return Quantized(IntArray(n) { -1 }, emptyList())
        val used = (0 until 32768).filter { binCount[it] > 0 }
        val points = FloatArray(used.size * 3)
        val weights = FloatArray(used.size)
        for ((i, bin) in used.withIndex()) {
            val c = binCount[bin]
            val argb = (0xFF shl 24) or ((binR[bin] / c).toInt() shl 16) or ((binG[bin] / c).toInt() shl 8) or (binB[bin] / c).toInt()
            oklab(argb, points, i * 3)
            weights[i] = c.toFloat()
        }
        check()
        var centers = spreadOut(points, weights, used.size, count)
        centers = kMeans(points, weights, used.size, centers, iterations = 12)
        check()
        centers = mergeClose(centers, 0.025f)
        val k = centers.size / 3

        // Her piksel en yakın renge atanır; palet rengi, atanan piksellerin ortalamasıdır.
        val labels = IntArray(n)
        val sumR = LongArray(k)
        val sumG = LongArray(k)
        val sumB = LongArray(k)
        val cnt = IntArray(k)
        val lab = FloatArray(3)
        // Aynı renk çok kez yinelenir (özellikle logolarda): son sonucu hatırlamak işi hızlandırır.
        var lastPixel = 0
        var lastLabel = -2
        for (i in 0 until n) {
            if (i and 0xFFFF == 0) check()
            val p = pixels[i]
            if ((p ushr 24) < OPAQUE) {
                labels[i] = -1
                continue
            }
            val rgb = p and 0xFFFFFF
            val label = if (lastLabel != -2 && rgb == lastPixel) lastLabel else {
                oklab(p, lab, 0)
                nearest(centers, k, lab[0], lab[1], lab[2]).also { lastPixel = rgb; lastLabel = it }
            }
            labels[i] = label
            sumR[label] += ((p shr 16) and 0xFF).toLong()
            sumG[label] += ((p shr 8) and 0xFF).toLong()
            sumB[label] += (p and 0xFF).toLong()
            cnt[label]++
        }
        val palette = List(k) { c ->
            if (cnt[c] == 0) Rgba.rgb(0) else Rgba(sumR[c] / 255.0 / cnt[c], sumG[c] / 255.0 / cnt[c], sumB[c] / 255.0 / cnt[c])
        }
        return Quantized(labels, palette)
    }

    private fun nearest(centers: FloatArray, k: Int, l: Float, a: Float, b: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (c in 0 until k) {
            val dl = centers[c * 3] - l
            val da = centers[c * 3 + 1] - a
            val db = centers[c * 3 + 2] - b
            val d = dl * dl + da * da + db * db
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    /**
     * Başlangıç renkleri: ilki en kalabalık renk; sonrakiler, seçilmiş olanlara en uzak ve yeterince kalabalık olanlar.
     * Kalabalıklığın etkisi köküyle sınırlanır: küçük ama belirgin bir renk (logodaki ince bir ayrıntı) de seçilebilir.
     */
    private fun spreadOut(points: FloatArray, weights: FloatArray, size: Int, count: Int): FloatArray {
        val k = minOf(count, size)
        val centers = FloatArray(k * 3)
        val nearest = FloatArray(size) { Float.MAX_VALUE }
        var pick = 0
        for (i in 1 until size) if (weights[i] > weights[pick]) pick = i
        for (c in 0 until k) {
            for (ax in 0..2) centers[c * 3 + ax] = points[pick * 3 + ax]
            var best = -1
            var bestScore = -1.0
            for (i in 0 until size) {
                val dl = points[i * 3] - centers[c * 3]
                val da = points[i * 3 + 1] - centers[c * 3 + 1]
                val db = points[i * 3 + 2] - centers[c * 3 + 2]
                val d = dl * dl + da * da + db * db
                if (d < nearest[i]) nearest[i] = d
                val score = nearest[i].toDouble() * Math.sqrt(weights[i].toDouble())
                if (score > bestScore) {
                    bestScore = score
                    best = i
                }
            }
            if (best < 0 || bestScore <= 0.0) return centers.copyOf((c + 1) * 3)
            pick = best
        }
        return centers
    }

    private fun kMeans(points: FloatArray, weights: FloatArray, size: Int, start: FloatArray, iterations: Int): FloatArray {
        val k = start.size / 3
        var centers = start
        repeat(iterations) {
            val sums = DoubleArray(k * 3)
            val totals = DoubleArray(k)
            for (i in 0 until size) {
                val c = nearest(centers, k, points[i * 3], points[i * 3 + 1], points[i * 3 + 2])
                val w = weights[i].toDouble()
                sums[c * 3] += points[i * 3] * w
                sums[c * 3 + 1] += points[i * 3 + 1] * w
                sums[c * 3 + 2] += points[i * 3 + 2] * w
                totals[c] += w
            }
            val next = centers.copyOf()
            for (c in 0 until k) {
                if (totals[c] == 0.0) continue
                for (ax in 0..2) next[c * 3 + ax] = (sums[c * 3 + ax] / totals[c]).toFloat()
            }
            centers = next
        }
        return centers
    }

    /** Birbirinden ayırt edilemeyecek kadar yakın renkler birleştirilir. */
    private fun mergeClose(centers: FloatArray, minDistance: Float): FloatArray {
        val k = centers.size / 3
        val keep = ArrayList<Int>()
        for (c in 0 until k) {
            val close = keep.any { o ->
                val dl = centers[c * 3] - centers[o * 3]
                val da = centers[c * 3 + 1] - centers[o * 3 + 1]
                val db = centers[c * 3 + 2] - centers[o * 3 + 2]
                dl * dl + da * da + db * db < minDistance * minDistance
            }
            if (!close) keep += c
        }
        val out = FloatArray(keep.size * 3)
        for ((i, c) in keep.withIndex()) for (ax in 0..2) out[i * 3 + ax] = centers[c * 3 + ax]
        return out
    }
}
