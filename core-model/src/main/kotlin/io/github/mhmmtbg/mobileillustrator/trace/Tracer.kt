package io.github.mhmmtbg.mobileillustrator.trace

import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.SubPath

/** Görseli vektöre çevirmenin (izlemenin) ayarları. */
data class TraceOptions(
    val mode: Mode = Mode.Color,
    /** Renkli izde hedef renk sayısı (2-48). */
    val colors: Int = 8,
    /** Siyah-beyaz izde eşik (0-1); `null` ise görsele göre kendiliğinden seçilir. */
    val threshold: Double? = null,
    /** Bu alandan (piksel) küçük lekeler komşu renge katılır. */
    val speckle: Int = 8,
    /** Eğrilerin piksel kenarından sapabileceği en büyük uzaklık (piksel). Büyük değer daha az düğüm, daha yumuşak kenar. */
    val smoothness: Double = 1.0,
    /** Köşe eşiği: küçük değerde daha çok dönüş keskin köşe olarak kalır (0 = çokgen), büyük değerde köşeler yuvarlanır (1,34 = hiç köşe yok). */
    val cornerThreshold: Double = 1.0,
    /** Görselin kenarlarını kaplayan zemin rengi izlenmez (saydam bırakılır). */
    val ignoreBackground: Boolean = false,
    /** En çok bu kadar ayrı şekil üretilir; aşılırsa her renk tek bir bileşik yol olur. */
    val maxShapes: Int = 1500,
) {
    enum class Mode { BlackWhite, Color }
}

/** Çözülmüş görsel: [argb] ön çarpımsız ARGB pikseller, satır satır. */
class PixelImage(val width: Int, val height: Int, val argb: IntArray)

/** İzlenen bir şekil: tek renkli, delikleri çift-tek kuralıyla boşaltılan kapalı yollar. Koordinatlar piksel cinsindendir. */
class TracedShape(val color: Rgba, val subpaths: List<SubPath>)

/** [shapes] alttan üste doğru sıralıdır: sonraki şekiller öncekilerin üzerine boyanır. */
class TraceResult(val width: Int, val height: Int, val shapes: List<TracedShape>, val colorCount: Int) {
    val anchorCount: Int get() = shapes.sumOf { s -> s.subpaths.sumOf { it.anchors.size } }
}

/** İzleme kullanıcı tarafından durdurulduğunda atılır. */
class TraceCancelled : RuntimeException()

/**
 * Piksel görselini vektör şekillere çevirir.
 *
 * Adımlar: (1) pikseller az sayıda renge indirgenir (ya da siyah-beyaz eşiklenir), (2) kenar yumuşatmasından
 * gelen ince ara renkler ve küçük lekeler temizlenir, (3) renkler alttan üste bir sıraya dizilir: her rengin şekli
 * kendi piksellerini, içinde kalan (üzerine boyanacak) şekillerin altını ve üstteki komşularının bir piksel altını
 * kaplar, böylece komşu şekiller arasında boşluk kalmaz, (4) her şeklin sınırı piksel kenarları boyunca izlenir,
 * (5) sınırlar köşeleri korunarak Bezier eğrilerine uydurulur.
 */
object Tracer {
    /**
     * @param pixels ARGB (ön çarpımsız) pikseller, satır satır; uzunluğu [width] × [height].
     * @param progress 0-1 arası ilerleme.
     * @param cancelled `true` dönerse izleme [TraceCancelled] ile bırakılır.
     */
    fun trace(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: TraceOptions = TraceOptions(),
        progress: (Double) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): TraceResult {
        require(width > 0 && height > 0 && pixels.size >= width * height) { "geçersiz görsel boyutu" }
        fun check() { if (cancelled()) throw TraceCancelled() }

        // 1) Renk indirgeme
        val quantized = if (options.mode == TraceOptions.Mode.BlackWhite) {
            Quantizer.blackWhite(pixels, width, height, options.threshold)
        } else {
            Quantizer.colors(pixels, width, height, options.colors.coerceIn(2, 48), ::check)
        }
        progress(0.3)
        check()

        // 2) Temizlik
        val labels = quantized.labels
        val palette = quantized.palette
        Regions.clean(labels, pixels, width, height, palette, options.speckle.coerceAtLeast(0), fringe = options.mode == TraceOptions.Mode.Color, check = ::check)
        if (options.ignoreBackground && options.mode == TraceOptions.Mode.Color) Regions.dropBackground(labels, width, height, palette.size)
        progress(0.45)
        check()

        // 3) Katman sırası: en çok alan kaplayan renk en alta gelir.
        val area = IntArray(palette.size)
        for (l in labels) if (l >= 0) area[l]++
        val order = palette.indices.filter { area[it] > 0 }.sortedByDescending { area[it] }
        val rank = IntArray(palette.size) { -1 }
        for ((r, label) in order.withIndex()) rank[label] = r

        // 4-5) Her katmanın sınırı izlenir ve eğrilere uydurulur.
        val mask = BooleanArray(width * height)
        val scratch = IntArray(width * height)
        val perRank = ArrayList<List<List<SubPath>>>(order.size)
        var shapeCount = 0
        for (r in order.indices) {
            check()
            Regions.layerMask(labels, rank, r, width, height, mask, scratch)
            val components = Contours.trace(mask, width, height)
            val fitted = ArrayList<List<SubPath>>(components.size)
            for (loops in components) {
                val subpaths = loops.mapNotNull { CurveFit.fitLoop(it, options.smoothness.coerceIn(0.2, 4.0), options.cornerThreshold.coerceIn(0.0, 1.34), width, height) }
                if (subpaths.isNotEmpty()) fitted += subpaths
            }
            perRank += fitted
            shapeCount += fitted.size
            progress(0.45 + 0.55 * (r + 1) / order.size)
        }
        // Çok fazla ayrı şekil çıkarsa her renk tek bileşik yol olur (belge hafif kalsın).
        val split = shapeCount <= options.maxShapes
        val shapes = ArrayList<TracedShape>()
        for ((r, components) in perRank.withIndex()) {
            val color = palette[order[r]]
            if (split) {
                for (subpaths in components) shapes += TracedShape(color, subpaths)
            } else if (components.isNotEmpty()) {
                shapes += TracedShape(color, components.flatten())
            }
        }
        return TraceResult(width, height, shapes, order.size)
    }
}
