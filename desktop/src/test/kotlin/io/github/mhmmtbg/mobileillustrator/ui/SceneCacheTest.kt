package io.github.mhmmtbg.mobileillustrator.ui

import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kalabalık belge önbelleğinin "bu görüntü hâlâ geçerli mi" kararı. Yanlış karar eski görüntü gösterir. */
class SceneCacheTest {
    private fun box(id: String) = PathNode(id = id, subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 10.0, 10.0))), fill = Paint.Solid(Rgba.rgb(0xFF0000)))

    private val a = box("a")
    private val b = box("b")
    private val c = box("c")
    private val doc = Document.blank().let { it.copy(layers = listOf(it.layers[0].copy(children = listOf(a, b, c)))) }

    private fun with(vararg nodes: PathNode) = doc.copy(layers = listOf(doc.layers[0].copy(children = nodes.toList())))

    @Test
    fun changingOnlyTheExcludedNodeKeepsTheImageValid() {
        val moved = with(a, b.copy(transform = Matrix.translate(5.0, 5.0)), c)
        assertTrue(SceneCache.sameExcept(moved, doc, setOf("b")))
        assertTrue(SceneCache.sameExcept(with(a, c), doc, setOf("b")), "silinen nesne de hariç tutulmuş sayılır")
        assertTrue(SceneCache.sameExcept(with(b, a, c), doc, setOf("b")), "hariç tutulanın sırası önemli değil")
    }

    @Test
    fun anyOtherChangeInvalidatesTheImage() {
        assertFalse(SceneCache.sameExcept(with(a.copy(opacity = 0.5), b, c), doc, setOf("b")))
        assertFalse(SceneCache.sameExcept(with(c, b, a), doc, setOf("b")), "diğerlerinin sırası değişti")
        assertFalse(SceneCache.sameExcept(with(a, b), doc, setOf("b")), "başka bir nesne silindi")
        assertFalse(SceneCache.sameExcept(with(a, b, c, box("d")), doc, setOf("b")), "yeni nesne eklendi")
        val hidden = doc.copy(layers = listOf(doc.layers[0].copy(visible = false)))
        assertFalse(SceneCache.sameExcept(hidden, doc, setOf("b")))
        val extraLayer = doc.copy(layers = doc.layers + Layer(name = "x"))
        assertFalse(SceneCache.sameExcept(extraLayer, doc, setOf("b")))
    }

    @Test
    fun nestedSelectionMapsToItsTopLevelNode() {
        val group = GroupNode(id = "g", children = listOf(b, GroupNode(id = "inner", children = listOf(c))))
        val nested = doc.copy(layers = listOf(doc.layers[0].copy(children = listOf(a, group))))
        assertEquals(setOf("g"), SceneCache.topLevelOf(nested, setOf("c")))
        assertEquals(setOf("a", "g"), SceneCache.topLevelOf(nested, setOf("a", "inner")))
        assertEquals(emptySet(), SceneCache.topLevelOf(nested, emptySet()))
    }
}
