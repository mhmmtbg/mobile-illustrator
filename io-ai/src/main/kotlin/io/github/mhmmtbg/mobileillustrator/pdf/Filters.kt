package io.github.mhmmtbg.mobileillustrator.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object Filters {

    /** Görsele özgü olup burada çözülmeyen süzgeçler; veri olduğu gibi bırakılır. */
    val ImageFilters = setOf("DCTDecode", "DCT", "JPXDecode", "CCITTFaxDecode", "CCF", "JBIG2Decode")

    class Decoded(val bytes: ByteArray, /** Çözülmeden kalan görsel süzgeci (ör. `DCTDecode`). */ val pending: String?)

    fun decode(raw: ByteArray, filters: List<String>, parms: List<PdfDict?>): Decoded {
        var data = raw
        for ((i, f) in filters.withIndex()) {
            val p = parms.getOrNull(i)
            data = when (f) {
                "FlateDecode", "Fl" -> predictor(inflate(data), p)
                "LZWDecode", "LZW" -> predictor(lzw(data, intOf(p, "EarlyChange", 1)), p)
                "ASCII85Decode", "A85" -> ascii85(data)
                "ASCIIHexDecode", "AHx" -> asciiHex(data)
                "RunLengthDecode", "RL" -> runLength(data)
                in ImageFilters -> return Decoded(data, f)
                "Crypt" -> data
                else -> throw PdfException("Desteklenmeyen süzgeç: $f")
            }
        }
        return Decoded(data, null)
    }

    private fun intOf(d: PdfDict?, key: String, def: Int): Int = (d?.get(key) as? PdfNum)?.value?.toInt() ?: def

    fun inflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(maxOf(data.size * 3, 1024))
        val buf = ByteArray(64 * 1024)
        var inf = Inflater()
        inf.setInput(data)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break
                }
                out.write(buf, 0, n)
            }
        } catch (e: DataFormatException) {
            // Başlıksız (raw deflate) veri olabilir; hiçbir şey çıkmadıysa onu dene.
            if (out.size() == 0) {
                inf.end()
                inf = Inflater(true)
                inf.setInput(data)
                try {
                    while (!inf.finished()) {
                        val n = inf.inflate(buf)
                        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                        out.write(buf, 0, n)
                    }
                } catch (_: DataFormatException) {
                }
            }
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }

    private fun predictor(data: ByteArray, p: PdfDict?): ByteArray {
        val predictor = intOf(p, "Predictor", 1)
        if (predictor <= 1) return data
        val colors = intOf(p, "Colors", 1)
        val bpc = intOf(p, "BitsPerComponent", 8)
        val columns = intOf(p, "Columns", 1)
        val bpp = maxOf(1, (colors * bpc + 7) / 8)
        val rowLen = (colors * bpc * columns + 7) / 8
        if (rowLen <= 0) return data
        if (predictor == 2) {
            if (bpc != 8) return data
            val out = data.copyOf()
            var row = 0
            while (row + rowLen <= out.size) {
                for (i in bpp until rowLen) out[row + i] = (out[row + i] + out[row + i - bpp]).toByte()
                row += rowLen
            }
            return out
        }
        // PNG tahmincileri: her satırın başında bir tür baytı vardır.
        val rows = data.size / (rowLen + 1)
        val out = ByteArray(rows * rowLen)
        var prev = -1
        for (r in 0 until rows) {
            val src = r * (rowLen + 1)
            val dst = r * rowLen
            val type = data[src].toInt() and 0xFF
            for (i in 0 until rowLen) {
                val x = data[src + 1 + i].toInt() and 0xFF
                val a = if (i >= bpp) out[dst + i - bpp].toInt() and 0xFF else 0
                val b = if (prev >= 0) out[prev + i].toInt() and 0xFF else 0
                val c = if (prev >= 0 && i >= bpp) out[prev + i - bpp].toInt() and 0xFF else 0
                val v = when (type) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> {
                        val pa = Math.abs(b - c)
                        val pb = Math.abs(a - c)
                        val pc = Math.abs(a + b - 2 * c)
                        x + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                    else -> x
                }
                out[dst + i] = v.toByte()
            }
            prev = dst
        }
        return out
    }

    private fun ascii85(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        val group = IntArray(5)
        var n = 0
        var i = 0
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            if (c == '~'.code) break
            if (c <= 32) continue
            if (c == 'z'.code && n == 0) {
                out.write(0); out.write(0); out.write(0); out.write(0)
                continue
            }
            if (c < '!'.code || c > 'u'.code) continue
            group[n++] = c - '!'.code
            if (n == 5) {
                var v = 0L
                for (k in 0 until 5) v = v * 85 + group[k]
                out.write((v shr 24).toInt() and 0xFF); out.write((v shr 16).toInt() and 0xFF)
                out.write((v shr 8).toInt() and 0xFF); out.write(v.toInt() and 0xFF)
                n = 0
            }
        }
        if (n > 1) {
            for (k in n until 5) group[k] = 84
            var v = 0L
            for (k in 0 until 5) v = v * 85 + group[k]
            val bytes = intArrayOf((v shr 24).toInt() and 0xFF, (v shr 16).toInt() and 0xFF, (v shr 8).toInt() and 0xFF)
            for (k in 0 until n - 1) out.write(bytes[k])
        }
        return out.toByteArray()
    }

    private fun asciiHex(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size / 2)
        var hi = -1
        for (b in data) {
            val c = b.toInt() and 0xFF
            if (c == '>'.code) break
            val v = when (c) {
                in '0'.code..'9'.code -> c - '0'.code
                in 'a'.code..'f'.code -> c - 'a'.code + 10
                in 'A'.code..'F'.code -> c - 'A'.code + 10
                else -> -1
            }
            if (v < 0) continue
            if (hi < 0) hi = v else { out.write(hi * 16 + v); hi = -1 }
        }
        if (hi >= 0) out.write(hi * 16)
        return out.toByteArray()
    }

    private fun runLength(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 2)
        var i = 0
        while (i < data.size) {
            val n = data[i].toInt() and 0xFF
            i++
            if (n == 128) break
            if (n < 128) {
                val len = minOf(n + 1, data.size - i)
                out.write(data, i, len)
                i += len
            } else if (i < data.size) {
                val b = data[i].toInt()
                i++
                repeat(257 - n) { out.write(b) }
            }
        }
        return out.toByteArray()
    }

    private fun lzw(data: ByteArray, earlyChange: Int): ByteArray {
        val out = ByteArrayOutputStream(data.size * 3)
        val table = arrayOfNulls<ByteArray>(4096)
        for (i in 0 until 256) table[i] = byteArrayOf(i.toByte())
        var next = 258
        var bits = 9
        var prev: ByteArray? = null
        var buf = 0
        var have = 0
        var i = 0
        while (true) {
            while (have < bits && i < data.size) {
                buf = (buf shl 8) or (data[i].toInt() and 0xFF)
                have += 8
                i++
            }
            if (have < bits) break
            val code = (buf shr (have - bits)) and ((1 shl bits) - 1)
            have -= bits
            if (code == 257) break
            if (code == 256) {
                next = 258
                bits = 9
                prev = null
                continue
            }
            val entry: ByteArray = when {
                code < next && table[code] != null -> table[code]!!
                prev != null -> prev + prev[0]
                else -> break
            }
            out.write(entry)
            if (prev != null && next < 4096) {
                table[next++] = prev + entry[0]
            }
            prev = entry
            if (next + earlyChange >= (1 shl bits) && bits < 12) bits++
        }
        return out.toByteArray()
    }
}
