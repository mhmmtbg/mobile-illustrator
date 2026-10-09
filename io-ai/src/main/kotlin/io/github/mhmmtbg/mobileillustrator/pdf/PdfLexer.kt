package io.github.mhmmtbg.mobileillustrator.pdf

/**
 * PDF sözdizimi çözümleyicisi. Hem dosya gövdesi hem içerik akışları için kullanılır.
 * Bozuk girdide istisna atmak yerine elinden geleni döndürmeye çalışır.
 */
class PdfLexer(val data: ByteArray, var pos: Int = 0, private val end: Int = data.size) {

    val atEnd: Boolean get() = pos >= end

    /** İç içe dizi/sözlük derinliği; kötü niyetli "[[[[..." girdilerinde yığının taşmasını önler. */
    private var depth = 0

    private fun isSpace(b: Int) = b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32
    private fun isDelim(b: Int) =
        b == '('.code || b == ')'.code || b == '<'.code || b == '>'.code || b == '['.code || b == ']'.code ||
            b == '{'.code || b == '}'.code || b == '/'.code || b == '%'.code

    fun skipSpace() {
        while (pos < end) {
            val b = data[pos].toInt() and 0xFF
            if (isSpace(b)) {
                pos++
            } else if (b == '%'.code) {
                while (pos < end && data[pos] != 10.toByte() && data[pos] != 13.toByte()) pos++
            } else {
                break
            }
        }
    }

    /** Sonraki nesneyi ya da işleci okur; girdi bittiyse `null`. Kapanış ayraçlarını [PdfOp] olarak döndürür. */
    fun next(): PdfObj? {
        skipSpace()
        if (pos >= end) return null
        val b = data[pos].toInt() and 0xFF
        return when {
            b == '/'.code -> readName()
            b == '('.code -> readLiteralString()
            b == '<'.code -> if (pos + 1 < end && data[pos + 1] == '<'.code.toByte()) readDict() else readHexString()
            b == '['.code -> readArray()
            b == ']'.code -> { pos++; PdfOp("]") }
            b == '>'.code -> { pos += if (pos + 1 < end && data[pos + 1] == '>'.code.toByte()) 2 else 1; PdfOp(">>") }
            b == '{'.code -> { pos++; PdfOp("{") }
            b == '}'.code -> { pos++; PdfOp("}") }
            b == ')'.code -> { pos++; next() }
            b == '+'.code || b == '-'.code || b == '.'.code || (b >= '0'.code && b <= '9'.code) -> readNumberOrRef()
            else -> readKeyword()
        }
    }

    private fun readToken(): String {
        val start = pos
        while (pos < end) {
            val b = data[pos].toInt() and 0xFF
            if (isSpace(b) || isDelim(b)) break
            pos++
        }
        if (pos == start) pos++ // ilerleme garantisi
        return String(data, start, pos - start, Charsets.ISO_8859_1)
    }

    private fun readKeyword(): PdfObj = when (val t = readToken()) {
        "true" -> PdfBool(true)
        "false" -> PdfBool(false)
        "null" -> PdfNull
        else -> PdfOp(t)
    }

    private fun readNumberOrRef(): PdfObj {
        val t = readToken()
        val num = parseNumber(t) ?: return PdfOp(t)
        // "12 0 R" biçimindeki dolaylı başvuru mu?
        if (num.isInt && num.value >= 0) {
            val save = pos
            skipSpace()
            if (pos < end && data[pos] >= '0'.code.toByte() && data[pos] <= '9'.code.toByte()) {
                val t2 = readToken()
                val gen = t2.toIntOrNull()
                if (gen != null) {
                    skipSpace()
                    if (pos < end && data[pos] == 'R'.code.toByte()) {
                        val after = if (pos + 1 < end) data[pos + 1].toInt() and 0xFF else 32
                        if (isSpace(after) || isDelim(after)) {
                            pos++
                            return PdfRef(num.value.toInt(), gen)
                        }
                    }
                }
            }
            pos = save
        }
        return num
    }

    private fun parseNumber(t: String): PdfNum? {
        t.toLongOrNull()?.let { return PdfNum(it.toDouble(), true) }
        // "--5", "5-" gibi bozuk yazımlar bazı üreticilerde görülür.
        val cleaned = t.replace("--", "-")
        val d = cleaned.toDoubleOrNull() ?: return null
        if (d.isNaN() || d.isInfinite()) return null
        return PdfNum(d, false)
    }

    private fun readName(): PdfName {
        pos++
        val out = java.io.ByteArrayOutputStream(24)
        var ascii = true
        while (pos < end) {
            val b = data[pos].toInt() and 0xFF
            if (isSpace(b) || isDelim(b)) break
            if (b == '#'.code && pos + 2 < end) {
                val h = hexVal(data[pos + 1].toInt()) * 16 + hexVal(data[pos + 2].toInt())
                if (h >= 0) {
                    out.write(h)
                    if (h >= 128) ascii = false
                    pos += 3
                    continue
                }
            }
            out.write(b)
            if (b >= 128) ascii = false
            pos++
        }
        val bytes = out.toByteArray()
        if (ascii) return PdfName(String(bytes, Charsets.ISO_8859_1))
        // Adlar kural olarak UTF-8'dir (ör. Türkçe karakterli spot renk adları); geçersizse Latin-1 sayılır.
        return PdfName(
            try {
                Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                String(bytes, Charsets.ISO_8859_1)
            },
        )
    }

    private fun hexVal(c: Int): Int = when (c) {
        in '0'.code..'9'.code -> c - '0'.code
        in 'a'.code..'f'.code -> c - 'a'.code + 10
        in 'A'.code..'F'.code -> c - 'A'.code + 10
        else -> -1000
    }

    private fun readLiteralString(): PdfStr {
        pos++
        val out = java.io.ByteArrayOutputStream()
        var depth = 1
        while (pos < end) {
            val b = data[pos].toInt() and 0xFF
            pos++
            when (b) {
                '('.code -> { depth++; out.write(b) }
                ')'.code -> { depth--; if (depth == 0) break else out.write(b) }
                '\\'.code -> {
                    if (pos >= end) break
                    val c = data[pos].toInt() and 0xFF
                    pos++
                    when (c) {
                        'n'.code -> out.write(10)
                        'r'.code -> out.write(13)
                        't'.code -> out.write(9)
                        'b'.code -> out.write(8)
                        'f'.code -> out.write(12)
                        13 -> if (pos < end && data[pos] == 10.toByte()) pos++
                        10 -> {}
                        in '0'.code..'7'.code -> {
                            var v = c - '0'.code
                            var n = 1
                            while (n < 3 && pos < end && data[pos] >= '0'.code.toByte() && data[pos] <= '7'.code.toByte()) {
                                v = v * 8 + (data[pos] - '0'.code.toByte())
                                pos++
                                n++
                            }
                            out.write(v and 0xFF)
                        }
                        else -> out.write(c)
                    }
                }
                else -> out.write(b)
            }
        }
        return PdfStr(out.toByteArray())
    }

    private fun readHexString(): PdfStr {
        pos++
        val out = java.io.ByteArrayOutputStream()
        var hi = -1
        while (pos < end) {
            val b = data[pos].toInt() and 0xFF
            pos++
            if (b == '>'.code) break
            val v = hexVal(b)
            if (v < 0) continue
            if (hi < 0) hi = v else { out.write(hi * 16 + v); hi = -1 }
        }
        if (hi >= 0) out.write(hi * 16)
        return PdfStr(out.toByteArray())
    }

    private fun readArray(): PdfArr {
        pos++
        val items = ArrayList<PdfObj>()
        if (depth >= MAX_DEPTH) return PdfArr(items)
        depth++
        try {
            readArrayItems(items)
        } finally {
            depth--
        }
        return PdfArr(items)
    }

    private fun readArrayItems(items: ArrayList<PdfObj>) {
        while (true) {
            val o = next() ?: break
            if (o is PdfOp) {
                if (o.name == "]") break
                continue // dizide beklenmeyen anahtar sözcük: yok say
            }
            items += o
        }
    }

    private fun readDict(): PdfDict {
        pos += 2
        val map = LinkedHashMap<String, PdfObj>()
        if (depth >= MAX_DEPTH) return PdfDict(map)
        depth++
        try {
            readDictItems(map)
        } finally {
            depth--
        }
        return PdfDict(map)
    }

    private fun readDictItems(map: LinkedHashMap<String, PdfObj>) {
        while (true) {
            val k = next() ?: break
            if (k is PdfOp && k.name == ">>") break
            if (k !is PdfName) continue
            val save = pos
            val v = next() ?: break
            if (v is PdfOp) {
                if (v.name == ">>") break
                pos = save
                readToken()
                continue
            }
            map[k.name] = v
        }
    }

    /** Verilen bayt dizisinin [from] sonrasındaki ilk konumu; yoksa -1. */
    fun indexOf(pattern: ByteArray, from: Int): Int = indexOf(data, pattern, from, end)

    companion object {
        private const val MAX_DEPTH = 96

        fun indexOf(data: ByteArray, pattern: ByteArray, from: Int, end: Int = data.size): Int {
            val last = end - pattern.size
            var i = maxOf(from, 0)
            outer@ while (i <= last) {
                for (j in pattern.indices) if (data[i + j] != pattern[j]) { i++; continue@outer }
                return i
            }
            return -1
        }

        fun lastIndexOf(data: ByteArray, pattern: ByteArray, from: Int = data.size - pattern.size): Int {
            var i = minOf(from, data.size - pattern.size)
            outer@ while (i >= 0) {
                for (j in pattern.indices) if (data[i + j] != pattern[j]) { i--; continue@outer }
                return i
            }
            return -1
        }
    }
}
