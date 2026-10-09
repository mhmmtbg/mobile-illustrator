package io.github.mhmmtbg.mobileillustrator.pdf

import io.github.mhmmtbg.mobileillustrator.model.Ink
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import kotlin.math.pow

/** PDF renk uzayları. Her şey sRGB'ye indirgenir; renk yönetimi (ICC) uygulanmaz. */
sealed class ColorSpace {
    abstract val components: Int
    abstract fun toRgb(c: DoubleArray): Rgba

    /**
     * Vektör nesnelerin rengi için: sRGB karşılığına ek olarak özgün CMYK/gri/spot tanımını da taşır.
     * Görsellerde piksel başına gereksiz nesne üretmemek için [toRgb] kullanılır.
     */
    open fun toColor(c: DoubleArray): Rgba = toRgb(c)
    open fun initial(): DoubleArray = DoubleArray(components)

    object Gray : ColorSpace() {
        override val components = 1
        override fun toRgb(c: DoubleArray): Rgba {
            val g = c.getOrElse(0) { 0.0 }.coerceIn(0.0, 1.0)
            return Rgba(g, g, g)
        }

        override fun toColor(c: DoubleArray): Rgba = toRgb(c).let { it.copy(ink = Ink.Gray(it.r)) }
    }

    object Rgb : ColorSpace() {
        override val components = 3
        override fun toRgb(c: DoubleArray) = Rgba(
            c.getOrElse(0) { 0.0 }.coerceIn(0.0, 1.0),
            c.getOrElse(1) { 0.0 }.coerceIn(0.0, 1.0),
            c.getOrElse(2) { 0.0 }.coerceIn(0.0, 1.0),
        )
    }

    object Cmyk : ColorSpace() {
        override val components = 4
        override fun initial() = doubleArrayOf(0.0, 0.0, 0.0, 1.0)

        override fun toRgb(c: DoubleArray): Rgba = toColor(c).copy(ink = null)

        override fun toColor(c: DoubleArray): Rgba = Ink.Cmyk(
            c.getOrElse(0) { 0.0 }.coerceIn(0.0, 1.0),
            c.getOrElse(1) { 0.0 }.coerceIn(0.0, 1.0),
            c.getOrElse(2) { 0.0 }.coerceIn(0.0, 1.0),
            c.getOrElse(3) { 0.0 }.coerceIn(0.0, 1.0),
        ).toRgb()
    }

    class Lab(private val white: DoubleArray, private val range: DoubleArray) : ColorSpace() {
        override val components = 3
        override fun toRgb(c: DoubleArray): Rgba {
            val l = c.getOrElse(0) { 0.0 }.coerceIn(0.0, 100.0)
            val a = c.getOrElse(1) { 0.0 }.coerceIn(range[0], range[1])
            val b = c.getOrElse(2) { 0.0 }.coerceIn(range[2], range[3])
            val fy = (l + 16) / 116
            val fx = fy + a / 500
            val fz = fy - b / 200
            fun g(t: Double) = if (t > 6.0 / 29) t * t * t else 3 * (6.0 / 29) * (6.0 / 29) * (t - 4.0 / 29)
            val x = white[0] * g(fx)
            val y = white[1] * g(fy)
            val z = white[2] * g(fz)
            // D50 XYZ -> sRGB (Bradford uyarlamalı matris)
            val rl = 3.1338561 * x - 1.6168667 * y - 0.4906146 * z
            val gl = -0.9787684 * x + 1.9161415 * y + 0.0334540 * z
            val bl = 0.0719453 * x - 0.2289914 * y + 1.4052427 * z
            fun gamma(v: Double): Double {
                val u = v.coerceIn(0.0, 1.0)
                return if (u <= 0.0031308) 12.92 * u else 1.055 * u.pow(1 / 2.4) - 0.055
            }
            return Rgba(gamma(rl), gamma(gl), gamma(bl))
        }
    }

    class Indexed(private val base: ColorSpace, private val hival: Int, private val lookup: ByteArray) : ColorSpace() {
        override val components = 1
        override fun toRgb(c: DoubleArray): Rgba {
            val i = c.getOrElse(0) { 0.0 }.toInt().coerceIn(0, hival)
            val n = base.components
            val comps = DoubleArray(n) { (lookup.getOrElse(i * n + it) { 0 }.toInt() and 0xFF) / 255.0 }
            if (base is Lab) {
                comps[0] *= 100.0
                comps[1] = comps[1] * 255 - 128
                comps[2] = comps[2] * 255 - 128
            }
            return base.toRgb(comps)
        }
    }

    /** Separation / DeviceN: ton değerleri bir işlevle alternatif uzaya çevrilir. */
    class Tint(
        override val components: Int,
        private val alt: ColorSpace,
        private val fn: PdfFunction?,
        private val none: Boolean,
        /** Tek mürekkepli (Separation) uzayda mürekkebin adı. */
        private val name: String? = null,
    ) : ColorSpace() {
        override fun toColor(c: DoubleArray): Rgba {
            val rgb = toRgb(c)
            val f = fn
            if (name == null || components != 1 || f == null || name == "All") return rgb
            val space = when (alt) {
                is Cmyk -> "CMYK"
                is Lab -> "Lab"
                is Rgb -> "RGB"
                else -> return rgb
            }
            return try {
                rgb.copy(ink = Ink.Spot(name, c.getOrElse(0) { 1.0 }.coerceIn(0.0, 1.0), space, f.eval(doubleArrayOf(0.0)).toList(), f.eval(doubleArrayOf(1.0)).toList()))
            } catch (e: Exception) {
                rgb
            }
        }

        override fun initial() = DoubleArray(components) { 1.0 }
        val isNone: Boolean get() = none
        override fun toRgb(c: DoubleArray): Rgba {
            val f = fn ?: return Gray.toRgb(doubleArrayOf(1 - c.getOrElse(0) { 0.0 }))
            return alt.toRgb(f.eval(c))
        }
    }

    class Pattern(val under: ColorSpace?) : ColorSpace() {
        override val components = under?.components ?: 0
        override fun toRgb(c: DoubleArray): Rgba = under?.toRgb(c) ?: Rgba(0.5, 0.5, 0.5)
    }

    companion object {
        fun parse(file: PdfFile, obj: PdfObj?, resources: PdfDict?, depth: Int = 0): ColorSpace? {
            if (depth > 8) return null
            val r = file.resolve(obj)
            if (r is PdfName) {
                return when (r.name) {
                    "DeviceGray", "G", "CalGray" -> Gray
                    "DeviceRGB", "RGB", "CalRGB" -> Rgb
                    "DeviceCMYK", "CMYK" -> Cmyk
                    "Pattern" -> Pattern(null)
                    else -> {
                        val named = file.dict(resources?.get("ColorSpace"))?.get(r.name) ?: return null
                        parse(file, named, resources, depth + 1)
                    }
                }
            }
            val arrObj = r as? PdfArr ?: return null
            file.colorSpaceCache[arrObj]?.let { return it }
            val arr = arrObj.items
            val kind = file.name(arr.getOrNull(0)) ?: return null
            val parsed = parseArray(file, kind, arr, resources, depth)
            if (parsed != null) file.colorSpaceCache[arrObj] = parsed
            return parsed
        }

        private fun parseArray(file: PdfFile, kind: String, arr: List<PdfObj>, resources: PdfDict?, depth: Int): ColorSpace? {
            return when (kind) {
                "DeviceGray", "G", "CalGray" -> Gray
                "DeviceRGB", "RGB", "CalRGB" -> Rgb
                "DeviceCMYK", "CMYK" -> Cmyk
                "ICCBased" -> {
                    val st = file.stream(arr.getOrNull(1))
                    val d = st?.dict
                    // Profil başlığının 16-19. baytları verinin renk uzayını söyler ("RGB ", "Lab ", ...).
                    val sig = st?.let {
                        try { file.decodedBytes(it).takeIf { b -> b.size >= 20 }?.let { b -> String(b, 16, 4, Charsets.ISO_8859_1) } } catch (e: Exception) { null }
                    }
                    if (sig == "Lab ") {
                        val rg = file.numbers(d?.get("Range"))
                        return Lab(
                            doubleArrayOf(0.9642, 1.0, 0.8249),
                            if (rg != null && rg.size >= 6) doubleArrayOf(rg[2], rg[3], rg[4], rg[5]) else doubleArrayOf(-128.0, 127.0, -128.0, 127.0),
                        )
                    }
                    when (file.int(d?.get("N"))) {
                        1 -> Gray
                        3 -> Rgb
                        4 -> Cmyk
                        else -> parse(file, d?.get("Alternate"), resources, depth + 1)
                    }
                }
                "Lab" -> {
                    val d = file.dict(arr.getOrNull(1))
                    Lab(
                        file.numbers(d?.get("WhitePoint")) ?: doubleArrayOf(0.9642, 1.0, 0.8249),
                        file.numbers(d?.get("Range"))?.takeIf { it.size >= 4 } ?: doubleArrayOf(-100.0, 100.0, -100.0, 100.0),
                    )
                }
                "Indexed", "I" -> {
                    val base = parse(file, arr.getOrNull(1), resources, depth + 1) ?: return null
                    val hival = file.int(arr.getOrNull(2)) ?: 255
                    val lookup = when (val l = file.resolve(arr.getOrNull(3))) {
                        is PdfStr -> l.bytes
                        is PdfStream -> file.decodedBytes(l)
                        else -> ByteArray(0)
                    }
                    Indexed(base, hival, lookup)
                }
                "Separation" -> {
                    val alt = parse(file, arr.getOrNull(2), resources, depth + 1) ?: Gray
                    val ink = file.name(arr.getOrNull(1))
                    Tint(1, alt, PdfFunction.parse(file, arr.getOrNull(3)), ink == "None", ink)
                }
                "DeviceN" -> {
                    val names = file.array(arr.getOrNull(1)) ?: return null
                    val alt = parse(file, arr.getOrNull(2), resources, depth + 1) ?: Gray
                    Tint(names.size, alt, PdfFunction.parse(file, arr.getOrNull(3)), names.all { file.name(it) == "None" })
                }
                "Pattern" -> Pattern(arr.getOrNull(1)?.let { parse(file, it, resources, depth + 1) })
                else -> null
            }
        }
    }
}
