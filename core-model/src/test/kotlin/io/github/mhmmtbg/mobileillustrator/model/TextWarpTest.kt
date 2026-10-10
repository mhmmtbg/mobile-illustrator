package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextWarpTest {
    private val box = Rect(0.0, -40.0, 200.0, 0.0)
    private val bar = listOf(Shapes.rect(box))

    @Test
    fun zeroBendLeavesShapesUntouched() {
        for (style in WarpStyle.entries) {
            assertEquals(bar, TextWarp(style, 0.0).apply(bar))
            assertEquals(Vec2(30.0, -10.0), TextWarp(style, 0.0).map(Vec2(30.0, -10.0), box))
        }
    }

    @Test
    fun arcBendsTheBaselineIntoACircleOfTheSameLength() {
        // Tam bükümde alt kenar yarım çember olur: uçları aynı yükseklikte, aralarındaki uzaklık çap kadardır.
        val warp = TextWarp(WarpStyle.Arc, 1.0)
        val radius = box.width / Math.PI
        val left = warp.map(Vec2(0.0, 0.0), box)
        val right = warp.map(Vec2(200.0, 0.0), box)
        val middle = warp.map(Vec2(100.0, 0.0), box)
        assertEquals(left.y, right.y, 1e-9)
        assertEquals(2 * radius, right.x - left.x, 1e-9)
        assertEquals(Vec2(100.0, 0.0), middle)
        // Yukarı büküm: uçlar ortadan aşağıda kalır (gökkuşağı gibi).
        assertTrue(left.y > middle.y)
        // Eksi büküm ters yöne eğer.
        assertTrue(TextWarp(WarpStyle.Arc, -1.0).map(Vec2(0.0, 0.0), box).y < 0.0)
        // Alt kenar üzerindeki her nokta çember merkezine aynı uzaklıktadır.
        for (x in listOf(0.0, 37.0, 100.0, 180.0)) {
            val p = warp.map(Vec2(x, 0.0), box)
            assertEquals(radius, hypot(p.x - 100.0, p.y - radius), 1e-9)
        }
    }

    @Test
    fun warpedShapesStayClosedAndFollowTheMapping() {
        for (style in WarpStyle.entries) {
            val warp = TextWarp(style, 0.6)
            val out = warp.apply(bar)
            assertEquals(1, out.size)
            assertTrue(out[0].closed)
            assertTrue(out[0].anchors.size > 8, "$style: düz kenarlar bükülebilmek için bölünmeli")
            // Bükülmüş yol, özgün dikdörtgenin kenarlarındaki noktaların bükülmüş hallerinden geçmeli.
            val flat = out[0].flatten(8)
            for (x in listOf(0.0, 50.0, 100.0, 150.0, 200.0)) {
                for (y in listOf(box.top, box.bottom)) {
                    val expected = warp.map(Vec2(x, y), box)
                    val nearest = flat.minOf { hypot(it.x - expected.x, it.y - expected.y) }
                    assertTrue(nearest < 0.6, "$style ($x,$y): en yakın nokta $nearest uzakta")
                }
            }
            // Kutusu, yolun gerçek kapladığı alanı içermeli.
            val bounds = warp.bounds(box)
            for (p in flat) {
                assertTrue(p.x >= bounds.left - 0.5 && p.x <= bounds.right + 0.5 && p.y >= bounds.top - 0.5 && p.y <= bounds.bottom + 0.5, "$style: $p kutunun dışında")
            }
        }
    }

    @Test
    fun textBoundsGrowWithTheWarpAndHitTestingFollows() {
        val text = TextNode(text = "Merhaba dünya", fontSize = 40.0)
        val flat = text.localBounds()
        val arched = text.copy(warp = TextWarp(WarpStyle.Arch, 1.0))
        val bounds = arched.localBounds()
        assertTrue(bounds.height > flat.height + 20, "kemer metni yükseltir: ${flat.height} -> ${bounds.height}")
        assertEquals(flat.width, bounds.width, 1e-6)
        // Kemerin tepesi artık metnin içinde, düz halinin üstündeki boşlukta.
        val top = Vec2((flat.left + flat.right) / 2, bounds.top + 5)
        val doc = Document.blank().let { d -> d.copy(layers = listOf(d.layers[0].copy(children = listOf(arched)))) }
        assertEquals(arched.id, doc.hitTest(top, 1.0)?.id)
        assertTrue(abs(top.y - flat.top) > 10)
    }
}
