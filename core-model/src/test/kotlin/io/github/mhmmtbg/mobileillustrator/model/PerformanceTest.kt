package io.github.mhmmtbg.mobileillustrator.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PerformanceTest {
    private fun bigDocument(n: Int): Document {
        val red = Paint.Solid(Rgba.rgb(0xCC3333))
        val nodes = (0 until n).map { i ->
            val x = (i % 100) * 12.0
            val y = (i / 100) * 12.0
            PathNode(id = "n$i", subpaths = listOf(Shapes.ellipse(Rect(x, y, x + 10, y + 10))), fill = red, stroke = Stroke(red, 1.0))
        }
        return Document(name = "büyük", artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 1200.0, 1200.0))), layers = listOf(Layer(name = "L", children = nodes)))
    }

    private inline fun millis(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1e6
    }

    @Test
    fun hitTestOnTenThousandObjectsIsInteractive() {
        val doc = bigDocument(10_000)
        DocIndex.of(doc) // ilk kurulum
        // Isınma
        repeat(50) { doc.hitTest(Vec2(605.0, 605.0), 2.0) }
        var found = 0
        val ms = millis { repeat(200) { i -> if (doc.hitTest(Vec2(5.0 + (i % 100) * 12, 5.0 + (i % 50) * 12), 2.0) != null) found++ } }
        assertEquals(200, found)
        // Dokunuş başına 1 ms'nin çok altında olmalı; yavaş CI makineleri için geniş pay bırakıldı.
        assertTrue(ms / 200 < 5.0, "dokunuş başına ${ms / 200} ms")
        assertEquals("n5050", doc.hitTest(Vec2(605.0, 605.0), 1.0)?.id)
    }

    @Test
    fun indexIsReusedAcrossEdits() {
        val doc = bigDocument(10_000)
        val first = DocIndex.of(doc)
        assertSame(first, DocIndex.of(doc))
        val moved = doc.transformNode("n42", Matrix.translate(3.0, 0.0))
        val ms = millis { DocIndex.of(moved) }
        val second = DocIndex.of(moved)
        // Değişmeyen düğümün girdisi aynı nesne olarak devralınır.
        assertSame(first.entry(doc.findNode("n7")!!), second.entry(moved.findNode("n7")!!))
        assertNotNull(second.bounds(moved.findNode("n42")!!))
        assertTrue(ms < 200.0, "artımlı dizin $ms ms")
        // Kontur payı kutuya eklenir
        val e = second.entry(moved.findNode("n7")!!)!!
        assertTrue(e.painted!!.left < e.bounds!!.left)
    }

    @Test
    fun marqueeAndMoveOnLargeSelection() {
        val doc = bigDocument(10_000)
        val picked = doc.nodesIn(Rect(0.0, 0.0, 1200.0, 600.0)).mapTo(HashSet()) { it.id }
        assertTrue(picked.size > 4000)
        val ms = millis { repeat(10) { doc.transformNodes(picked, Matrix.translate(it.toDouble(), 0.0)) } }
        assertTrue(ms / 10 < 150.0, "taşıma karesi başına ${ms / 10} ms")
        assertNotNull(doc.boundsOf(picked))
    }
}
