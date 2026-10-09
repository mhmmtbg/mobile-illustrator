package io.github.mhmmtbg.mobileillustrator.model

/**
 * Rengin dosyadaki özgün tanımı. Ekranda her renk sRGB olarak gösterilir; ama baskıya giden dosyalarda
 * CMYK ve spot (Pantone vb.) değerlerin aynen korunması gerekir. Bu bilgi rengin yanında taşınır ve
 * .ai/PDF'e kaydederken RGB yerine o yazılır.
 */
sealed interface Ink {
    /** Baskı mürekkepleri, 0..1. */
    data class Cmyk(val c: Double, val m: Double, val y: Double, val k: Double) : Ink {
        /**
         * Ekran önizlemesi. Mürekkeplerin 16 köşe rengi arasında doğrusal karışım (xpdf/poppler ile aynı tablo);
         * `1-c` türü saf dönüşümden çok daha az cırtlak, Illustrator'ın gösterdiğine yakın sonuç verir.
         */
        fun toRgb(): Rgba {
            val cy = c.coerceIn(0.0, 1.0)
            val mg = m.coerceIn(0.0, 1.0)
            val ye = y.coerceIn(0.0, 1.0)
            val bk = k.coerceIn(0.0, 1.0)
            val c1 = 1 - cy
            val m1 = 1 - mg
            val y1 = 1 - ye
            val k1 = 1 - bk
            var x = c1 * m1 * y1 * k1
            var r = x; var g = x; var b = x
            x = c1 * m1 * y1 * bk; r += 0.1373 * x; g += 0.1216 * x; b += 0.1255 * x
            x = c1 * m1 * ye * k1; r += x; g += 0.9490 * x
            x = c1 * m1 * ye * bk; r += 0.1098 * x; g += 0.1020 * x
            x = c1 * mg * y1 * k1; r += 0.9255 * x; b += 0.5490 * x
            x = c1 * mg * y1 * bk; r += 0.1412 * x
            x = c1 * mg * ye * k1; r += 0.9294 * x; g += 0.1098 * x; b += 0.1412 * x
            x = c1 * mg * ye * bk; r += 0.1333 * x
            x = cy * m1 * y1 * k1; g += 0.6784 * x; b += 0.9373 * x
            x = cy * m1 * y1 * bk; g += 0.0588 * x; b += 0.1412 * x
            x = cy * m1 * ye * k1; g += 0.6510 * x; b += 0.3137 * x
            x = cy * m1 * ye * bk; g += 0.0745 * x
            x = cy * mg * y1 * k1; r += 0.1804 * x; g += 0.1922 * x; b += 0.5725 * x
            x = cy * mg * y1 * bk; b += 0.0078 * x
            x = cy * mg * ye * k1; r += 0.2118 * x; g += 0.2119 * x; b += 0.2235 * x
            return Rgba(r.coerceIn(0.0, 1.0), g.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0), ink = this)
        }
    }

    /** Tek kanallı gri (0 siyah, 1 beyaz). */
    data class Gray(val level: Double) : Ink

    /**
     * Spot renk (ör. "PANTONE 7524 C"). [tint] mürekkebin yoğunluğudur (1 tam ton).
     * [space] alternatif renk uzayıdır ("CMYK", "Lab" ya da "RGB"); [zero] ve [full], tonun 0 ve 1
     * olduğu durumlarda o uzaydaki değerlerdir. Dosyaya ad ve bu tanım aynen geri yazılır.
     */
    data class Spot(val name: String, val tint: Double, val space: String, val zero: List<Double>, val full: List<Double>) : Ink
}

/** Bileşenler 0..1 aralığında, sRGB. [ink] doluysa rengin dosyadaki özgün (CMYK/spot) tanımıdır. */
data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0, val ink: Ink? = null) {
    fun toArgb(): Int {
        fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
        return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    companion object {
        val Black = Rgba(0.0, 0.0, 0.0)
        val White = Rgba(1.0, 1.0, 1.0)

        fun fromArgb(argb: Int) = Rgba(
            r = ((argb shr 16) and 0xFF) / 255.0,
            g = ((argb shr 8) and 0xFF) / 255.0,
            b = (argb and 0xFF) / 255.0,
            a = ((argb ushr 24) and 0xFF) / 255.0,
        )

        /** `0xRRGGBB` biçiminde, tam opak. */
        fun rgb(hex: Int) = fromArgb(hex or (0xFF shl 24))
    }
}

data class GradientStop(val offset: Double, val color: Rgba)

sealed interface Paint {
    data class Solid(val color: Rgba) : Paint

    /**
     * Koordinatlar gradyan uzayındadır; [transform] gradyan uzayını nesnenin yerel uzayına taşır
     * (SVG'deki `gradientTransform`).
     */
    data class LinearGradient(
        val start: Vec2,
        val end: Vec2,
        val stops: List<GradientStop>,
        val transform: Matrix = Matrix.Identity,
    ) : Paint

    data class RadialGradient(
        val center: Vec2,
        val radius: Double,
        val stops: List<GradientStop>,
        val transform: Matrix = Matrix.Identity,
    ) : Paint
}

enum class LineCap { Butt, Round, Square }
enum class LineJoin { Miter, Round, Bevel }
enum class FillRule { NonZero, EvenOdd }

data class Stroke(
    val paint: Paint,
    val width: Double = 1.0,
    val cap: LineCap = LineCap.Butt,
    val join: LineJoin = LineJoin.Miter,
    val miterLimit: Double = 10.0,
    val dash: List<Double> = emptyList(),
    val dashOffset: Double = 0.0,
)

/** Boyayı [m] ile dönüştürür (gradyanların yönü ve ölçeği nesneyle birlikte taşınsın diye). */
fun Paint.transformedBy(m: Matrix): Paint = when (this) {
    is Paint.Solid -> this
    is Paint.LinearGradient -> copy(transform = m * transform)
    is Paint.RadialGradient -> copy(transform = m * transform)
}

/** Gradyanlar için temsilî tek renk (ilk durak); arayüz göstergeleri için. */
fun Paint.representativeColor(): Rgba = when (this) {
    is Paint.Solid -> color
    is Paint.LinearGradient -> stops.firstOrNull()?.color ?: Rgba.Black
    is Paint.RadialGradient -> stops.firstOrNull()?.color ?: Rgba.Black
}
