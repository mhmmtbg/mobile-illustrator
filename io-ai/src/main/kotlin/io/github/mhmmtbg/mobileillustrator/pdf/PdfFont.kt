package io.github.mhmmtbg.mobileillustrator.pdf

import java.nio.charset.Charset

/**
 * Metni Unicode'a çevirmek ve ilerleme genişliklerini bulmak için gereken kadar font bilgisi.
 * Glif çizimleri okunmaz; metin cihazdaki bir fontla gösterilir.
 */
class PdfFont private constructor(
    val baseName: String,
    private val twoByte: Boolean,
    private val toUnicode: Map<Int, String>?,
    private val encoding: Array<String?>?,
    private val widths: Map<Int, Double>,
    private val defaultWidth: Double,
    val serif: Boolean,
    val monospace: Boolean,
    val bold: Boolean,
    val italic: Boolean,
) {
    class Glyph(val text: String?, /** 1000'lik em biriminde ilerleme. */ val width: Double, val isSpace: Boolean)

    /** Bu fontla yazılmış metin güvenle Unicode'a çevrilebiliyor mu? */
    val decodable: Boolean get() = toUnicode != null || encoding != null

    fun decode(bytes: ByteArray): List<Glyph> {
        val out = ArrayList<Glyph>(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val code: Int
            if (twoByte && i + 1 < bytes.size) {
                code = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                i += 2
            } else {
                code = bytes[i].toInt() and 0xFF
                i++
            }
            val text = toUnicode?.get(code) ?: encoding?.getOrNull(code)
            out += Glyph(text, widths[code] ?: defaultWidth, !twoByte && code == 32)
        }
        return out
    }

    companion object {
        fun parse(file: PdfFile, dict: PdfDict): PdfFont {
            val subtype = file.name(dict["Subtype"])
            val rawName = file.name(dict["BaseFont"]) ?: file.name(dict["Name"]) ?: "Font"
            val baseName = rawName.substringAfter('+')
            val toUnicode = file.stream(dict["ToUnicode"])?.let {
                try { parseToUnicode(file.decodedBytes(it)) } catch (e: Exception) { null }
            }?.takeIf { it.isNotEmpty() }

            var descriptor: PdfDict? = file.dict(dict["FontDescriptor"])
            val widths = HashMap<Int, Double>()
            var defaultWidth = 500.0
            var twoByte = false
            var encoding: Array<String?>? = null

            if (subtype == "Type0") {
                twoByte = true
                val desc = file.dict(file.array(dict["DescendantFonts"])?.firstOrNull())
                descriptor = file.dict(desc?.get("FontDescriptor")) ?: descriptor
                defaultWidth = file.num(desc?.get("DW")) ?: 1000.0
                val w = file.array(desc?.get("W"))
                if (w != null) {
                    var k = 0
                    while (k < w.size) {
                        val first = file.int(w[k]) ?: break
                        val second = file.resolve(w.getOrNull(k + 1)) ?: break
                        if (second is PdfArr) {
                            second.items.forEachIndexed { j, o -> file.num(o)?.let { widths[first + j] = it } }
                            k += 2
                        } else {
                            val last = (second as? PdfNum)?.value?.toInt() ?: break
                            val width = file.num(w.getOrNull(k + 2)) ?: break
                            if (last - first < 70000) for (c in first..last) widths[c] = width
                            k += 3
                        }
                    }
                }
            } else {
                val first = file.int(dict["FirstChar"]) ?: 0
                file.array(dict["Widths"])?.forEachIndexed { j, o -> file.num(o)?.let { widths[first + j] = it } }
                defaultWidth = file.num(descriptor?.get("MissingWidth")) ?: 500.0
                // Type3 fontlarda genişlikler glif uzayındadır; FontMatrix genelde 0.001'dir.
                if (subtype == "Type3") {
                    val fm = file.numbers(dict["FontMatrix"])
                    val k = (fm?.getOrNull(0) ?: 0.001) * 1000
                    for (key in widths.keys.toList()) widths[key] = widths[key]!! * k
                }
                encoding = buildEncoding(file, dict["Encoding"], baseName)
            }

            val flags = file.int(descriptor?.get("Flags")) ?: 0
            val lower = baseName.lowercase()
            val serif = (flags and 2) != 0 && !lower.contains("sans") ||
                listOf("times", "serif", "garamond", "georgia", "minion", "caslon", "baskerville", "bodoni", "didot", "palatino")
                    .any { lower.contains(it) } && !lower.contains("sans")
            val mono = (flags and 1) != 0 || listOf("courier", "mono", "consol", "menlo").any { lower.contains(it) }
            val bold = listOf("bold", "black", "heavy", "semibold", "demi").any { lower.contains(it) } ||
                (file.num(descriptor?.get("FontWeight")) ?: 400.0) >= 600
            val italic = (flags and 64) != 0 || lower.contains("italic") || lower.contains("oblique") ||
                (file.num(descriptor?.get("ItalicAngle")) ?: 0.0) != 0.0
            return PdfFont(baseName, twoByte, toUnicode, encoding, widths, defaultWidth, serif, mono, bold, italic)
        }

        private fun charsetOrNull(name: String): Charset? = try { Charset.forName(name) } catch (e: Exception) { null }

        private fun baseTable(name: String?): Array<String?> {
            val cs = when (name) {
                "MacRomanEncoding" -> charsetOrNull("x-MacRoman") ?: charsetOrNull("macintosh")
                else -> charsetOrNull("windows-1252")
            } ?: Charsets.ISO_8859_1
            val table = arrayOfNulls<String>(256)
            for (i in 32 until 256) {
                val s = String(byteArrayOf(i.toByte()), cs)
                table[i] = if (s == "�") null else s
            }
            return table
        }

        private fun buildEncoding(file: PdfFile, enc: PdfObj?, baseName: String): Array<String?>? {
            val lower = baseName.lowercase()
            // Simge fontlarında kodların harf karşılığı yoktur.
            if (lower.contains("symbol") || lower.contains("dingbat") || lower.contains("wingding")) return null
            return when (val e = file.resolve(enc)) {
                is PdfName -> baseTable(e.name)
                is PdfDict -> {
                    val table = baseTable(file.name(e["BaseEncoding"]))
                    val diffs = file.array(e["Differences"])
                    if (diffs != null) {
                        var code = 0
                        for (d in diffs) {
                            when (val r = file.resolve(d)) {
                                is PdfNum -> code = r.value.toInt()
                                is PdfName -> {
                                    if (code in 0..255) table[code] = GlyphNames.toUnicode(r.name)
                                    code++
                                }
                                else -> {}
                            }
                        }
                    }
                    table
                }
                else -> baseTable(null)
            }
        }

        /** ToUnicode CMap: `bfchar` ve `bfrange` bloklarını okur. */
        private fun parseToUnicode(bytes: ByteArray): Map<Int, String> {
            val map = HashMap<Int, String>()
            val lx = PdfLexer(bytes)
            fun code(s: PdfStr): Int {
                var v = 0
                for (b in s.bytes) v = (v shl 8) or (b.toInt() and 0xFF)
                return v
            }
            fun text(s: PdfStr): String =
                if (s.bytes.size % 2 == 0) String(s.bytes, Charsets.UTF_16BE) else String(s.bytes, Charsets.ISO_8859_1)
            val pending = ArrayList<PdfObj>()
            var mode = 0
            while (true) {
                val o = lx.next() ?: break
                if (o is PdfOp) {
                    when (o.name) {
                        "beginbfchar" -> { mode = 1; pending.clear() }
                        "beginbfrange" -> { mode = 2; pending.clear() }
                        "endbfchar" -> {
                            var i = 0
                            while (i + 1 < pending.size) {
                                val a = pending[i] as? PdfStr
                                val b = pending[i + 1] as? PdfStr
                                if (a != null && b != null) map[code(a)] = text(b)
                                i += 2
                            }
                            mode = 0
                        }
                        "endbfrange" -> {
                            var i = 0
                            while (i + 2 < pending.size) {
                                val lo = pending[i] as? PdfStr
                                val hi = pending[i + 1] as? PdfStr
                                val dst = pending[i + 2]
                                if (lo != null && hi != null) {
                                    val a = code(lo)
                                    val b = minOf(code(hi), a + 70000)
                                    if (dst is PdfArr) {
                                        for (c in a..b) (dst.items.getOrNull(c - a) as? PdfStr)?.let { map[c] = text(it) }
                                    } else if (dst is PdfStr) {
                                        val base = text(dst)
                                        if (base.isNotEmpty()) {
                                            val head = base.dropLast(1)
                                            val last = base.last().code
                                            for (c in a..b) map[c] = head + (last + (c - a)).toChar()
                                        }
                                    }
                                }
                                i += 3
                            }
                            mode = 0
                        }
                    }
                } else if (mode != 0) {
                    pending += o
                }
            }
            return map
        }
    }
}

/** Sık kullanılan glif adlarının Unicode karşılıkları (Adobe Glyph List'in Latin kısmı). */
internal object GlyphNames {
    private val table: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        val pairs = "space:0020 exclam:0021 quotedbl:0022 numbersign:0023 dollar:0024 percent:0025 ampersand:0026 " +
            "quotesingle:0027 parenleft:0028 parenright:0029 asterisk:002A plus:002B comma:002C hyphen:002D period:002E " +
            "slash:002F zero:0030 one:0031 two:0032 three:0033 four:0034 five:0035 six:0036 seven:0037 eight:0038 " +
            "nine:0039 colon:003A semicolon:003B less:003C equal:003D greater:003E question:003F at:0040 " +
            "bracketleft:005B backslash:005C bracketright:005D asciicircum:005E underscore:005F grave:0060 " +
            "braceleft:007B bar:007C braceright:007D asciitilde:007E exclamdown:00A1 cent:00A2 sterling:00A3 " +
            "currency:00A4 yen:00A5 brokenbar:00A6 section:00A7 dieresis:00A8 copyright:00A9 ordfeminine:00AA " +
            "guillemotleft:00AB logicalnot:00AC registered:00AE macron:00AF degree:00B0 plusminus:00B1 acute:00B4 " +
            "mu:00B5 paragraph:00B6 periodcentered:00B7 cedilla:00B8 ordmasculine:00BA guillemotright:00BB " +
            "onequarter:00BC onehalf:00BD threequarters:00BE questiondown:00BF Agrave:00C0 Aacute:00C1 " +
            "Acircumflex:00C2 Atilde:00C3 Adieresis:00C4 Aring:00C5 AE:00C6 Ccedilla:00C7 Egrave:00C8 Eacute:00C9 " +
            "Ecircumflex:00CA Edieresis:00CB Igrave:00CC Iacute:00CD Icircumflex:00CE Idieresis:00CF Eth:00D0 " +
            "Ntilde:00D1 Ograve:00D2 Oacute:00D3 Ocircumflex:00D4 Otilde:00D5 Odieresis:00D6 multiply:00D7 " +
            "Oslash:00D8 Ugrave:00D9 Uacute:00DA Ucircumflex:00DB Udieresis:00DC Yacute:00DD Thorn:00DE " +
            "germandbls:00DF agrave:00E0 aacute:00E1 acircumflex:00E2 atilde:00E3 adieresis:00E4 aring:00E5 ae:00E6 " +
            "ccedilla:00E7 egrave:00E8 eacute:00E9 ecircumflex:00EA edieresis:00EB igrave:00EC iacute:00ED " +
            "icircumflex:00EE idieresis:00EF eth:00F0 ntilde:00F1 ograve:00F2 oacute:00F3 ocircumflex:00F4 " +
            "otilde:00F5 odieresis:00F6 divide:00F7 oslash:00F8 ugrave:00F9 uacute:00FA ucircumflex:00FB " +
            "udieresis:00FC yacute:00FD thorn:00FE ydieresis:00FF Gbreve:011E gbreve:011F Idotaccent:0130 " +
            "dotlessi:0131 Scedilla:015E scedilla:015F Lslash:0141 lslash:0142 OE:0152 oe:0153 Scaron:0160 " +
            "scaron:0161 Ydieresis:0178 Zcaron:017D zcaron:017E florin:0192 circumflex:02C6 caron:02C7 breve:02D8 " +
            "dotaccent:02D9 ring:02DA ogonek:02DB tilde:02DC hungarumlaut:02DD endash:2013 emdash:2014 " +
            "quoteleft:2018 quoteright:2019 quotesinglbase:201A quotedblleft:201C quotedblright:201D " +
            "quotedblbase:201E dagger:2020 daggerdbl:2021 bullet:2022 ellipsis:2026 perthousand:2030 " +
            "guilsinglleft:2039 guilsinglright:203A fraction:2044 Euro:20AC trademark:2122 minus:2212 fi:FB01 fl:FB02 " +
            "nbspace:00A0 sfthyphen:00AD"
        for (p in pairs.split(' ')) {
            val (n, h) = p.split(':')
            m[n] = h.toInt(16).toChar().toString()
        }
        for (c in 'A'..'Z') m[c.toString()] = c.toString()
        for (c in 'a'..'z') m[c.toString()] = c.toString()
        m
    }

    fun toUnicode(name: String): String? {
        table[name]?.let { return it }
        if (name.startsWith("uni") && name.length >= 7) {
            name.substring(3, 7).toIntOrNull(16)?.let { return it.toChar().toString() }
        }
        if (name.startsWith("u") && name.length in 5..7) {
            name.substring(1).toIntOrNull(16)?.let { return String(Character.toChars(it)) }
        }
        // "A.sc", "one.oldstyle" gibi türevler
        val base = name.substringBefore('.')
        if (base != name && base.isNotEmpty()) return toUnicode(base)
        return null
    }
}
