package io.github.mhmmtbg.mobileillustrator.pdf

import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.Rgba

/** PDF görsel nesnelerini PNG ya da JPEG baytlarına çevirir. */
internal object ImageDecoder {

    fun decode(file: PdfFile, xo: PdfStream, w: Int, h: Int, stencilColor: Rgba, sink: ContentSink): ImageData? {
        val d = xo.dict
        val dec = file.decode(xo)
        when (dec.pending) {
            null -> {}
            "DCTDecode", "DCT" -> {
                val cs = ColorSpace.parse(file, d["ColorSpace"], null)
                if (cs is ColorSpace.Cmyk) sink.warn("CMYK JPEG görsellerin renkleri farklı görünebilir")
                if (d["SMask"] != null) sink.warn("JPEG görsellerdeki saydamlık maskesi yok sayıldı")
                return ImageData(dec.bytes, "image/jpeg", w, h)
            }
            else -> {
                sink.warn("Desteklenmeyen görsel sıkıştırması: ${dec.pending}")
                return null
            }
        }
        val raw = dec.bytes
        val stencil = file.bool(d["ImageMask"]) == true
        val decodeArr = file.numbers(d["Decode"])
        val inverted = decodeArr != null && decodeArr.size >= 2 && decodeArr[0] > decodeArr[1]

        if (stencil) {
            val rowBytes = (w + 7) / 8
            if (raw.size < rowBytes * h) return null
            val r = ch(stencilColor.r); val g = ch(stencilColor.g); val b = ch(stencilColor.b)
            val png = PngEncoder.encode(w, h) { y, out ->
                for (x in 0 until w) {
                    val bit = (raw[y * rowBytes + x / 8].toInt() shr (7 - x % 8)) and 1
                    val paint = (bit == 0) != inverted
                    out[x * 4] = r; out[x * 4 + 1] = g; out[x * 4 + 2] = b
                    out[x * 4 + 3] = if (paint) (-1).toByte() else 0
                }
            }
            return ImageData(png, "image/png", w, h)
        }

        val cs = ColorSpace.parse(file, d["ColorSpace"], null) ?: return null
        val bpc = file.int(d["BitsPerComponent"]) ?: 8
        val n = cs.components
        if (n <= 0) return null
        val rowBytes = (w * n * bpc + 7) / 8
        if (raw.size < rowBytes * h) return null
        val maxV = ((1 shl bpc) - 1).toDouble()
        val indexed = cs is ColorSpace.Indexed
        val lab = cs is ColorSpace.Lab

        // Alfa kanalı: yumuşak maske (gri görsel)
        var alpha: ByteArray? = null
        var aw = 0
        var ah = 0
        file.stream(d["SMask"])?.let { sm ->
            val sd = file.decode(sm)
            val sw = file.int(sm.dict["Width"]) ?: 0
            val sh = file.int(sm.dict["Height"]) ?: 0
            val sb = file.int(sm.dict["BitsPerComponent"]) ?: 8
            if (sd.pending == null && sb == 8 && sw > 0 && sh > 0 && sd.bytes.size >= sw * sh) {
                alpha = sd.bytes; aw = sw; ah = sh
            } else {
                sink.warn("Bir görselin saydamlık maskesi okunamadı")
            }
        }

        val comps = DoubleArray(n)
        // Paletli görsellerde renk dönüşümünü her piksel için tekrarlama.
        val palette = if (indexed) arrayOfNulls<Rgba>(1 shl minOf(bpc, 8)) else null
        val png = PngEncoder.encode(w, h) { y, out ->
            val rowStart = y * rowBytes
            val alphaRow = alpha?.let { (y.toLong() * ah / h).toInt() * aw }
            for (x in 0 until w) {
                var rgb: Rgba? = null
                if (bpc == 8) {
                    val p = rowStart + x * n
                    if (indexed) {
                        val idx = raw[p].toInt() and 0xFF
                        rgb = palette!![idx] ?: cs.toRgb(doubleArrayOf(idx.toDouble())).also { palette[idx] = it }
                    } else {
                        for (k in 0 until n) comps[k] = (raw[p + k].toInt() and 0xFF) / 255.0
                    }
                } else {
                    val bitBase = x.toLong() * n * bpc
                    for (k in 0 until n) {
                        var v = 0
                        val start = bitBase + k.toLong() * bpc
                        for (bI in 0 until bpc) {
                            val pos = start + bI
                            val byte = raw[rowStart + (pos / 8).toInt()].toInt() and 0xFF
                            v = (v shl 1) or ((byte shr (7 - (pos % 8).toInt())) and 1)
                        }
                        comps[k] = if (indexed) v.toDouble() else v / maxV
                    }
                    if (indexed) {
                        val idx = comps[0].toInt()
                        rgb = palette!!.getOrNull(idx) ?: cs.toRgb(comps).also { if (idx in palette.indices) palette[idx] = it }
                    }
                }
                if (rgb == null) {
                    if (inverted) for (k in 0 until n) comps[k] = 1 - comps[k]
                    if (lab) {
                        comps[0] *= 100.0
                        comps[1] = comps[1] * 255 - 128
                        comps[2] = comps[2] * 255 - 128
                    }
                    rgb = cs.toRgb(comps)
                }
                out[x * 4] = ch(rgb.r); out[x * 4 + 1] = ch(rgb.g); out[x * 4 + 2] = ch(rgb.b)
                out[x * 4 + 3] = if (alphaRow != null) alpha!![alphaRow + (x.toLong() * aw / w).toInt()] else (-1).toByte()
            }
        }
        return ImageData(png, "image/png", w, h)
    }

    private fun ch(v: Double): Byte = (v.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt().toByte()
}
