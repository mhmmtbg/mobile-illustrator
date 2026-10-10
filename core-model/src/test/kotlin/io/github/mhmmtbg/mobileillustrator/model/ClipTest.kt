package io.github.mhmmtbg.mobileillustrator.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClipTest {
    private fun box(id: String, x: Double) =
        PathNode(id = id, name = id, subpaths = listOf(Shapes.rect(Rect(x, 0.0, x + 10, 10.0))), fill = Paint.Solid(Rgba.Black))

    private fun doc() = Document(
        artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 200.0, 100.0))),
        layers = listOf(
            Layer(id = "L1", name = "Alt", children = listOf(box("a", 0.0), box("b", 20.0))),
            Layer(
                id = "L2", name = "Üst", locked = true,
                children = listOf(GroupNode(id = "g", transform = Matrix.translate(100.0, 0.0), children = listOf(box("c", 0.0), box("d", 20.0)))),
            ),
        ),
    )

    @Test
    fun nodesAreCopiedInDocumentOrderWithTheirGroupTransform() {
        val clip = doc().clip(setOf("c", "b", "a"))
        assertEquals(listOf("a", "b", "c"), clip.nodes.map { it.id })
        // Grubun içindeki nesne, belgede göründüğü yerde kopyalanır.
        assertEquals(Rect(100.0, 0.0, 110.0, 10.0), clip.nodes[2].bounds())
        assertEquals(3, clip.count)
    }

    @Test
    fun aSelectedGroupOrLayerIsCopiedWholeNotTwice() {
        val d = doc()
        assertEquals(listOf("g"), d.clip(setOf("g", "c", "d")).nodes.map { it.id })
        val clip = d.clip(setOf("a", "c"), setOf("L1"))
        assertEquals(listOf("L1"), clip.layers.map { it.id })
        assertEquals(listOf("c"), clip.nodes.map { it.id })
    }

    @Test
    fun pastingIntoAnotherDocumentGivesFreshIdsAndKeepsLayers() {
        val clip = doc().clip(setOf("a"), setOf("L2"))
        val target = Document.blank(300.0, 300.0)
        val active = target.layers.single().id
        val r = target.paste(clip, active, Vec2(5.0, 7.0))
        val out = r.document
        assertEquals(listOf("Katman 1", "Üst"), out.layers.map { it.name })
        assertTrue(out.layers[1].locked)
        assertEquals(1, out.layers[0].children.size)
        assertEquals(Rect(5.0, 7.0, 15.0, 17.0), out.layers[0].children.single().bounds())
        assertEquals(Rect(105.0, 7.0, 135.0, 17.0), out.layers[1].children.single().bounds())
        val ids = HashSet<String>()
        fun collect(ns: List<Node>) { for (n in ns) { ids += n.id; if (n is GroupNode) collect(n.children) } }
        out.layers.forEach { collect(it.children) }
        assertTrue(ids.none { it in setOf("a", "g", "c", "d") })
        assertEquals(2, r.nodeIds.size)
        assertEquals(1, r.layerIds.size)
        // İkinci kez yapıştırmak çakışan kimlik üretmez.
        val twice = out.paste(clip, active)
        assertEquals(3, twice.document.layers.size)
        assertNotNull(twice.document.findNode(twice.nodeIds.first()))
        assertNotNull(out.findNode(r.nodeIds.first()))
    }

    @Test
    fun mergingCentersTheOtherDocumentOnTheArtboard() {
        val other = doc()
        val target = Document.blank(400.0, 400.0)
        val r = target.merged(other)
        assertEquals(listOf("Katman 1", "Alt", "Üst"), r.document.layers.map { it.name })
        // (100,50) merkezli yüzey (200,200) merkezine taşındı: +100, +150
        assertEquals(Rect(100.0, 150.0, 110.0, 160.0), r.document.layers[1].children[0].bounds())
        assertEquals(2, r.layerIds.size)
    }
}
