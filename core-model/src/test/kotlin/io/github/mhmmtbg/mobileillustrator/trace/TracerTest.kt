package io.github.mhmmtbg.mobileillustrator.trace

import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TracerTest {
    private val white = 0xFFFFFFFF.toInt()

    /** Çift-tek kuralıyla: nokta bu kapalı yolların içinde mi (eğriler düzleştirilerek). */
    private fun inside(subpaths: List<List<Vec2>>, x: Double, y: Double): Boolean {
        var crossings = 0
        for (poly in subpaths) {
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) crossings++
            }
        }
        return crossings % 2 == 1
    }

    private fun flat(subpaths: List<SubPath>) = subpaths.map { it.flatten(16) }

    /** İzleme sonucunu piksel merkezlerinde yeniden boyar: her pikselin son aldığı renk (ARGB), boyanmadıysa 0. */
    private fun repaint(result: TraceResult): IntArray {
        val out = IntArray(result.width * result.height)
        for (shape in result.shapes) {
            val polys = flat(shape.subpaths)
            val minX = polys.minOf { p -> p.minOf { it.x } }.toInt().coerceAtLeast(0)
            val maxX = polys.maxOf { p -> p.maxOf { it.x } }.toInt().coerceAtMost(result.width - 1)
            val minY = polys.minOf { p -> p.minOf { it.y } }.toInt().coerceAtLeast(0)
            val maxY = polys.maxOf { p -> p.maxOf { it.y } }.toInt().coerceAtMost(result.height - 1)
            for (y in minY..maxY) for (x in minX..maxX) {
                if (inside(polys, x + 0.5, y + 0.5)) out[y * result.width + x] = shape.color.toArgb()
            }
        }
        return out
    }

    private fun close(a: Int, b: Int, tolerance: Int = 40): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF)) <= tolerance

    @Test
    fun circleBecomesAFewSmoothAnchorsCloseToTheTrueCircle() {
        for (r in listOf(12, 40, 150)) {
            val w = r * 2 + 12
            val c = w / 2.0
            val mask = BooleanArray(w * w) { i -> hypot(i % w + 0.5 - c, i / w + 0.5 - c) <= r }
            val loops = Contours.trace(mask, w, w)
            assertEquals(1, loops.size)
            assertEquals(1, loops[0].size)
            val path = CurveFit.fitLoop(loops[0][0], 1.0, 1.0)!!
            assertTrue(path.closed)
            assertTrue(path.anchors.size in 2..8, "r=$r için ${path.anchors.size} düğüm")
            assertTrue(path.anchors.all { it.handleIn != null && it.handleOut != null }, "çemberde köşe olmamalı")
            val worst = path.flatten(24).maxOf { abs(hypot(it.x - c, it.y - c) - r) }
            assertTrue(worst < 1.2, "r=$r için en büyük sapma $worst")
        }
    }

    @Test
    fun rectangleKeepsFourSharpCornersExactly() {
        val w = 60
        val h = 40
        val mask = BooleanArray(w * h) { i -> i % w in 10 until 45 && i / w in 8 until 30 }
        val path = CurveFit.fitLoop(Contours.trace(mask, w, h)[0][0], 1.0, 1.0)!!
        assertEquals(4, path.anchors.size)
        assertTrue(path.anchors.all { it.handleIn == null && it.handleOut == null })
        assertEquals(setOf(Vec2(10.0, 8.0), Vec2(45.0, 8.0), Vec2(45.0, 30.0), Vec2(10.0, 30.0)), path.anchors.map { it.point }.toSet())
    }

    @Test
    fun slantedEdgesStayStraight() {
        // Üçgen: iki eğik kenarı basamaklıdır; izleme üç doğru ve üç köşe vermeli.
        val w = 200
        val h = 160
        fun side(ax: Double, ay: Double, bx: Double, by: Double, x: Double, y: Double) = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
        val mask = BooleanArray(w * h) { i ->
            val x = i % w + 0.5
            val y = i / w + 0.5
            side(20.0, 140.0, 110.0, 15.0, x, y) >= 0 && side(110.0, 15.0, 180.0, 140.0, x, y) >= 0 && y < 140
        }
        val path = CurveFit.fitLoop(Contours.trace(mask, w, h)[0][0], 1.0, 1.0)!!
        // Tepe, piksel genişliğinde küt olabilir; kenarların ortasında düğüm olmamalı ve hepsi doğru olmalı.
        assertTrue(path.anchors.size in 3..4, "düğümler: ${path.anchors.map { it.point }}")
        assertTrue(path.anchors.all { it.handleIn == null && it.handleOut == null })
    }

    @Test
    fun contoursEncloseExactlyTheMaskIncludingHolesAndDiagonalTouches() {
        val rnd = java.util.Random(7)
        repeat(30) {
            val w = 5 + rnd.nextInt(20)
            val h = 5 + rnd.nextInt(20)
            val mask = BooleanArray(w * h) { rnd.nextInt(100) < 45 }
            val components = Contours.trace(mask, w, h)
            // Her pikselin merkezi, döngülerin çift-tek dolgusunda yalnızca maske doluysa içeride kalmalı.
            val polys = components.flatten().map { loop -> (0 until loop.size / 2).map { Vec2(loop[it * 2].toDouble(), loop[it * 2 + 1].toDouble()) } }
            for (y in 0 until h) for (x in 0 until w) {
                assertEquals(mask[y * w + x], polys.isNotEmpty() && inside(polys, x + 0.5, y + 0.5), "piksel $x,$y ($w x $h)")
            }
            // Döngü kenarları yatay ya da dikey olmalı ve her döngü kapanmalı.
            for (loop in components.flatten()) {
                val n = loop.size / 2
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    assertTrue((loop[i * 2] == loop[j * 2]) != (loop[i * 2 + 1] == loop[j * 2 + 1]), "eğik ya da sıfır uzunlukta kenar")
                }
            }
        }
    }

    @Test
    fun layersPaintedInOrderReproduceEveryPixel() {
        // Katman maskeleri üst üste boyandığında her piksel kendi rengini almalı (altına girilen pikseller sonradan boyanır).
        val rnd = java.util.Random(3)
        repeat(20) {
            val w = 8 + rnd.nextInt(24)
            val h = 8 + rnd.nextInt(24)
            val colors = 2 + rnd.nextInt(5)
            // Rastgele ama bloklu bir etiket haritası (bazı pikseller saydam)
            val labels = IntArray(w * h) { i -> val v = ((i % w) / 3 * 7 + (i / w) / 3 * 13 + rnd.nextInt(2)) % (colors + 1); if (v == colors) -1 else v }
            val rank = IntArray(colors) { it }
            val painted = IntArray(w * h) { -1 }
            val mask = BooleanArray(w * h)
            val scratch = IntArray(w * h)
            for (r in 0 until colors) {
                Regions.layerMask(labels, rank, r, w, h, mask, scratch)
                for (i in mask.indices) if (mask[i]) painted[i] = r
            }
            for (i in labels.indices) assertEquals(labels[i], painted[i], "piksel ${i % w},${i / w}")
        }
    }

    /** Beyaz zeminde kırmızı halka ve mavi kare; kenarları yumuşatılmış (4x4 örnekleme). */
    private fun logo(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        var r = 0; var g = 0; var b = 0
        for (sy in 0 until 4) for (sx in 0 until 4) {
            val x = i % w + (sx + 0.5) / 4
            val y = i / w + (sy + 0.5) / 4
            val d = hypot(x - 60, y - 60)
            val c = when {
                d <= 40 && d >= 18 -> 0xE03030
                x in 120.0..190.0 && y in 30.0..95.0 -> 0x2850C8
                else -> 0xFFFFFF
            }
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        (0xFF shl 24) or ((r / 16) shl 16) or ((g / 16) shl 8) or (b / 16)
    }

    @Test
    fun colourLogoGivesOneShapePerRegionWithoutFringes() {
        val w = 220
        val h = 120
        val pixels = logo(w, h)
        // İstenen renk sayısı gerçek renklerden fazla: kenar yumuşatmasının ara tonları ayrı şekil olmamalı.
        val result = Tracer.trace(pixels, w, h, TraceOptions(colors = 8))
        assertEquals(3, result.colorCount, "renkler: ${result.shapes.map { "%06X".format(it.color.toArgb() and 0xFFFFFF) }}")
        assertEquals(3, result.shapes.size)
        // Zemin en altta ve tek parça dikdörtgen: üzerindeki şekiller silinince altında delik kalmaz.
        val background = result.shapes[0]
        assertTrue(close(background.color.toArgb(), white))
        assertEquals(1, background.subpaths.size)
        assertEquals(4, background.subpaths[0].anchors.size)
        // Halka: dış ve iç çember
        val ring = result.shapes.first { close(it.color.toArgb(), 0xE03030, 60) }
        assertEquals(2, ring.subpaths.size)
        assertTrue(result.anchorCount < 40, "${result.anchorCount} düğüm")
        // Yeniden boyandığında özgün görselle örtüşmeli (kenar pikselleri dışında).
        val painted = repaint(result)
        val wrong = pixels.indices.count { !close(painted[it], pixels[it], 90) }
        assertTrue(wrong < pixels.size * 0.02, "$wrong piksel farklı")
    }

    @Test
    fun blackAndWhiteTracesOnlyTheDarkParts() {
        val w = 100
        val h = 60
        val pixels = IntArray(w * h) { i -> if (i % w in 20 until 70 && i / w in 10 until 50) 0xFF202020.toInt() else 0xFFF0F0F0.toInt() }
        val result = Tracer.trace(pixels, w, h, TraceOptions(mode = TraceOptions.Mode.BlackWhite))
        assertEquals(1, result.shapes.size)
        assertEquals(0xFF000000.toInt(), result.shapes[0].color.toArgb())
        val points = result.shapes[0].subpaths[0].anchors.map { it.point }.toSet()
        assertEquals(setOf(Vec2(20.0, 10.0), Vec2(70.0, 10.0), Vec2(70.0, 50.0), Vec2(20.0, 50.0)), points)
    }

    @Test
    fun specklesAreRemovedAndTransparentPixelsAreNotTraced() {
        val w = 80
        val h = 80
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x >= 40 -> 0x00000000 // sağ yarı saydam
                x == 10 && y == 10 -> 0xFFFF0000.toInt() // tek piksellik leke
                else -> 0xFF3060C0.toInt()
            }
        }
        val result = Tracer.trace(pixels, w, h, TraceOptions(colors = 4, speckle = 6))
        assertEquals(1, result.shapes.size)
        val xs = result.shapes[0].subpaths.flatMap { sp -> sp.anchors.map { it.point.x } }
        assertEquals(0.0, xs.min(), 1e-9)
        assertEquals(40.0, xs.max(), 1e-9)
    }

    @Test
    fun backgroundCanBeLeftOut() {
        val result = Tracer.trace(logo(220, 120), 220, 120, TraceOptions(colors = 8, ignoreBackground = true))
        assertEquals(2, result.shapes.size)
        assertTrue(result.shapes.none { close(it.color.toArgb(), white) })
    }

    @Test
    fun tracingCanBeCancelledAndReportsProgress() {
        assertFailsWith<TraceCancelled> { Tracer.trace(logo(220, 120), 220, 120, cancelled = { true }) }
        val seen = ArrayList<Double>()
        Tracer.trace(logo(220, 120), 220, 120, progress = { seen += it })
        assertTrue(seen.isNotEmpty() && seen == seen.sorted() && seen.last() == 1.0, "ilerleme: $seen")
    }

    @Test
    fun manyTinyRegionsCollapseIntoOneCompoundPathPerColour() {
        // Dama deseni: binlerce ayrı kare. Şekil sınırı aşılınca her renk tek bileşik yol olur.
        val w = 120
        val h = 120
        val pixels = IntArray(w * h) { i -> if (((i % w) / 3 + (i / w) / 3) % 2 == 0) 0xFF101010.toInt() else 0xFFF0F0F0.toInt() }
        val result = Tracer.trace(pixels, w, h, TraceOptions(colors = 2, speckle = 0, maxShapes = 100))
        assertEquals(2, result.shapes.size)
        assertTrue(result.shapes.sumOf { it.subpaths.size } > 500)
    }
}
