package io.github.mhmmtbg.mobileillustrator.pdf

import io.github.mhmmtbg.mobileillustrator.model.tr

/**
 * Bir PDF dosyasının nesne deposu: çapraz başvuru tablosunu okur, nesneleri istendikçe çözer.
 * Illustrator dosyalarındaki dev özel veri akışlarına hiç dokunulmaz.
 */
class PdfFile(private val data: ByteArray) {

    private sealed class Entry {
        class Offset(val offset: Int) : Entry()
        class InStream(val streamNum: Int, val index: Int) : Entry()
    }

    private val xref = HashMap<Int, Entry>()
    private val cache = HashMap<Int, PdfObj>()
    private val objStreams = HashMap<Int, Pair<PdfLexer, IntArray>?>()
    private val resolving = HashSet<Int>()
    /** Aynı renk uzayı binlerce kez seçilebilir; çözümlenmiş hali burada tutulur. */
    val colorSpaceCache = java.util.IdentityHashMap<PdfObj, ColorSpace>()
    var trailer: PdfDict = PdfDict.Empty
        private set

    init {
        if (LexerUtil.find(data, "%PDF-", 0, minOf(data.size, 1024)) < 0) {
            throw PdfException(tr("PDF başlığı bulunamadı"))
        }
        val ok = try {
            readXrefChain()
        } catch (e: Exception) {
            false
        }
        if (!ok || trailer["Root"] == null) rebuild()
        if (trailer["Root"] == null) throw PdfException(tr("Belge kökü bulunamadı"))
        if (trailer["Encrypt"] != null) throw PdfException(tr("Şifreli dosyalar desteklenmiyor"))
    }

    val root: PdfDict get() = dict(trailer["Root"]) ?: throw PdfException(tr("Belge kökü okunamadı"))

    // ---- Çözümleme yardımcıları -------------------------------------------

    fun resolve(o: PdfObj?): PdfObj? {
        var cur = o
        var guard = 0
        while (cur is PdfRef && guard++ < 32) cur = getObject(cur.num)
        return if (cur is PdfNull) null else cur
    }

    fun dict(o: PdfObj?): PdfDict? = when (val r = resolve(o)) {
        is PdfDict -> r
        is PdfStream -> r.dict
        else -> null
    }

    fun array(o: PdfObj?): List<PdfObj>? = (resolve(o) as? PdfArr)?.items
    fun stream(o: PdfObj?): PdfStream? = resolve(o) as? PdfStream
    fun name(o: PdfObj?): String? = (resolve(o) as? PdfName)?.name
    fun num(o: PdfObj?): Double? = (resolve(o) as? PdfNum)?.value
    fun int(o: PdfObj?): Int? = num(o)?.toInt()
    fun bool(o: PdfObj?): Boolean? = (resolve(o) as? PdfBool)?.value
    fun string(o: PdfObj?): PdfStr? = resolve(o) as? PdfStr

    fun numbers(o: PdfObj?): DoubleArray? {
        val arr = array(o) ?: return null
        return DoubleArray(arr.size) { num(arr[it]) ?: 0.0 }
    }

    /** Akışı süzgeçlerinden geçirir. Görsel süzgeci kalırsa [Filters.Decoded.pending] doludur. */
    fun decode(s: PdfStream): Filters.Decoded {
        val f = resolve(s.dict["Filter"] ?: s.dict["F"])
        val filters = when (f) {
            is PdfName -> listOf(f.name)
            is PdfArr -> f.items.mapNotNull { name(it) }
            else -> emptyList()
        }
        val p = resolve(s.dict["DecodeParms"] ?: s.dict["DP"])
        val parms: List<PdfDict?> = when (p) {
            is PdfDict -> listOf(p)
            is PdfArr -> p.items.map { dict(it) }
            else -> emptyList()
        }
        return Filters.decode(s.raw, filters, parms)
    }

    fun decodedBytes(s: PdfStream): ByteArray = decode(s).bytes

    // ---- Nesne erişimi ----------------------------------------------------

    fun getObject(num: Int): PdfObj {
        cache[num]?.let { return it }
        if (!resolving.add(num)) return PdfNull
        try {
            val obj = when (val e = xref[num]) {
                null -> PdfNull
                is Entry.Offset -> readIndirectAt(e.offset, num) ?: PdfNull
                is Entry.InStream -> readFromObjectStream(e.streamNum, e.index) ?: PdfNull
            }
            cache[num] = obj
            return obj
        } finally {
            resolving.remove(num)
        }
    }

    private fun readIndirectAt(offset: Int, expectNum: Int?): PdfObj? {
        if (offset < 0 || offset >= data.size) return null
        val lx = PdfLexer(data, offset)
        val n = lx.next() as? PdfNum ?: return null
        val g = lx.next() as? PdfNum ?: return null
        val kw = lx.next() as? PdfOp ?: return null
        if (kw.name != "obj" || !n.isInt || !g.isInt) return null
        if (expectNum != null && n.value.toInt() != expectNum) return null
        val obj = lx.next() ?: return null
        if (obj is PdfDict) {
            val save = lx.pos
            val after = lx.next()
            if (after is PdfOp && after.name == "stream") {
                var p = lx.pos
                // "stream" sözcüğünden sonra CRLF ya da LF gelir.
                if (p < data.size && data[p] == 13.toByte()) p++
                if (p < data.size && data[p] == 10.toByte()) p++
                val declared = (if (obj["Length"] is PdfRef) resolve(obj["Length"]) else obj["Length"]) as? PdfNum
                var len = declared?.value?.toInt() ?: -1
                if (len < 0 || p + len > data.size || !endstreamFollows(p + len)) {
                    val e = PdfLexer.indexOf(data, ENDSTREAM, p)
                    len = if (e < 0) data.size - p else e - p
                    // endstream öncesindeki satır sonunu veri sayma
                    if (len > 0 && data[p + len - 1] == 10.toByte()) len--
                    if (len > 0 && data[p + len - 1] == 13.toByte()) len--
                }
                return PdfStream(obj, data.copyOfRange(p, p + maxOf(len, 0)))
            }
            lx.pos = save
        }
        return if (obj is PdfOp) null else obj
    }

    private fun endstreamFollows(at: Int): Boolean {
        var p = at
        var skipped = 0
        while (p < data.size && skipped < 4 && (data[p] == 10.toByte() || data[p] == 13.toByte() || data[p] == 32.toByte())) {
            p++
            skipped++
        }
        if (p + ENDSTREAM.size > data.size) return false
        for (i in ENDSTREAM.indices) if (data[p + i] != ENDSTREAM[i]) return false
        return true
    }

    private fun readFromObjectStream(streamNum: Int, index: Int): PdfObj? {
        val entry = objStreams.getOrPut(streamNum) {
            val s = getObject(streamNum) as? PdfStream ?: return@getOrPut null
            val n = int(s.dict["N"]) ?: return@getOrPut null
            val first = int(s.dict["First"]) ?: return@getOrPut null
            val bytes = decodedBytes(s)
            val head = PdfLexer(bytes, 0, minOf(first, bytes.size))
            val offsets = IntArray(n)
            for (i in 0 until n) {
                head.next()
                offsets[i] = first + ((head.next() as? PdfNum)?.value?.toInt() ?: 0)
            }
            PdfLexer(bytes) to offsets
        } ?: return null
        val (lx, offsets) = entry
        if (index !in offsets.indices) return null
        lx.pos = offsets[index]
        return lx.next()?.takeIf { it !is PdfOp }
    }

    // ---- Çapraz başvuru ---------------------------------------------------

    private fun readXrefChain(): Boolean {
        val sx = PdfLexer.lastIndexOf(data, "startxref".toByteArray())
        if (sx < 0) return false
        val lx = PdfLexer(data, sx + 9)
        var offset = (lx.next() as? PdfNum)?.value?.toInt() ?: return false
        val seen = HashSet<Int>()
        var first = true
        while (offset >= 0 && seen.add(offset)) {
            val t = readXrefSection(offset) ?: return !first
            if (first) {
                trailer = t
                first = false
            } else if (trailer["Root"] == null && t["Root"] != null) {
                trailer = PdfDict(t.map + trailer.map)
            }
            // Karma dosyalar: klasik tablo + XRefStm
            (t["XRefStm"] as? PdfNum)?.value?.toInt()?.let { if (seen.add(it)) readXrefSection(it) }
            offset = (t["Prev"] as? PdfNum)?.value?.toInt() ?: -1
        }
        return !first
    }

    private fun readXrefSection(offset: Int): PdfDict? {
        if (offset < 0 || offset >= data.size) return null
        val lx = PdfLexer(data, offset)
        lx.skipSpace()
        val isTable = lx.pos + 4 <= data.size && String(data, lx.pos, 4, Charsets.ISO_8859_1) == "xref"
        if (isTable) {
            lx.pos += 4
            while (true) {
                val save = lx.pos
                val a = lx.next()
                if (a is PdfOp && a.name == "trailer") break
                val start = (a as? PdfNum)?.value?.toInt() ?: run { lx.pos = save; return null }
                val count = (lx.next() as? PdfNum)?.value?.toInt() ?: return null
                for (i in 0 until count) {
                    val off = (lx.next() as? PdfNum)?.value?.toInt() ?: return null
                    lx.next() ?: return null
                    val kind = (lx.next() as? PdfOp)?.name ?: return null
                    val num = start + i
                    if (kind == "n" && !xref.containsKey(num)) xref[num] = Entry.Offset(off)
                    else if (kind == "f" && !xref.containsKey(num)) xref[num] = Entry.Offset(-1)
                }
            }
            return lx.next() as? PdfDict
        }
        // Çapraz başvuru akışı
        val s = readIndirectAt(offset, null) as? PdfStream ?: return null
        val d = s.dict
        val w = (d["W"] as? PdfArr)?.items?.map { (it as? PdfNum)?.value?.toInt() ?: 0 } ?: return null
        if (w.size < 3) return null
        val size = (d["Size"] as? PdfNum)?.value?.toInt() ?: 0
        val index = (d["Index"] as? PdfArr)?.items?.map { (it as? PdfNum)?.value?.toInt() ?: 0 } ?: listOf(0, size)
        val bytes = decodedBytes(s)
        val rowLen = w[0] + w[1] + w[2]
        if (rowLen <= 0) return null
        var p = 0
        fun field(n: Int, def: Int): Int {
            if (n == 0) return def
            var v = 0L
            repeat(n) { v = (v shl 8) or (bytes[p++].toLong() and 0xFF) }
            return v.toInt()
        }
        var k = 0
        while (k + 1 < index.size) {
            val start = index[k]
            val count = index[k + 1]
            for (i in 0 until count) {
                if (p + rowLen > bytes.size) break
                val type = field(w[0], 1)
                val f2 = field(w[1], 0)
                val f3 = field(w[2], 0)
                val num = start + i
                if (xref.containsKey(num)) continue
                when (type) {
                    0 -> xref[num] = Entry.Offset(-1)
                    1 -> xref[num] = Entry.Offset(f2)
                    2 -> xref[num] = Entry.InStream(f2, f3)
                }
            }
            k += 2
        }
        return d
    }

    /** Tablo bozuksa dosyayı baştan sona tarayıp "N G obj" başlıklarından yeniden kurar. */
    private fun rebuild() {
        xref.clear()
        cache.clear()
        objStreams.clear()
        val objKw = " obj".toByteArray()
        var i = 0
        var merged: Map<String, PdfObj> = emptyMap()
        while (true) {
            val at = PdfLexer.indexOf(data, objKw, i)
            if (at < 0) break
            i = at + 4
            // Geriye doğru "N G" oku
            var p = at - 1
            while (p >= 0 && data[p] >= '0'.code.toByte() && data[p] <= '9'.code.toByte()) p--
            val genStart = p + 1
            if (genStart > at - 1 || p < 0 || data[p] != 32.toByte()) continue
            var q = p - 1
            while (q >= 0 && data[q] >= '0'.code.toByte() && data[q] <= '9'.code.toByte()) q--
            val numStart = q + 1
            if (numStart > p - 1) continue
            val num = String(data, numStart, p - numStart, Charsets.ISO_8859_1).toIntOrNull() ?: continue
            xref[num] = Entry.Offset(numStart)
        }
        // Akış içindeki nesneler ve kök
        for ((num, e) in xref.toMap()) {
            if (e !is Entry.Offset) continue
            val obj = try { readIndirectAt(e.offset, num) } catch (ex: Exception) { null }
            val d = (obj as? PdfStream)?.dict ?: (obj as? PdfDict) ?: continue
            val type = (d["Type"] as? PdfName)?.name
            if (type == "XRef") merged = d.map + merged
            if (type == "Catalog" && merged["Root"] == null) merged = merged + ("Root" to PdfRef(num, 0))
            if (type == "ObjStm" && obj is PdfStream) {
                val n = (d["N"] as? PdfNum)?.value?.toInt() ?: continue
                val first = (d["First"] as? PdfNum)?.value?.toInt() ?: continue
                val bytes = try { decodedBytes(obj) } catch (ex: Exception) { continue }
                val head = PdfLexer(bytes, 0, minOf(first, bytes.size))
                for (k in 0 until n) {
                    val on = (head.next() as? PdfNum)?.value?.toInt() ?: break
                    head.next()
                    if (xref[on] == null) xref[on] = Entry.InStream(num, k)
                }
            }
        }
        val t = PdfLexer.lastIndexOf(data, "trailer".toByteArray())
        if (t >= 0) {
            (PdfLexer(data, t + 7).next() as? PdfDict)?.let { merged = merged + it.map.filterKeys { k -> k !in merged || k == "Root" } }
        }
        trailer = PdfDict(merged - "Prev")
    }

    private object LexerUtil {
        fun find(data: ByteArray, s: String, from: Int, end: Int) = PdfLexer.indexOf(data, s.toByteArray(), from, end)
    }

    companion object {
        private val ENDSTREAM = "endstream".toByteArray()
    }
}
