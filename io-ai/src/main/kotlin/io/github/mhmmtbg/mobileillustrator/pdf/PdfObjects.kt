package io.github.mhmmtbg.mobileillustrator.pdf

class PdfException(message: String) : Exception(message)

sealed class PdfObj

object PdfNull : PdfObj()

data class PdfBool(val value: Boolean) : PdfObj()

data class PdfNum(val value: Double, val isInt: Boolean) : PdfObj()

class PdfStr(val bytes: ByteArray) : PdfObj() {
    /** PDF "text string": UTF-16BE (BOM'lu), UTF-8 (BOM'lu) ya da PDFDocEncoding (Latin-1'e yakın). */
    fun text(): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> String(bytes, Charsets.ISO_8859_1)
    }
}

data class PdfName(val name: String) : PdfObj()

class PdfArr(val items: List<PdfObj>) : PdfObj()

class PdfDict(val map: Map<String, PdfObj>) : PdfObj() {
    operator fun get(key: String): PdfObj? = map[key]

    companion object {
        val Empty = PdfDict(emptyMap())
    }
}

/** [raw] henüz süzgeçlerden geçmemiş veridir. */
class PdfStream(val dict: PdfDict, val raw: ByteArray) : PdfObj()

data class PdfRef(val num: Int, val gen: Int) : PdfObj()

/** İçerik akışındaki işleç (ör. `re`, `f`, `BT`). */
data class PdfOp(val name: String) : PdfObj()
