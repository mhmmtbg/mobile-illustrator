package io.github.mhmmtbg.mobileillustrator.model

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpsTest {
    private fun near(e: Double, a: Double, eps: Double = 1e-6) = assertTrue(abs(e - a) < eps, "beklenen $e, gelen $a")
    private val red = Paint.Solid(Rgba.rgb(0xFF0000))
    private fun box(id: String, x: Double = 0.0) =
        PathNode(id = id, subpaths = listOf(Shapes.rect(Rect(x, 0.0, x + 10, 10.0))), fill = red, stroke = Stroke(red, 2.0))

    private fun doc(vararg nodes: Node) = Document.blank().let { d -> d.copy(layers = listOf(d.layers[0].copy(children = nodes.toList()))) }

    @Test
    fun appliedBakesGeometryAndKeepsStrokeWidth() {
        val n = box("a").applied(Matrix.scale(3.0)) as PathNode
        assertTrue(n.transform.isIdentity)
        near(30.0, n.bounds()!!.right)
        near(2.0, n.stroke!!.width)
    }

    @Test
    fun transformNodeWorksInsideTransformedGroup() {
        val g = GroupNode(id = "g", children = listOf(box("a")), transform = Matrix.translate(100.0, 0.0) * Matrix.scale(2.0))
        val d = doc(g)
        near(100.0, d.boundsOf("a")!!.left); near(120.0, d.boundsOf("a")!!.right)
        // Belge uzayında 10 birim sağa: grubun 2x ölçeğine rağmen tam 10 kaymalı.
        val moved = d.transformNode("a", Matrix.translate(10.0, 0.0))
        near(110.0, moved.boundsOf("a")!!.left); near(130.0, moved.boundsOf("a")!!.right)
    }

    @Test
    fun deepHitFindsLeafInsideGroup() {
        val g = GroupNode(id = "g", children = listOf(box("a"), box("b", 50.0)), transform = Matrix.translate(100.0, 100.0))
        val d = doc(g)
        assertEquals("g", d.hitTest(Vec2(155.0, 105.0), 0.5)?.id)
        assertEquals("b", d.hitTestDeep(Vec2(155.0, 105.0), 0.5)?.id)
        assertNull(d.hitTestDeep(Vec2(5.0, 5.0), 0.5))
    }

    @Test
    fun groupAndUngroupPreserveOrderAndPlacement() {
        val d = doc(box("a"), box("b", 20.0), box("c", 40.0))
        val (grouped, gid) = assertNotNull(d.group(setOf("a", "c")))
        assertEquals(listOf("b", gid), grouped.layers[0].children.map { it.id })
        assertEquals(listOf("a", "c"), (grouped.findNode(gid) as GroupNode).children.map { it.id })
        assertNull(d.group(setOf("a")))

        val shifted = grouped.updateNode(gid) { it.transformedBy(Matrix.translate(5.0, 0.0)).withOpacity(0.5) }
        val (flat, kids) = assertNotNull(shifted.ungroup(gid))
        assertEquals(listOf("b", "a", "c"), flat.layers[0].children.map { it.id })
        assertEquals(listOf("a", "c"), kids)
        near(5.0, flat.boundsOf("a")!!.left)
        near(0.5, flat.findNode("a")!!.opacity)
    }

    @Test
    fun reorderAndDuplicate() {
        val d = doc(box("a"), box("b"), box("c"))
        assertEquals(listOf("b", "a", "c"), d.reorderNode("a", ZMove.Forward).layers[0].children.map { it.id })
        assertEquals(listOf("b", "c", "a"), d.reorderNode("a", ZMove.Front).layers[0].children.map { it.id })
        assertEquals(listOf("c", "a", "b"), d.reorderNode("c", ZMove.Back).layers[0].children.map { it.id })
        assertEquals(listOf("a", "b", "c"), d.reorderNode("a", ZMove.Backward).layers[0].children.map { it.id })

        val (dup, ids) = d.duplicate(setOf("b"), Vec2(7.0, 0.0))
        assertEquals(4, dup.layers[0].children.size)
        assertEquals(ids.single(), dup.layers[0].children[2].id)
        near(7.0, dup.boundsOf(ids.single())!!.left)
    }

    @Test
    fun layerOperations() {
        var d = doc(box("a"))
        val l1 = d.layers[0].id
        d = d.addLayer(Layer(id = "L2", name = d.nextLayerName()), l1)
        assertEquals("Katman 2", d.layers[1].name)
        d = d.moveNodesToLayer(setOf("a"), "L2")
        assertEquals(0, d.layers[0].children.size)
        assertEquals("L2", d.layerOf("a")?.id)
        d = d.moveLayer("L2", up = false)
        assertEquals("L2", d.layers[0].id)
        d = d.removeLayer(l1)
        assertEquals(1, d.layers.size)
        assertEquals(1, d.removeLayer("L2").layers.size)
    }

    @Test
    fun marqueeUsesBounds() {
        val d = doc(box("a"), box("b", 100.0))
        assertEquals(listOf("b"), d.nodesIn(Rect(90.0, -5.0, 105.0, 5.0)).map { it.id })
        assertEquals(2, d.nodesIn(Rect(-1.0, -1.0, 200.0, 20.0)).size)
    }

    @Test
    fun insertAnchorKeepsCurveShape() {
        val sp = SubPath(
            listOf(
                Anchor(Vec2(0.0, 0.0), handleOut = Vec2(0.0, 100.0)),
                Anchor(Vec2(100.0, 0.0), handleIn = Vec2(100.0, 100.0)),
            ),
        )
        val before = sp.segments()[0].pointAt(0.5)
        val loc = assertNotNull(listOf(sp).nearest(Vec2(50.0, 80.0)))
        near(0.5, loc.t, 0.02)
        val (out, ref) = assertNotNull(listOf(sp).insertAnchor(loc))
        assertEquals(3, out[0].anchors.size)
        assertEquals(1, ref.index)
        // Yeni düğüm eğrinin üzerinde, uçlar yerinde, toplam şekil aynı
        near(before.x, out[0].anchors[1].point.x, 2.0)
        val b0 = sp.bounds()!!
        val b1 = out[0].bounds()!!
        near(b0.bottom, b1.bottom, 1e-6); near(b0.right, b1.right, 1e-6)
        assertTrue(out[0].anchors[1].isSmooth())
    }

    @Test
    fun smoothHandleMirrorsOppositeAndCornerDoesNot() {
        val smooth = Anchor(Vec2(0.0, 0.0), handleIn = Vec2(-10.0, 0.0), handleOut = Vec2(20.0, 0.0))
        assertTrue(smooth.isSmooth())
        val moved = smooth.withHandle(outgoing = true, position = Vec2(0.0, 30.0))
        near(0.0, moved.handleIn!!.x); near(-10.0, moved.handleIn!!.y)
        val corner = Anchor(Vec2(0.0, 0.0), handleIn = Vec2(-10.0, 0.0), handleOut = Vec2(0.0, 20.0))
        assertEquals(Vec2(-10.0, 0.0), corner.withHandle(true, Vec2(5.0, 5.0)).handleIn)
    }

    @Test
    fun toggleSmoothAndDeleteAnchor() {
        val tri = listOf(SubPath(listOf(Anchor(Vec2(0.0, 0.0)), Anchor(Vec2(50.0, 50.0)), Anchor(Vec2(100.0, 0.0))), closed = true))
        val s = tri.toggleSmooth(AnchorRef(0, 1))
        assertTrue(s[0].anchors[1].isSmooth())
        assertNull(s.toggleSmooth(AnchorRef(0, 1))[0].anchors[1].handleIn)
        val two = tri.deleteAnchor(AnchorRef(0, 1))
        assertEquals(2, two[0].anchors.size)
        assertEquals(false, two[0].closed)
        assertEquals(0, two.deleteAnchor(AnchorRef(0, 0)).size)
    }

    @Test
    fun pencilFitReducesPointsAndStaysClose() {
        val pts = (0..200).map { Vec2(it.toDouble(), 30 * sin(it / 30.0)) }
        val sp = assertNotNull(PathFit.fit(pts, 1.0))
        assertTrue(sp.anchors.size in 3..30, "düğüm sayısı ${sp.anchors.size}")
        val fitted = listOf(sp)
        for (p in pts) assertTrue(fitted.nearest(p)!!.distance < 3.0)
        assertEquals(2, PathFit.fit(listOf(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(10.0, 0.0)), 0.5)!!.anchors.size)
    }
}
