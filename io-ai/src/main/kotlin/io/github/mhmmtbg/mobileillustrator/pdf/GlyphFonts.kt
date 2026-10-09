package io.github.mhmmtbg.mobileillustrator.pdf

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import kotlin.math.abs

/** Gömülü fontlardan harf biçimi okur. Dış hatlar 1 em = 1 birim ölçeğinde, y yukarı doğrudur. */
internal interface GlyphSource {
    fun outline(gid: Int): List<SubPath>?
}

internal class OutlineBuilder(private val scale: Double) {
    private val done = ArrayList<SubPath>()
    private var cur: ArrayList<Anchor>? = null
    var x = 0.0
    var y = 0.0

    private fun pt(px: Double, py: Double) = Vec2(px * scale, py * scale)

    fun moveTo(px: Double, py: Double) {
        close()
        x = px; y = py
        cur = arrayListOf(Anchor(pt(px, py)))
    }

    private fun open(): ArrayList<Anchor> = cur ?: arrayListOf(Anchor(pt(x, y))).also { cur = it }

    fun lineTo(px: Double, py: Double) {
        open() += Anchor(pt(px, py))
        x = px; y = py
    }

    fun curveTo(x1: Double, y1: Double, x2: Double, y2: Double, px: Double, py: Double) {
        val c = open()
        val prev = c[c.size - 1]
        val h1 = pt(x1, y1)
        val h2 = pt(x2, y2)
        val end = pt(px, py)
        c[c.size - 1] = prev.copy(handleOut = h1.takeIf { it != prev.point })
        c += Anchor(end, handleIn = h2.takeIf { it != end })
        x = px; y = py
    }

    fun quadTo(qx: Double, qy: Double, px: Double, py: Double) {
        curveTo(x + 2.0 / 3 * (qx - x), y + 2.0 / 3 * (qy - y), px + 2.0 / 3 * (qx - px), py + 2.0 / 3 * (qy - py), px, py)
    }

    /** Harf dış hatları her zaman kapalıdır. */
    fun close() {
        val c = cur ?: return
        cur = null
        if (c.size > 2) {
            val first = c[0]
            val last = c[c.size - 1]
            if (abs(first.point.x - last.point.x) < 1e-9 && abs(first.point.y - last.point.y) < 1e-9) {
                c[0] = first.copy(handleIn = last.handleIn)
                c.removeAt(c.size - 1)
            }
        }
        if (c.size >= 2) done += SubPath(c, closed = true)
    }

    fun finish(): List<SubPath> {
        close()
        return done
    }
}

private class Reader(val d: ByteArray) {
    fun u8(p: Int): Int = if (p in d.indices) d[p].toInt() and 0xFF else 0
    fun u16(p: Int): Int = (u8(p) shl 8) or u8(p + 1)
    fun s16(p: Int): Int = u16(p).toShort().toInt()
    fun u32(p: Int): Long = (u16(p).toLong() shl 16) or u16(p + 2).toLong()
    fun tag(p: Int): String = if (p + 4 <= d.size) String(d, p, 4, Charsets.ISO_8859_1) else ""
}

/** TrueType (glyf) fontları. */
internal class TrueTypeFont private constructor(private val r: Reader, private val tables: Map<String, Int>) : GlyphSource {
    private val unitsPerEm: Int
    private val longLoca: Boolean
    private val numGlyphs: Int
    private val loca = tables["loca"] ?: -1
    private val glyf = tables["glyf"] ?: -1
    private val cmaps = HashMap<Int, Int>() // (platform << 16 | encoding) -> alt tablo konumu

    init {
        val head = tables["head"] ?: throw PdfException("head tablosu yok")
        unitsPerEm = r.u16(head + 18).takeIf { it > 0 } ?: 1000
        longLoca = r.s16(head + 50) != 0
        numGlyphs = tables["maxp"]?.let { r.u16(it + 4) } ?: 0
        tables["cmap"]?.let { cm ->
            val n = r.u16(cm + 2)
            for (i in 0 until n) {
                val e = cm + 4 + i * 8
                cmaps[(r.u16(e) shl 16) or r.u16(e + 2)] = cm + r.u32(e + 4).toInt()
            }
        }
    }

    val hasGlyphs: Boolean get() = loca >= 0 && glyf >= 0

    private fun lookup(sub: Int, code: Int): Int {
        when (r.u16(sub)) {
            0 -> return if (code in 0..255) r.u8(sub + 6 + code) else 0
            6 -> {
                val first = r.u16(sub + 6)
                val count = r.u16(sub + 8)
                return if (code - first in 0 until count) r.u16(sub + 10 + (code - first) * 2) else 0
            }
            4 -> {
                val segX2 = r.u16(sub + 6)
                val ends = sub + 14
                val starts = ends + segX2 + 2
                val deltas = starts + segX2
                val offsets = deltas + segX2
                var i = 0
                while (i < segX2) {
                    val end = r.u16(ends + i)
                    if (code <= end) {
                        val start = r.u16(starts + i)
                        if (code < start) return 0
                        val ro = r.u16(offsets + i)
                        if (ro == 0) return (code + r.s16(deltas + i)) and 0xFFFF
                        val g = r.u16(offsets + i + ro + (code - start) * 2)
                        return if (g == 0) 0 else (g + r.s16(deltas + i)) and 0xFFFF
                    }
                    i += 2
                }
                return 0
            }
            12 -> {
                val n = r.u32(sub + 12).toInt()
                for (i in 0 until minOf(n, 100000)) {
                    val g = sub + 16 + i * 12
                    val s = r.u32(g)
                    val e = r.u32(g + 4)
                    if (code >= s && code <= e) return (r.u32(g + 8) + (code - s)).toInt()
                }
                return 0
            }
        }
        return 0
    }

    /** Basit (tek baytlı) fontlarda kod -> glif. Önce Unicode tablosu, sonra fontun kendi tabloları denenir. */
    fun gidForCode(code: Int, unicode: Int?): Int {
        if (unicode != null) {
            cmaps[(3 shl 16) or 1]?.let { t -> lookup(t, unicode).takeIf { it != 0 }?.let { return it } }
            cmaps[(0 shl 16) or 3]?.let { t -> lookup(t, unicode).takeIf { it != 0 }?.let { return it } }
        }
        cmaps[(1 shl 16) or 0]?.let { t -> lookup(t, code).takeIf { it != 0 }?.let { return it } }
        cmaps[(3 shl 16) or 0]?.let { t ->
            lookup(t, 0xF000 + code).takeIf { it != 0 }?.let { return it }
            lookup(t, code).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    private fun glyphOffset(gid: Int): Pair<Int, Int>? {
        if (gid < 0 || (numGlyphs > 0 && gid >= numGlyphs) || loca < 0 || glyf < 0) return null
        val a: Long
        val b: Long
        if (longLoca) { a = r.u32(loca + gid * 4); b = r.u32(loca + gid * 4 + 4) } else { a = r.u16(loca + gid * 2) * 2L; b = r.u16(loca + gid * 2 + 2) * 2L }
        if (b <= a) return null // boş glif (ör. boşluk)
        return (glyf + a).toInt() to (b - a).toInt()
    }

    override fun outline(gid: Int): List<SubPath>? {
        if (!hasGlyphs) return null
        val b = OutlineBuilder(1.0 / unitsPerEm)
        if (!emit(gid, b, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0)) return emptyList()
        return b.finish()
    }

    private fun emit(gid: Int, out: OutlineBuilder, a: Double, b: Double, c: Double, d: Double, e: Double, f: Double, depth: Int): Boolean {
        val (off, _) = glyphOffset(gid) ?: return false
        val contours = r.s16(off)
        var p = off + 10
        if (contours >= 0) {
            val ends = IntArray(contours) { r.u16(p + it * 2) }
            p += contours * 2
            val n = if (contours == 0) 0 else ends[contours - 1] + 1
            if (n > 20000) return false
            p += 2 + r.u16(p) // yönergeler
            val flags = IntArray(n)
            var i = 0
            while (i < n) {
                val fl = r.u8(p++)
                flags[i++] = fl
                if (fl and 8 != 0) {
                    var rep = r.u8(p++)
                    while (rep-- > 0 && i < n) flags[i++] = fl
                }
            }
            val xs = DoubleArray(n)
            val ys = DoubleArray(n)
            var v = 0
            for (k in 0 until n) {
                val fl = flags[k]
                if (fl and 2 != 0) { val dx = r.u8(p++); v += if (fl and 16 != 0) dx else -dx } else if (fl and 16 == 0) { v += r.s16(p); p += 2 }
                xs[k] = v.toDouble()
            }
            v = 0
            for (k in 0 until n) {
                val fl = flags[k]
                if (fl and 4 != 0) { val dy = r.u8(p++); v += if (fl and 32 != 0) dy else -dy } else if (fl and 32 == 0) { v += r.s16(p); p += 2 }
                ys[k] = v.toDouble()
            }
            fun tx(k: Int) = a * xs[k] + c * ys[k] + e
            fun ty(k: Int) = b * xs[k] + d * ys[k] + f
            var start = 0
            for (ci in 0 until contours) {
                val end = ends[ci]
                val count = end - start + 1
                if (count >= 2) {
                    fun on(k: Int) = flags[start + k % count] and 1 != 0
                    fun px(k: Int) = tx(start + k % count)
                    fun py(k: Int) = ty(start + k % count)
                    // Başlangıç: eğri üzerindeki ilk nokta; yoksa ilk iki kontrol noktasının ortası.
                    var first = 0
                    while (first < count && !on(first)) first++
                    val sx: Double
                    val sy: Double
                    val from: Int
                    if (first < count) { sx = px(first); sy = py(first); from = first + 1 } else {
                        sx = (px(0) + px(count - 1)) / 2; sy = (py(0) + py(count - 1)) / 2; from = 0
                    }
                    out.moveTo(sx, sy)
                    var offX = 0.0
                    var offY = 0.0
                    var pending = false
                    val steps = if (first < count) count - 1 else count
                    for (s in 0 until steps) {
                        val k = from + s
                        if (on(k)) {
                            if (pending) out.quadTo(offX, offY, px(k), py(k)) else out.lineTo(px(k), py(k))
                            pending = false
                        } else {
                            if (pending) out.quadTo(offX, offY, (offX + px(k)) / 2, (offY + py(k)) / 2)
                            offX = px(k); offY = py(k); pending = true
                        }
                    }
                    if (pending) out.quadTo(offX, offY, sx, sy)
                    out.close()
                }
                start = end + 1
            }
            return true
        }
        // Bileşik glif
        if (depth > 6) return false
        while (true) {
            val fl = r.u16(p)
            val child = r.u16(p + 2)
            p += 4
            var dx = 0.0
            var dy = 0.0
            if (fl and 1 != 0) { if (fl and 2 != 0) { dx = r.s16(p).toDouble(); dy = r.s16(p + 2).toDouble() }; p += 4 } else {
                if (fl and 2 != 0) { dx = r.u8(p).toByte().toDouble(); dy = r.u8(p + 1).toByte().toDouble() }; p += 2
            }
            var ma = 1.0; var mb = 0.0; var mc = 0.0; var md = 1.0
            fun f2(at: Int) = r.s16(at) / 16384.0
            when {
                fl and 8 != 0 -> { ma = f2(p); md = ma; p += 2 }
                fl and 0x40 != 0 -> { ma = f2(p); md = f2(p + 2); p += 4 }
                fl and 0x80 != 0 -> { ma = f2(p); mb = f2(p + 2); mc = f2(p + 4); md = f2(p + 6); p += 8 }
            }
            // çocuk -> bu glif -> çağıran
            emit(
                child, out,
                a * ma + c * mb, b * ma + d * mb, a * mc + c * md, b * mc + d * md,
                a * dx + c * dy + e, b * dx + d * dy + f, depth + 1,
            )
            if (fl and 0x20 == 0) break
        }
        return true
    }

    companion object {
        /** sfnt kabındaki tabloları okur; kap değilse `null`. */
        fun tablesOf(data: ByteArray): Map<String, Int>? {
            val r = Reader(data)
            val v = r.tag(0)
            if (v != "\u0000\u0001\u0000\u0000" && v != "true" && v != "OTTO" && v != "typ1") return null
            val n = r.u16(4)
            val out = HashMap<String, Int>()
            for (i in 0 until n) {
                val e = 12 + i * 16
                out[r.tag(e)] = r.u32(e + 8).toInt()
            }
            return out
        }

        fun parse(data: ByteArray): TrueTypeFont? {
            val t = tablesOf(data) ?: return null
            return try { TrueTypeFont(Reader(data), t).takeIf { it.hasGlyphs } } catch (e: Exception) { null }
        }
    }
}

/** CFF (Type 1C, CIDFontType0C, OpenType/CFF) fontları. */
internal class CffFont private constructor(private val d: ByteArray, private val base: Int) : GlyphSource {
    private val r = Reader(d)

    private class Index(val count: Int, val offSize: Int, val offsets: Int, val data: Int, val end: Int)

    private fun index(p: Int): Index {
        val count = r.u16(p)
        if (count == 0) return Index(0, 1, p + 2, p + 2, p + 2)
        val offSize = r.u8(p + 2)
        val offsets = p + 3
        val data = offsets + (count + 1) * offSize - 1
        return Index(count, offSize, offsets, data, data + off(offsets + count * offSize, offSize))
    }

    private fun off(p: Int, size: Int): Int {
        var v = 0
        for (i in 0 until size) v = (v shl 8) or r.u8(p + i)
        return v
    }

    private fun item(ix: Index, i: Int): Pair<Int, Int>? {
        if (i < 0 || i >= ix.count) return null
        val a = ix.data + off(ix.offsets + i * ix.offSize, ix.offSize)
        val b = ix.data + off(ix.offsets + (i + 1) * ix.offSize, ix.offSize)
        if (b < a || b > d.size) return null
        return a to b
    }

    private fun dict(from: Int, to: Int): Map<Int, DoubleArray> {
        val out = HashMap<Int, DoubleArray>()
        val st = ArrayList<Double>()
        var p = from
        while (p < to) {
            val b0 = r.u8(p)
            when {
                b0 <= 21 -> {
                    var op = b0
                    p++
                    if (b0 == 12) { op = 1200 + r.u8(p); p++ }
                    out[op] = st.toDoubleArray()
                    st.clear()
                }
                b0 == 28 -> { st += r.s16(p + 1).toDouble(); p += 3 }
                b0 == 29 -> { st += r.u32(p + 1).toInt().toDouble(); p += 5 }
                b0 == 30 -> {
                    // Paketlenmiş ondalık sayı
                    val sb = StringBuilder()
                    p++
                    var done = false
                    while (!done && p < to) {
                        val b = r.u8(p++)
                        for (nib in intArrayOf(b shr 4, b and 15)) {
                            when (nib) {
                                in 0..9 -> sb.append(nib)
                                10 -> sb.append('.')
                                11 -> sb.append('E')
                                12 -> sb.append("E-")
                                14 -> sb.append('-')
                                15 -> done = true
                            }
                            if (done) break
                        }
                    }
                    st += sb.toString().toDoubleOrNull() ?: 0.0
                }
                b0 in 32..246 -> { st += (b0 - 139).toDouble(); p++ }
                b0 in 247..250 -> { st += ((b0 - 247) * 256 + r.u8(p + 1) + 108).toDouble(); p += 2 }
                b0 in 251..254 -> { st += (-(b0 - 251) * 256 - r.u8(p + 1) - 108).toDouble(); p += 2 }
                else -> p++
            }
        }
        return out
    }

    private val strings: Index
    private val globalSubrs: Index
    private val charStrings: Index
    private val isCid: Boolean
    private val charset = HashMap<Int, Int>() // SID ya da CID -> gid
    private val encoding = HashMap<Int, Int>() // kod -> gid
    private val topScale: Double?
    private val localSubrs = ArrayList<Index?>() // FD başına
    private val fdScale = ArrayList<Double?>()
    private var fdSelect: IntArray? = null
    private val nameToGid = HashMap<String, Int>()

    init {
        val hdr = r.u8(base + 2)
        val nameIx = index(base + hdr)
        val topIx = index(nameIx.end)
        strings = index(topIx.end)
        globalSubrs = index(strings.end)
        val (ta, tb) = item(topIx, 0) ?: throw PdfException("CFF üst sözlüğü yok")
        val top = dict(ta, tb)
        charStrings = index(base + (top[17]?.firstOrNull()?.toInt() ?: throw PdfException("CharStrings yok")))
        isCid = top.containsKey(1230)
        topScale = top[1207]?.firstOrNull()
        val n = charStrings.count

        // charset: gid -> SID/CID
        val csOff = top[15]?.firstOrNull()?.toInt() ?: 0
        charset[0] = 0
        if (csOff > 2) {
            var p = base + csOff
            val fmt = r.u8(p++)
            var gid = 1
            when (fmt) {
                0 -> while (gid < n) { charset[r.u16(p)] = gid++; p += 2 }
                1, 2 -> while (gid < n) {
                    val first = r.u16(p)
                    val left = if (fmt == 1) r.u8(p + 2) else r.u16(p + 2)
                    p += if (fmt == 1) 3 else 4
                    for (k in 0..left) { if (gid >= n) break; charset[first + k] = gid++ }
                }
            }
        } else {
            for (g in 1 until n) charset[g] = g // ISOAdobe: SID = gid
        }

        if (!isCid) {
            for ((sid, gid) in charset) stringOf(sid)?.let { nameToGid.putIfAbsent(it, gid) }
            val encOff = top[16]?.firstOrNull()?.toInt() ?: 0
            if (encOff > 1) {
                var p = base + encOff
                val fmt = r.u8(p++)
                when (fmt and 0x7F) {
                    0 -> { val cnt = r.u8(p++); for (i in 0 until cnt) encoding[r.u8(p++)] = i + 1 }
                    1 -> {
                        val ranges = r.u8(p++)
                        var gid = 1
                        for (i in 0 until ranges) {
                            val first = r.u8(p++)
                            val left = r.u8(p++)
                            for (k in 0..left) encoding[first + k] = gid++
                        }
                    }
                }
            }
            val priv = top[18]
            localSubrs += priv?.takeIf { it.size >= 2 }?.let { privateSubrs(it[0].toInt(), it[1].toInt()) }
            fdScale += null
        } else {
            val fdArray = index(base + (top[1236]?.firstOrNull()?.toInt() ?: throw PdfException("FDArray yok")))
            for (i in 0 until fdArray.count) {
                val (fa, fb) = item(fdArray, i) ?: continue
                val fd = dict(fa, fb)
                localSubrs += fd[18]?.takeIf { it.size >= 2 }?.let { privateSubrs(it[0].toInt(), it[1].toInt()) }
                fdScale += fd[1207]?.firstOrNull()
            }
            top[1237]?.firstOrNull()?.toInt()?.let { fo ->
                var p = base + fo
                val sel = IntArray(n)
                when (r.u8(p++)) {
                    0 -> for (g in 0 until n) sel[g] = r.u8(p++)
                    3 -> {
                        val ranges = r.u16(p); p += 2
                        var first = r.u16(p); p += 2
                        for (i in 0 until ranges) {
                            val fd = r.u8(p); p++
                            val next = r.u16(p); p += 2
                            for (g in first until minOf(next, n)) sel[g] = fd
                            first = next
                        }
                    }
                }
                fdSelect = sel
            }
        }
    }

    private fun privateSubrs(size: Int, offset: Int): Index? {
        val start = base + offset
        val priv = dict(start, start + size)
        val so = priv[19]?.firstOrNull()?.toInt() ?: return null
        return index(start + so)
    }

    private fun stringOf(sid: Int): String? {
        if (sid < StandardStrings.size) return StandardStrings[sid]
        val (a, b) = item(strings, sid - 391) ?: return null
        return String(d, a, b - a, Charsets.ISO_8859_1)
    }

    fun gidForName(name: String): Int? = nameToGid[name]
    fun gidForCode(code: Int): Int? = encoding[code]

    /** CID anahtarlı fontlarda CID -> glif; değilse CID doğrudan glif numarasıdır. */
    fun gidForCid(cid: Int): Int = if (isCid) charset[cid] ?: 0 else cid

    override fun outline(gid: Int): List<SubPath>? {
        val (a, b) = item(charStrings, gid) ?: return null
        val fd = fdSelect?.getOrNull(gid) ?: 0
        val fdS = fdScale.getOrNull(fd)
        val scale = (topScale ?: if (fdS != null) 1.0 else 0.001) * (fdS ?: 1.0)
        val out = OutlineBuilder(scale)
        val run = Type2(out, localSubrs.getOrNull(fd))
        try {
            run.exec(a, b, 0)
        } catch (e: IndexOutOfBoundsException) {
            // Bozuk yönerge dizisi: o ana kadar çizilenle yetin.
        }
        return out.finish()
    }

    private fun bias(ix: Index?) = when {
        ix == null -> 0
        ix.count < 1240 -> 107
        ix.count < 33900 -> 1131
        else -> 32768
    }

    /** Type 2 charstring yorumlayıcısı. */
    private inner class Type2(val out: OutlineBuilder, val local: Index?) {
        val st = DoubleArray(96)
        var sp = 0
        var stems = 0
        var widthSeen = false
        var done = false
        var open = false

        fun clear() { sp = 0 }

        /** Yığını temizleyen ilk işleçte, beklenenden bir fazla sayı varsa baştaki genişliktir. */
        fun dropWidth(expected: Int, parity: Boolean = false): Int {
            if (widthSeen) return 0
            widthSeen = true
            val extra = if (parity) sp % 2 == 1 else sp > expected
            return if (extra) 1 else 0
        }

        fun moveTo(dx: Double, dy: Double) {
            out.moveTo(out.x + dx, out.y + dy)
            open = true
        }

        fun line(dx: Double, dy: Double) = out.lineTo(out.x + dx, out.y + dy)

        fun curve(d1x: Double, d1y: Double, d2x: Double, d2y: Double, d3x: Double, d3y: Double) {
            val x1 = out.x + d1x; val y1 = out.y + d1y
            val x2 = x1 + d2x; val y2 = y1 + d2y
            out.curveTo(x1, y1, x2, y2, x2 + d3x, y2 + d3y)
        }

        fun exec(from: Int, to: Int, depth: Int) {
            if (depth > 12) return
            var p = from
            while (p < to && !done) {
                val b0 = r.u8(p++)
                when {
                    b0 == 28 -> { st[sp++] = r.s16(p).toDouble(); p += 2 }
                    b0 in 32..246 -> st[sp++] = (b0 - 139).toDouble()
                    b0 in 247..250 -> { st[sp++] = ((b0 - 247) * 256 + r.u8(p) + 108).toDouble(); p++ }
                    b0 in 251..254 -> { st[sp++] = (-(b0 - 251) * 256 - r.u8(p) - 108).toDouble(); p++ }
                    b0 == 255 -> { st[sp++] = r.u32(p).toInt() / 65536.0; p += 4 }
                    else -> when (b0) {
                        1, 3, 18, 23 -> { val w = dropWidth(0, parity = true); stems += (sp - w) / 2; clear() }
                        19, 20 -> {
                            val w = dropWidth(0, parity = true)
                            stems += (sp - w) / 2
                            clear()
                            p += (stems + 7) / 8
                        }
                        21 -> { val w = dropWidth(2); moveTo(st[w], st[w + 1]); clear() }
                        22 -> { val w = dropWidth(1); moveTo(st[w], 0.0); clear() }
                        4 -> { val w = dropWidth(1); moveTo(0.0, st[w]); clear() }
                        5 -> { var i = 0; while (i + 1 < sp) { line(st[i], st[i + 1]); i += 2 }; clear() }
                        6, 7 -> {
                            var horizontal = b0 == 6
                            for (i in 0 until sp) { if (horizontal) line(st[i], 0.0) else line(0.0, st[i]); horizontal = !horizontal }
                            clear()
                        }
                        8 -> { var i = 0; while (i + 5 < sp) { curve(st[i], st[i + 1], st[i + 2], st[i + 3], st[i + 4], st[i + 5]); i += 6 }; clear() }
                        24 -> {
                            var i = 0
                            while (i + 5 < sp - 2) { curve(st[i], st[i + 1], st[i + 2], st[i + 3], st[i + 4], st[i + 5]); i += 6 }
                            if (i + 1 < sp) line(st[i], st[i + 1])
                            clear()
                        }
                        25 -> {
                            var i = 0
                            while (i + 1 < sp - 6) { line(st[i], st[i + 1]); i += 2 }
                            if (i + 5 < sp) curve(st[i], st[i + 1], st[i + 2], st[i + 3], st[i + 4], st[i + 5])
                            clear()
                        }
                        26 -> {
                            var i = 0
                            var dx1 = 0.0
                            if (sp % 4 == 1) dx1 = st[i++]
                            while (i + 3 < sp) { curve(dx1, st[i], st[i + 1], st[i + 2], 0.0, st[i + 3]); dx1 = 0.0; i += 4 }
                            clear()
                        }
                        27 -> {
                            var i = 0
                            var dy1 = 0.0
                            if (sp % 4 == 1) dy1 = st[i++]
                            while (i + 3 < sp) { curve(st[i], dy1, st[i + 1], st[i + 2], st[i + 3], 0.0); dy1 = 0.0; i += 4 }
                            clear()
                        }
                        30, 31 -> {
                            var horizontal = b0 == 31
                            var i = 0
                            while (i + 3 < sp) {
                                val last = sp - i == 5
                                if (horizontal) {
                                    curve(st[i], 0.0, st[i + 1], st[i + 2], if (last) st[i + 4] else 0.0, st[i + 3])
                                } else {
                                    curve(0.0, st[i], st[i + 1], st[i + 2], st[i + 3], if (last) st[i + 4] else 0.0)
                                }
                                horizontal = !horizontal
                                i += 4
                            }
                            clear()
                        }
                        10, 29 -> {
                            val ix = if (b0 == 10) local else globalSubrs
                            if (sp > 0 && ix != null) {
                                val n = st[--sp].toInt() + bias(ix)
                                item(ix, n)?.let { (a, b) -> exec(a, b, depth + 1) }
                            }
                        }
                        11 -> return
                        14 -> { dropWidth(0); done = true; clear() }
                        12 -> {
                            val b1 = r.u8(p++)
                            when (b1) {
                                35 -> if (sp >= 12) { curve(st[0], st[1], st[2], st[3], st[4], st[5]); curve(st[6], st[7], st[8], st[9], st[10], st[11]) }
                                34 -> if (sp >= 7) { curve(st[0], 0.0, st[1], st[2], st[3], 0.0); curve(st[4], 0.0, st[5], -st[2], st[6], 0.0) }
                                36 -> if (sp >= 9) {
                                    curve(st[0], st[1], st[2], st[3], st[4], 0.0)
                                    curve(st[5], 0.0, st[6], st[7], st[8], -(st[1] + st[3] + st[7]))
                                }
                                37 -> if (sp >= 11) {
                                    val dx = st[0] + st[2] + st[4] + st[6] + st[8]
                                    val dy = st[1] + st[3] + st[5] + st[7] + st[9]
                                    curve(st[0], st[1], st[2], st[3], st[4], st[5])
                                    if (abs(dx) > abs(dy)) curve(st[6], st[7], st[8], st[9], st[10], -dy) else curve(st[6], st[7], st[8], st[9], -dx, st[10])
                                }
                            }
                            clear()
                        }
                        else -> clear()
                    }
                }
                if (sp >= st.size - 2) sp = 0
            }
        }
    }

    companion object {
        fun parse(data: ByteArray): CffFont? {
            var base = 0
            // OpenType kabı içindeki CFF tablosu
            TrueTypeFont.tablesOf(data)?.let { t -> base = t["CFF "] ?: return null }
            return try { CffFont(data, base) } catch (e: Exception) { null }
        }

        /** CFF standart dizgilerinin Latin kısmı (SID 0-228); gerisi uzman karakter kümesidir. */
        private val StandardStrings: List<String?> by lazy {
            val names = (
                ".notdef space exclam quotedbl numbersign dollar percent ampersand quoteright parenleft parenright asterisk plus comma " +
                    "hyphen period slash zero one two three four five six seven eight nine colon semicolon less equal greater question at " +
                    "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z bracketleft backslash bracketright asciicircum underscore quoteleft " +
                    "a b c d e f g h i j k l m n o p q r s t u v w x y z braceleft bar braceright asciitilde exclamdown cent sterling fraction " +
                    "yen florin section currency quotesingle quotedblleft guillemotleft guilsinglleft guilsinglright fi fl endash dagger " +
                    "daggerdbl periodcentered paragraph bullet quotesinglbase quotedblbase quotedblright guillemotright ellipsis perthousand " +
                    "questiondown grave acute circumflex tilde macron breve dotaccent dieresis ring cedilla hungarumlaut ogonek caron emdash " +
                    "AE ordfeminine Lslash Oslash OE ordmasculine ae dotlessi lslash oslash oe germandbls onesuperior logicalnot mu trademark " +
                    "Eth onehalf plusminus Thorn onequarter divide brokenbar degree thorn threequarters twosuperior registered minus eth " +
                    "multiply threesuperior copyright Aacute Acircumflex Adieresis Agrave Aring Atilde Ccedilla Eacute Ecircumflex Edieresis " +
                    "Egrave Iacute Icircumflex Idieresis Igrave Ntilde Oacute Ocircumflex Odieresis Ograve Otilde Scaron Uacute Ucircumflex " +
                    "Udieresis Ugrave Yacute Ydieresis Zcaron aacute acircumflex adieresis agrave aring atilde ccedilla eacute ecircumflex " +
                    "edieresis egrave iacute icircumflex idieresis igrave ntilde oacute ocircumflex odieresis ograve otilde scaron uacute " +
                    "ucircumflex udieresis ugrave yacute ydieresis zcaron"
                ).split(' ')
            val out = ArrayList<String?>(391)
            out.addAll(names)
            while (out.size < 391) out += null
            out
        }
    }
}
