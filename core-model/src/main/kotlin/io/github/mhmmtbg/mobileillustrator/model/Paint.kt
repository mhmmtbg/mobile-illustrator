package io.github.mhmmtbg.mobileillustrator.model

/** Bileşenler 0..1 aralığında, sRGB. */
data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
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

    /** Koordinatlar nesnenin yerel uzayındadır. */
    data class LinearGradient(val start: Vec2, val end: Vec2, val stops: List<GradientStop>) : Paint

    data class RadialGradient(val center: Vec2, val radius: Double, val stops: List<GradientStop>) : Paint
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
