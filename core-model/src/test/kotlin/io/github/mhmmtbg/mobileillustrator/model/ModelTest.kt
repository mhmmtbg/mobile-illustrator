package io.github.mhmmtbg.mobileillustrator.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModelTest {
    private fun near(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue(kotlin.math.abs(expected - actual) < eps, "beklenen $expected, gelen $actual")

    private val red = Paint.Solid(Rgba.rgb(0xFF0000))

    @Test
    fun matrixInverseRoundTrips() {
        val m = Matrix.translate(10.0, -4.0) * Matrix.rotate(0.7) * Matrix.scale(2.0, 3.0)
        val p = Vec2(5.0, 9.0)
        val back = m.inverse()!!.apply(m.apply(p))
        near(p.x, back.x); near(p.y, back.y)
        assertNull(Matrix.scale(0.0).inverse())
    }

    @Test
    fun matrixMultiplicationAppliesRightFirst() {
        val m = Matrix.translate(10.0, 0.0) * Matrix.scale(2.0)
        assertEquals(Vec2(12.0, 2.0), m.apply(Vec2(1.0, 1.0)))
    }

    @Test
    fun ellipseBoundsAreExactNotControlPolygon() {
        val r = Rect(10.0, 20.0, 110.0, 80.0)
        val b = Shapes.ellipse(r).bounds()!!
        near(r.left, b.left); near(r.top, b.top); near(r.right, b.right); near(r.bottom, b.bottom)
    }

    @Test
    fun curveBoundsIncludeBulge() {
        // Uçları y=0'da olan, yukarı doğru kabaran eğri: sınır kutusu kontrol noktasına kadar çıkmaz.
        val sp = SubPath(
            listOf(
                Anchor(Vec2(0.0, 0.0), handleOut = Vec2(0.0, 100.0)),
                Anchor(Vec2(100.0, 0.0), handleIn = Vec2(100.0, 100.0)),
            ),
        )
        val b = sp.bounds()!!
        near(75.0, b.bottom)
        near(0.0, b.top)
    }

    @Test
    fun hitTestRespectsFillOrderAndLocks() {
        val bottom = PathNode(name = "alt", subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 100.0, 100.0))), fill = red)
        val top = PathNode(name = "üst", subpaths = listOf(Shapes.ellipse(Rect(50.0, 50.0, 150.0, 150.0))), fill = red)
        val layer = Layer(name = "L", children = listOf(bottom, top))
        val doc = Document.blank().copy(layers = listOf(layer))

        assertEquals(top.id, doc.hitTest(Vec2(90.0, 90.0), 1.0)?.id)
        assertEquals(bottom.id, doc.hitTest(Vec2(10.0, 10.0), 1.0)?.id)
        // Elipsin sınır kutusunun köşesi elipsin dışındadır.
        assertNull(doc.hitTest(Vec2(148.0, 148.0), 1.0))

        val lockedTop = doc.updateNode(top.id) { (it as PathNode).copy(locked = true) }
        assertEquals(bottom.id, lockedTop.hitTest(Vec2(90.0, 90.0), 1.0)?.id)

        val hiddenLayer = doc.updateLayer(layer.id) { it.copy(visible = false) }
        assertNull(hiddenLayer.hitTest(Vec2(10.0, 10.0), 1.0))
    }

    @Test
    fun hitTestStrokeOnlyNeedsToBeNearTheLine() {
        val line = PathNode(
            subpaths = listOf(Shapes.line(Vec2(0.0, 0.0), Vec2(100.0, 0.0))),
            stroke = Stroke(red, width = 4.0),
        )
        val doc = Document.blank().let { it.addNode(it.layers[0].id, line) }
        assertNotNull(doc.hitTest(Vec2(50.0, 2.5), 1.0))
        assertNull(doc.hitTest(Vec2(50.0, 10.0), 1.0))
    }

    @Test
    fun hitTestFollowsTransformsAndReturnsOutermostGroup() {
        val inner = PathNode(subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 10.0, 10.0))), fill = red)
        val group = GroupNode(children = listOf(inner), transform = Matrix.translate(200.0, 200.0))
        val doc = Document.blank().let { it.addNode(it.layers[0].id, group) }
        assertNull(doc.hitTest(Vec2(5.0, 5.0), 0.5))
        assertEquals(group.id, doc.hitTest(Vec2(205.0, 205.0), 0.5)?.id)
        val b = group.bounds()!!
        near(200.0, b.left); near(210.0, b.right)
    }

    @Test
    fun evenOddLeavesHole() {
        val donut = PathNode(
            subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 100.0, 100.0)), Shapes.rect(Rect(25.0, 25.0, 75.0, 75.0))),
            fill = red,
            fillRule = FillRule.EvenOdd,
        )
        val doc = Document.blank().let { it.addNode(it.layers[0].id, donut) }
        assertNull(doc.hitTest(Vec2(50.0, 50.0), 0.1))
        assertNotNull(doc.hitTest(Vec2(10.0, 10.0), 0.1))
    }

    @Test
    fun updateNodeSharesUntouchedBranches() {
        val a = PathNode(subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 1.0, 1.0))))
        val b = PathNode(subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 1.0, 1.0))))
        val l1 = Layer(name = "1", children = listOf(GroupNode(children = listOf(a))))
        val l2 = Layer(name = "2", children = listOf(b))
        val doc = Document.blank().copy(layers = listOf(l1, l2))

        val moved = doc.updateNode(a.id) { it.transformedBy(Matrix.translate(5.0, 0.0)) }
        assertSame(doc.layers[1], moved.layers[1])
        assertEquals(5.0, moved.findNode(a.id)!!.transform.e)
        assertSame(doc, doc.updateNode("yok") { it })

        val removed = doc.removeNode(a.id)
        assertNull(removed.findNode(a.id))
        assertEquals(l1.id, doc.layerOf(a.id)?.id)
    }

    @Test
    fun historyUndoRedo() {
        var h = History(present = 0)
        assertFalse(h.canUndo)
        h = h.push(1).push(2)
        assertSame(h, h.push(2))
        h = h.undo()
        assertEquals(1, h.present)
        assertTrue(h.canRedo)
        h = h.redo()
        assertEquals(2, h.present)
        h = h.undo().push(3)
        assertFalse(h.canRedo)
        assertEquals(listOf(0, 1), h.past)
    }

    @Test
    fun historyHonoursLimit() {
        var h = History(present = 0, limit = 3)
        for (i in 1..10) h = h.push(i)
        assertEquals(listOf(7, 8, 9), h.past)
    }

    @Test
    fun rgbaArgbRoundTrip() {
        assertEquals(0xFF336699.toInt(), Rgba.rgb(0x336699).toArgb())
        assertEquals(0x80FF0000.toInt(), Rgba.fromArgb(0x80FF0000.toInt()).toArgb())
    }
}
