package io.github.mhmmtbg.mobileillustrator.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ColorGroupsTest {
    private val red = Rgba.rgb(0xE5484D)
    private val blue = Rgba.rgb(0x3B9BFF)
    private fun box(id: String, fill: Paint?, stroke: Rgba? = null, locked: Boolean = false) = PathNode(
        id = id, subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 10.0, 10.0))), fill = fill, stroke = stroke?.let { Stroke(Paint.Solid(it), 2.0) }, locked = locked,
    )

    private fun doc() = Document(
        artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 100.0, 100.0))),
        layers = listOf(
            Layer(
                id = "L1", name = "Alt",
                children = listOf(
                    box("a", Paint.Solid(red)),
                    box("b", Paint.Solid(red.copy(a = 0.5)), stroke = blue),
                    GroupNode(id = "g", children = listOf(box("c", Paint.Solid(blue)), TextNode(id = "t", text = "x", fill = Paint.Solid(red)))),
                    box("k", Paint.Solid(red), locked = true),
                    box("gr", Paint.LinearGradient(Vec2(0.0, 0.0), Vec2(1.0, 0.0), listOf(GradientStop(0.0, red), GradientStop(1.0, Rgba.White)))),
                ),
            ),
            Layer(id = "L2", name = "Kilitli", locked = true, children = listOf(box("z", Paint.Solid(red)))),
        ),
    )

    @Test
    fun coloursAreGroupedAcrossFillsStrokesGroupsAndGradients() {
        val groups = doc().colorGroups()
        assertEquals(listOf(0xE5484D, 0x3B9BFF, 0xFFFFFF), groups.map { it.rgb })
        // Yarı saydam kırmızı da kırmızıdır; kilitli nesne ve kilitli katman sayılmaz.
        assertEquals(listOf("a", "b", "t", "gr"), groups[0].nodeIds)
        assertEquals(4, groups[0].fills)
        assertEquals(listOf("b", "c"), groups[1].nodeIds)
        assertEquals(1, groups[1].strokes)
    }

    @Test
    fun recolouringChangesEveryUseAndKeepsAlphaAndLockedObjects() {
        val d = doc()
        val green = Rgba.rgb(0x40C057)
        val out = d.recolored(0xE5484D, green)
        assertEquals(listOf(0x40C057, 0x3B9BFF, 0xFFFFFF), out.colorGroups().map { it.rgb })
        val b = out.findNode("b") as PathNode
        assertEquals(0.5, (b.fill as Paint.Solid).color.a)
        assertEquals(blue, (b.stroke!!.paint as Paint.Solid).color)
        assertEquals(green, ((out.findNode("gr") as PathNode).fill as Paint.LinearGradient).stops[0].color)
        assertEquals(green, ((out.findNode("t") as TextNode).fill as Paint.Solid).color)
        // Kilitli olanlar ve dokunulmayan dallar aynen kalır.
        assertEquals(red, ((out.findNode("k") as PathNode).fill as Paint.Solid).color)
        assertSame(d.layers[1], out.layers[1])
        assertSame(d.findNode("c"), out.findNode("c"))
        assertSame(d, d.recolored(0x123456, green))
    }
}
