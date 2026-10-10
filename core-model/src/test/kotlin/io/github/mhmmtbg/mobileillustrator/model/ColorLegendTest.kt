package io.github.mhmmtbg.mobileillustrator.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorLegendTest {
    private fun box(color: Rgba, x: Double) =
        PathNode(subpaths = listOf(Shapes.rect(Rect(x, 0.0, x + 50, 50.0))), fill = Paint.Solid(color))

    private fun doc(vararg colors: Rgba) = Document(
        artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 1000.0, 600.0))),
        layers = listOf(Layer(name = "Katman 1", children = colors.mapIndexed { i, c -> box(c, i * 60.0) })),
    )

    @Test
    fun codesGiveHexRgbAndCmyk() {
        assertEquals(listOf("#FF0000", "RGB 255 0 0", "CMYK 0 100 100 0"), Rgba.rgb(0xFF0000).codeLines())
        assertEquals(listOf("#000000", "RGB 0 0 0", "CMYK 0 0 0 100"), Rgba.Black.codeLines())
        assertEquals(listOf("RGB 128 128 128"), Rgba.rgb(0x808080).codeLines(LegendOptions(hex = false, cmyk = false)))
        assertEquals("CMYK 0 0 0 50", Rgba.rgb(0x808080).codeLines().last())
        // Dosyada CMYK olarak tanımlı renkte özgün değerler yazılır, RGB'den yeniden hesaplanmaz.
        val print = Ink.Cmyk(0.62, 0.0, 0.38, 0.05).toRgb()
        assertEquals("CMYK 62 0 38 5", print.codeLines().last())
    }

    @Test
    fun legendListsEveryDesignColourInsideTheArtboard() {
        val d = doc(Rgba.rgb(0x6B6A42), Rgba.rgb(0xD8CBA0), Rgba.rgb(0x6B6A42), Rgba.rgb(0x2A2B18))
        val r = assertNotNull(d.withColorLegend())
        assertEquals(3, r.colors)
        val layer = r.document.layers.last()
        assertEquals(LEGEND_LAYER, layer.name)
        val group = layer.children.single() as GroupNode
        assertEquals(r.groupId, group.id)
        // Her renk için bir örnek kare ve bir etiket; en çok kullanılan renk başta.
        assertEquals(6, group.children.size)
        val swatch = group.children[0] as PathNode
        assertEquals(0x6B6A42, (swatch.fill as Paint.Solid).color.toArgb() and 0xFFFFFF)
        assertEquals("#6B6A42\nRGB 107 106 66\nCMYK 0 1 38 58", (group.children[1] as TextNode).text)
        val box = assertNotNull(group.bounds())
        val board = d.artboards.first().bounds
        assertTrue(box.left >= board.left && box.right <= board.right && box.bottom <= board.bottom && box.top > board.center.y, "lejant yüzeyin altında durmalı: $box")
        assertEquals(d.colorCodesText(), r.document.colorCodesText(), "lejantın kendi renkleri listeye karışmamalı")
        assertEquals(3, d.colorCodesText().lines().size)
        assertEquals(d.colorGroups().map { it.rgb }, r.document.colorGroups().map { it.rgb })
        assertEquals(LegendOptions(), r.document.legendOptions())
        assertNull(d.legendOptions())
    }

    @Test
    fun addingAgainReplacesTheOldLegendAndManyColoursWrap() {
        val many = (0 until 14).map { Rgba.rgb(0x101010 * it + 0x0000FF) }.toTypedArray()
        val first = assertNotNull(doc(*many).withColorLegend(LegendOptions(rgb = false, cmyk = false)))
        val again = assertNotNull(first.document.withColorLegend())
        assertEquals(2, again.document.layers.size)
        assertEquals(14, again.colors)
        val group = again.document.layers.last().children.single() as GroupNode
        val rows = group.children.filterIsInstance<PathNode>().mapNotNull { it.bounds()?.top }.distinct()
        assertTrue(rows.size > 1, "çok renk birden çok satıra bölünmeli")
        val board = again.document.artboards.first().bounds
        assertTrue(group.bounds()!!.right <= board.right + 1.0)
        assertNull(Document.blank().withColorLegend())
    }
}
