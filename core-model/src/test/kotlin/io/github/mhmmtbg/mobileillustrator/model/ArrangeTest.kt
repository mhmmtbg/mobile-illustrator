package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArrangeTest {
    private fun near(e: Double, a: Double, eps: Double = 1e-6) = assertTrue(abs(e - a) < eps, "beklenen $e, gelen $a")
    private val red = Paint.Solid(Rgba.rgb(0xFF0000))
    private fun box(id: String, x: Double, y: Double, w: Double = 10.0, h: Double = 10.0) =
        PathNode(id = id, subpaths = listOf(Shapes.rect(Rect(x, y, x + w, y + h))), fill = red)

    private fun doc(vararg nodes: Node) = Document.blank(200.0, 100.0).let { d -> d.copy(layers = listOf(d.layers[0].copy(children = nodes.toList()))) }

    @Test
    fun alignToSelectionAndToArtboard() {
        val d = doc(box("a", 0.0, 0.0), box("b", 50.0, 30.0, 20.0, 20.0))
        val left = d.align(setOf("a", "b"), Align.Left)
        near(0.0, left.boundsOf("b")!!.left)
        val bottom = d.align(setOf("a", "b"), Align.Bottom)
        near(50.0, bottom.boundsOf("a")!!.bottom)
        val centered = d.align(setOf("a", "b"), Align.CenterH)
        near(centered.boundsOf("a")!!.center.x, centered.boundsOf("b")!!.center.x)
        // Tek nesne: çalışma yüzeyine göre
        val onBoard = d.align(setOf("a"), Align.CenterV, d.artboards[0].bounds)
        near(50.0, onBoard.boundsOf("a")!!.center.y)
        assertEquals(d, d.align(setOf("a"), Align.Right, null))
    }

    @Test
    fun distributeEqualisesGaps() {
        val d = doc(box("a", 0.0, 0.0), box("b", 15.0, 0.0), box("c", 90.0, 0.0))
        val r = d.distribute(setOf("a", "b", "c"), horizontal = true)
        near(0.0, r.boundsOf("a")!!.left); near(90.0, r.boundsOf("c")!!.left)
        near(45.0, r.boundsOf("b")!!.left)
        assertEquals(d, d.distribute(setOf("a", "b"), true))
    }

    @Test
    fun clippingMaskUsesTopmostPathAndCanBeReleased() {
        val d = doc(box("a", 0.0, 0.0, 100.0, 100.0), box("m", 10.0, 10.0, 20.0, 20.0))
        val (masked, gid) = assertNotNull(d.makeClippingMask(setOf("a", "m")))
        val g = masked.findNode(gid) as GroupNode
        assertEquals(listOf("a"), g.children.map { it.id })
        near(10.0, masked.boundsOf(gid)!!.left); near(30.0, masked.boundsOf(gid)!!.right)
        assertNull(masked.hitTest(Vec2(80.0, 80.0), 0.5))
        assertEquals(gid, masked.hitTest(Vec2(15.0, 15.0), 0.5)?.id)
        val released = masked.releaseClippingMask(gid)
        val rg = released.findNode(gid) as GroupNode
        assertNull(rg.clip)
        assertEquals(2, rg.children.size)
        assertNull(d.makeClippingMask(setOf("a")))
    }

    @Test
    fun gradientSpecFitsTheBoxAndRoundTrips() {
        val spec = GradientSpec(false, 90.0, listOf(GradientStop(0.0, Rgba.Black), GradientStop(1.0, Rgba.White)))
        val p = spec.paintFor(Rect(0.0, 0.0, 40.0, 100.0)) as Paint.LinearGradient
        near(20.0, p.start.x); near(0.0, p.start.y); near(100.0, p.end.y)
        near(90.0, GradientSpec.from(p)!!.angleDegrees)
        val diag = GradientSpec(false, 45.0, spec.stops).paintFor(Rect(0.0, 0.0, 10.0, 10.0)) as Paint.LinearGradient
        near(0.0, diag.start.x, 1e-9); near(10.0, diag.end.y, 1e-9)
        val g = GroupNode(children = listOf(box("a", 0.0, 0.0), box("b", 100.0, 0.0))).withGradient(spec) as GroupNode
        near(105.0, ((g.children[1] as PathNode).fill as Paint.LinearGradient).start.x)
        assertTrue(GradientSpec(true, 0.0, spec.stops).paintFor(Rect(0.0, 0.0, 10.0, 30.0)) is Paint.RadialGradient)
    }

    @Test
    fun snappingPullsEdgesAndCentersWithinThreshold() {
        val target = Rect(100.0, 100.0, 200.0, 200.0)
        // Sol kenar hedefin sağ kenarına 3 birim uzak; üstte hizalama yok
        val r = Snap.box(Rect(203.0, 10.0, 223.0, 30.0), listOf(target), 5.0)
        near(-3.0, r.dx); near(0.0, r.dy)
        assertEquals(listOf(200.0), r.guidesX)
        assertTrue(r.guidesY.isEmpty())
        // Merkezler: 148 -> 150
        val c = Snap.box(Rect(138.0, 300.0, 158.0, 320.0), listOf(target), 5.0)
        near(2.0, c.dx)
        val far = Snap.box(Rect(400.0, 400.0, 410.0, 410.0), listOf(target), 5.0)
        near(0.0, far.dx); near(0.0, far.dy)
        val pt = Snap.point(Vec2(101.5, 198.0), listOf(target), 2.5)
        near(-1.5, pt.dx); near(2.0, pt.dy)
    }
}
