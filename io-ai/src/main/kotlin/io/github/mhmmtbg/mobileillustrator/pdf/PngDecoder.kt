package io.github.mhmmtbg.mobileillustrator.pdf

/** 8 bit, geçmesiz (non-interlaced) PNG'leri ham RGB + alfa olarak açar. PDF'e gömmek için. */
object PngDecoder {
    class Raw(val width: Int, val height: Int, val rgb: ByteArray, /** Tamamen opaksa `null`. */ val alpha: ByteArray?)

    fun decode(png: ByteArray): Raw? {
        if (png.size < 33 || png[1] != 'P'.code.toByte() || png[2] != 'N'.code.toByte()) return null
        var p = 8
        var w = 0
        var h = 0
        var depth = 0
        var type = 0
        var interlace = 0
        var palette: ByteArray? = null
        var trns: ByteArray? = null
        val idat = java.io.ByteArrayOutputStream(png.size)
        fun int(at: Int) = ((png[at].toInt() and 0xFF) shl 24) or ((png[at + 1].toInt() and 0xFF) shl 16) or
            ((png[at + 2].toInt() and 0xFF) shl 8) or (png[at + 3].toInt() and 0xFF)
        while (p + 8 <= png.size) {
            val len = int(p)
            val kind = String(png, p + 4, 4, Charsets.ISO_8859_1)
            val body = p + 8
            if (len < 0 || body + len > png.size) break
            when (kind) {
                "IHDR" -> { w = int(body); h = int(body + 4); depth = png[body + 8].toInt(); type = png[body + 9].toInt(); interlace = png[body + 12].toInt() }
                "PLTE" -> palette = png.copyOfRange(body, body + len)
                "tRNS" -> trns = png.copyOfRange(body, body + len)
                "IDAT" -> idat.write(png, body, len)
            }
            if (kind == "IEND") break
            p = body + len + 4
        }
        if (w <= 0 || h <= 0 || depth != 8 || interlace != 0) return null
        val channels = when (type) { 0 -> 1; 2 -> 3; 3 -> 1; 4 -> 2; 6 -> 4; else -> return null }
        val rowLen = w * channels
        val data = Filters.inflate(idat.toByteArray())
        if (data.size < (rowLen + 1) * h) return null
        val cur = ByteArray(rowLen)
        var prev = ByteArray(rowLen)
        val rgb = ByteArray(w * h * 3)
        val hasAlpha = type == 4 || type == 6 || (type == 3 && trns != null)
        val alpha = if (hasAlpha) ByteArray(w * h) else null
        var opaque = true
        for (y in 0 until h) {
            val src = y * (rowLen + 1)
            val ft = data[src].toInt() and 0xFF
            for (i in 0 until rowLen) {
                val x = data[src + 1 + i].toInt() and 0xFF
                val a = if (i >= channels) cur[i - channels].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= channels) prev[i - channels].toInt() and 0xFF else 0
                cur[i] = when (ft) {
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> {
                        val pa = Math.abs(b - c); val pb = Math.abs(a - c); val pc = Math.abs(a + b - 2 * c)
                        x + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                    else -> x
                }.toByte()
            }
            for (x in 0 until w) {
                val o = (y * w + x) * 3
                var al = 255
                when (type) {
                    0 -> { rgb[o] = cur[x]; rgb[o + 1] = cur[x]; rgb[o + 2] = cur[x] }
                    2 -> { rgb[o] = cur[x * 3]; rgb[o + 1] = cur[x * 3 + 1]; rgb[o + 2] = cur[x * 3 + 2] }
                    3 -> {
                        val idx = cur[x].toInt() and 0xFF
                        val pal = palette ?: return null
                        if (idx * 3 + 2 < pal.size) { rgb[o] = pal[idx * 3]; rgb[o + 1] = pal[idx * 3 + 1]; rgb[o + 2] = pal[idx * 3 + 2] }
                        if (trns != null && idx < trns.size) al = trns[idx].toInt() and 0xFF
                    }
                    4 -> { rgb[o] = cur[x * 2]; rgb[o + 1] = cur[x * 2]; rgb[o + 2] = cur[x * 2]; al = cur[x * 2 + 1].toInt() and 0xFF }
                    6 -> { rgb[o] = cur[x * 4]; rgb[o + 1] = cur[x * 4 + 1]; rgb[o + 2] = cur[x * 4 + 2]; al = cur[x * 4 + 3].toInt() and 0xFF }
                }
                if (alpha != null) {
                    alpha[y * w + x] = al.toByte()
                    if (al != 255) opaque = false
                }
            }
            System.arraycopy(cur, 0, prev, 0, rowLen)
        }
        return Raw(w, h, rgb, if (opaque) null else alpha)
    }

    /** JPEG başlığından bileşen sayısı (1 gri, 3 renkli, 4 CMYK); okunamazsa `null`. */
    fun jpegComponents(jpeg: ByteArray): Int? {
        var p = 2
        while (p + 9 < jpeg.size) {
            if (jpeg[p] != 0xFF.toByte()) { p++; continue }
            val marker = jpeg[p + 1].toInt() and 0xFF
            if (marker == 0xFF) { p++; continue }
            if (marker in 0xD0..0xD9 || marker == 0x01) { p += 2; continue }
            val len = ((jpeg[p + 2].toInt() and 0xFF) shl 8) or (jpeg[p + 3].toInt() and 0xFF)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return jpeg[p + 9].toInt() and 0xFF
            p += 2 + len
        }
        return null
    }
}
