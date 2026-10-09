package io.github.mhmmtbg.mobileillustrator.pdf

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.truncate

/** PDF işlevleri (tür 0, 2, 3, 4). Gradyan durakları ve spot renk dönüşümleri için. */
abstract class PdfFunction(val domain: DoubleArray, val range: DoubleArray?) {
    abstract val outputs: Int
    protected abstract fun compute(x: DoubleArray): DoubleArray

    fun eval(input: DoubleArray): DoubleArray {
        val x = DoubleArray(input.size) { i ->
            if (2 * i + 1 < domain.size) input[i].coerceIn(domain[2 * i], maxOf(domain[2 * i], domain[2 * i + 1])) else input[i]
        }
        val y = compute(x)
        val r = range ?: return y
        return DoubleArray(y.size) { i ->
            if (2 * i + 1 < r.size) y[i].coerceIn(r[2 * i], maxOf(r[2 * i], r[2 * i + 1])) else y[i]
        }
    }

    companion object {
        fun parse(file: PdfFile, obj: PdfObj?, depth: Int = 0): PdfFunction? {
            if (depth > 12) return null
            val r = file.resolve(obj)
            if (r is PdfArr) {
                if (r.items.size > 64) return null
                val parts = r.items.map { parse(file, it, depth + 1) ?: return null }
                if (parts.isEmpty()) return null
                return Combined(parts)
            }
            val d = file.dict(r) ?: return null
            val domain = file.numbers(d["Domain"]) ?: doubleArrayOf(0.0, 1.0)
            val range = file.numbers(d["Range"])
            return when (file.int(d["FunctionType"])) {
                2 -> {
                    val c0 = file.numbers(d["C0"]) ?: doubleArrayOf(0.0)
                    val c1 = file.numbers(d["C1"]) ?: doubleArrayOf(1.0)
                    Exponential(domain, range, c0, c1, file.num(d["N"]) ?: 1.0)
                }
                3 -> {
                    val list = file.array(d["Functions"])?.takeIf { it.size <= 4096 } ?: return null
                    val fns = list.map { parse(file, it, depth + 1) ?: return null }
                    if (fns.isEmpty()) return null
                    Stitching(domain, range, fns, file.numbers(d["Bounds"]) ?: DoubleArray(0), file.numbers(d["Encode"]) ?: DoubleArray(0))
                }
                0 -> {
                    val s = r as? PdfStream ?: return null
                    val size = file.numbers(d["Size"])?.map { it.toInt() } ?: return null
                    val bps = file.int(d["BitsPerSample"]) ?: 8
                    val rg = range ?: return null
                    Sampled(domain, rg, size, bps, file.numbers(d["Encode"]), file.numbers(d["Decode"]), file.decodedBytes(s))
                }
                4 -> {
                    val s = r as? PdfStream ?: return null
                    val rg = range ?: return null
                    PostScript(domain, rg, file.decodedBytes(s))
                }
                else -> null
            }
        }
    }

    class Combined(private val parts: List<PdfFunction>) : PdfFunction(parts[0].domain, null) {
        override val outputs: Int get() = parts.sumOf { it.outputs }
        override fun compute(x: DoubleArray): DoubleArray {
            val out = ArrayList<Double>()
            for (p in parts) for (v in p.eval(x)) out += v
            return out.toDoubleArray()
        }
    }

    class Exponential(domain: DoubleArray, range: DoubleArray?, val c0: DoubleArray, val c1: DoubleArray, val n: Double) :
        PdfFunction(domain, range) {
        override val outputs: Int get() = minOf(c0.size, c1.size)
        override fun compute(x: DoubleArray): DoubleArray {
            val t = if (n == 1.0) x[0] else x[0].coerceAtLeast(0.0).pow(n)
            return DoubleArray(outputs) { c0[it] + t * (c1[it] - c0[it]) }
        }
    }

    class Stitching(
        domain: DoubleArray,
        range: DoubleArray?,
        val functions: List<PdfFunction>,
        val bounds: DoubleArray,
        private val encode: DoubleArray,
    ) : PdfFunction(domain, range) {
        override val outputs: Int get() = functions[0].outputs

        /** k. alt işlevin tanım aralığı. */
        fun span(k: Int): Pair<Double, Double> {
            val lo = if (k == 0) domain[0] else bounds.getOrElse(k - 1) { domain[1] }
            val hi = if (k >= bounds.size) domain[1] else bounds[k]
            return lo to hi
        }

        override fun compute(x: DoubleArray): DoubleArray {
            val v = x[0]
            var k = 0
            while (k < bounds.size && k < functions.size - 1 && v >= bounds[k]) k++
            val (lo, hi) = span(k)
            val e0 = encode.getOrElse(2 * k) { 0.0 }
            val e1 = encode.getOrElse(2 * k + 1) { 1.0 }
            val t = if (hi == lo) e0 else e0 + (v - lo) * (e1 - e0) / (hi - lo)
            return functions[k].eval(doubleArrayOf(t))
        }
    }

    class Sampled(
        domain: DoubleArray,
        range: DoubleArray,
        private val size: List<Int>,
        private val bps: Int,
        encode: DoubleArray?,
        decode: DoubleArray?,
        private val samples: ByteArray,
    ) : PdfFunction(domain, range) {
        private val m = size.size
        override val outputs: Int = range.size / 2
        private val enc = encode ?: DoubleArray(2 * m) { if (it % 2 == 0) 0.0 else (size[it / 2] - 1).toDouble() }
        private val dec = decode ?: range
        private val maxSample = (2.0.pow(bps) - 1)

        val sampleCount: Int get() = size.firstOrNull() ?: 2

        private fun sample(index: IntArray, j: Int): Double {
            var flat = 0L
            var stride = 1L
            for (i in 0 until m) {
                flat += index[i].coerceIn(0, size[i] - 1) * stride
                stride *= size[i]
            }
            val bit = (flat * outputs + j) * bps
            val raw: Double = when (bps) {
                8 -> ((samples.getOrElse((bit / 8).toInt()) { 0 }).toInt() and 0xFF).toDouble()
                16 -> {
                    val p = (bit / 8).toInt()
                    (((samples.getOrElse(p) { 0 }.toInt() and 0xFF) shl 8) or (samples.getOrElse(p + 1) { 0 }.toInt() and 0xFF)).toDouble()
                }
                else -> {
                    var v = 0L
                    for (b in 0 until bps) {
                        val pos = bit + b
                        val byte = samples.getOrElse((pos / 8).toInt()) { 0 }.toInt() and 0xFF
                        v = (v shl 1) or ((byte shr (7 - (pos % 8).toInt())) and 1).toLong()
                    }
                    v.toDouble()
                }
            }
            val d0 = dec.getOrElse(2 * j) { 0.0 }
            val d1 = dec.getOrElse(2 * j + 1) { 1.0 }
            return d0 + raw / maxSample * (d1 - d0)
        }

        override fun compute(x: DoubleArray): DoubleArray {
            val e = DoubleArray(m) { i ->
                val d0 = domain[2 * i]
                val d1 = domain[2 * i + 1]
                val t = if (d1 == d0) 0.0 else (x.getOrElse(i) { 0.0 } - d0) / (d1 - d0)
                (enc[2 * i] + t * (enc[2 * i + 1] - enc[2 * i])).coerceIn(0.0, (size[i] - 1).toDouble())
            }
            if (m == 1) {
                val i0 = floor(e[0]).toInt()
                val f = e[0] - i0
                return DoubleArray(outputs) { j ->
                    val a = sample(intArrayOf(i0), j)
                    if (f == 0.0) a else a + f * (sample(intArrayOf(i0 + 1), j) - a)
                }
            }
            // Çok boyutlu girdide en yakın örnek yeterli.
            val idx = IntArray(m) { e[it].roundToInt() }
            return DoubleArray(outputs) { sample(idx, it) }
        }
    }

    /** PostScript hesap işlevi (tür 4). */
    class PostScript(domain: DoubleArray, range: DoubleArray, code: ByteArray) : PdfFunction(domain, range) {
        override val outputs: Int = range.size / 2
        private val program: List<Any>

        init {
            val tokens = String(code, Charsets.ISO_8859_1).replace("{", " { ").replace("}", " } ").trim().split(Regex("\\s+"))
            var p = 0
            var nesting = 0
            fun block(): List<Any> {
                val out = ArrayList<Any>()
                if (++nesting > 64) throw PdfException("İşlev çok derin iç içe")
                while (p < tokens.size) {
                    val t = tokens[p++]
                    when {
                        t == "{" -> { out.add(block()); nesting-- }
                        t == "}" -> return out
                        t.isEmpty() -> {}
                        else -> out += (t.toDoubleOrNull() ?: t)
                    }
                }
                return out
            }
            val top = block()
            @Suppress("UNCHECKED_CAST")
            program = (top.firstOrNull() as? List<Any>) ?: top
        }

        override fun compute(x: DoubleArray): DoubleArray {
            val st = ArrayList<Double>()
            for (v in x) st += v
            try {
                run(program, st)
            } catch (e: Exception) {
                // Yığın hatası: eldekiyle devam
            }
            return DoubleArray(outputs) { i -> st.getOrElse(st.size - outputs + i) { 0.0 } }
        }

        private fun run(code: List<Any>, st: ArrayList<Double>) {
            if (st.size > 4096) throw IllegalStateException("yığın taştı")
            fun pop() = st.removeAt(st.size - 1)
            fun b(v: Boolean) = if (v) 1.0 else 0.0
            var i = 0
            while (i < code.size) {
                val t = code[i++]
                if (t is Double) { st += t; continue }
                if (t is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val block1 = t as List<Any>
                    val nxt = code.getOrNull(i)
                    if (nxt is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        val block2 = nxt as List<Any>
                        i++ // ifelse
                        i++
                        if (pop() != 0.0) run(block1, st) else run(block2, st)
                    } else {
                        i++ // if
                        if (pop() != 0.0) run(block1, st)
                    }
                    continue
                }
                when (t as String) {
                    "add" -> { val y = pop(); st += pop() + y }
                    "sub" -> { val y = pop(); st += pop() - y }
                    "mul" -> { val y = pop(); st += pop() * y }
                    "div" -> { val y = pop(); st += pop() / y }
                    "idiv" -> { val y = pop(); st += truncate(pop() / y) }
                    "mod" -> { val y = pop(); st += pop().rem(y) }
                    "neg" -> st += -pop()
                    "abs" -> st += abs(pop())
                    "ceiling" -> st += ceil(pop())
                    "floor" -> st += floor(pop())
                    "round" -> st += floor(pop() + 0.5)
                    "truncate", "cvi" -> st += truncate(pop())
                    "cvr" -> {}
                    "sqrt" -> st += sqrt(pop())
                    "sin" -> st += sin(Math.toRadians(pop()))
                    "cos" -> st += cos(Math.toRadians(pop()))
                    "atan" -> { val den = pop(); val num = pop(); var a = Math.toDegrees(atan2(num, den)); if (a < 0) a += 360.0; st += a }
                    "exp" -> { val e = pop(); st += pop().pow(e) }
                    "ln" -> st += ln(pop())
                    "log" -> st += log10(pop())
                    "eq" -> { val y = pop(); st += b(pop() == y) }
                    "ne" -> { val y = pop(); st += b(pop() != y) }
                    "gt" -> { val y = pop(); st += b(pop() > y) }
                    "ge" -> { val y = pop(); st += b(pop() >= y) }
                    "lt" -> { val y = pop(); st += b(pop() < y) }
                    "le" -> { val y = pop(); st += b(pop() <= y) }
                    "and" -> { val y = pop().toLong(); st += (pop().toLong() and y).toDouble() }
                    "or" -> { val y = pop().toLong(); st += (pop().toLong() or y).toDouble() }
                    "xor" -> { val y = pop().toLong(); st += (pop().toLong() xor y).toDouble() }
                    "not" -> st += b(pop() == 0.0)
                    "bitshift" -> { val s = pop().toInt(); val v = pop().toLong(); st += (if (s >= 0) v shl s else v shr -s).toDouble() }
                    "true" -> st += 1.0
                    "false" -> st += 0.0
                    "pop" -> pop()
                    "exch" -> { val y = pop(); val z = pop(); st += y; st += z }
                    "dup" -> st += st.last()
                    "copy" -> { val n = pop().toInt(); val from = st.size - n; for (k in 0 until n) st += st[from + k] }
                    "index" -> { val n = pop().toInt(); st += st[st.size - 1 - n] }
                    "roll" -> {
                        val j = pop().toInt()
                        val n = pop().toInt()
                        if (n > 0) {
                            val from = st.size - n
                            val part = st.subList(from, st.size).toList()
                            val shift = ((j % n) + n) % n
                            for (k in 0 until n) st[from + (k + shift) % n] = part[k]
                        }
                    }
                }
            }
        }
    }
}
