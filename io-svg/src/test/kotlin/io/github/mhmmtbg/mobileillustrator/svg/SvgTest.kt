package io.github.mhmmtbg.mobileillustrator.svg

import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.bounds
import io.github.mhmmtbg.mobileillustrator.model.boundsOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvgTest {
    private fun near(e: Double, a: Double, eps: Double = 1e-3) = assertTrue(abs(e - a) < eps, "beklenen $e, gelen $a")
    private fun rgb(p: Paint?) = (p as Paint.Solid).color.toArgb() and 0xFFFFFF

    @Test
    fun letterSpacingIsWrittenAndReadBack() {
        val doc = SvgImporter.import("""<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><text x="10" y="50" font-size="20" letter-spacing="5">AB</text></svg>""".toByteArray(), "t").document
        val t = doc.layers.flatMap { it.children }.single() as TextNode
        near(250.0, t.tracking)
        val svg = SvgExporter.export(doc)
        assertTrue("letter-spacing=\"5\"" in svg, svg)
    }

    @Test
    fun pathCommandsRelativeImplicitAndSmooth() {
        val sp = SvgPathParser.parse("M10 10 20 10l0,10h-10z m30,0 c0,10 10,10 10,0 s10-10 10,0")
        assertEquals(2, sp.size)
        assertTrue(sp[0].closed)
        assertEquals(4, sp[0].anchors.size)
        near(10.0, sp[0].anchors[3].point.x); near(20.0, sp[0].anchors[3].point.y)
        // İkinci alt yol (10,10)+(30,0)'dan başlar; S komutu önceki kontrol noktasını yansıtır.
        near(40.0, sp[1].anchors[0].point.x)
        val mid = sp[1].anchors[1]
        near(50.0, mid.point.x)
        near(50.0, mid.handleIn!!.x); near(20.0, mid.handleIn!!.y)
        near(50.0, mid.handleOut!!.x); near(0.0, mid.handleOut!!.y)
    }

    @Test
    fun arcsBecomeCurvesWithCorrectExtent() {
        // Yarıçapı 50 olan yarım daire: (0,0)'dan (100,0)'a, saat yönünde (SVG'de aşağı doğru kabarır mı? sweep=1 yukarı).
        val up = SvgPathParser.parse("M0 0 A50 50 0 0 1 100 0").single().bounds()!!
        near(-50.0, up.top, 0.05); near(0.0, up.bottom, 0.05)
        val down = SvgPathParser.parse("M0 0 A50 50 0 0 0 100 0").single().bounds()!!
        near(50.0, down.bottom, 0.05)
        // Sıkışık bayrak yazımı
        val compact = SvgPathParser.parse("M0 0a50 50 0 01100 0").single().bounds()!!
        near(100.0, compact.right, 0.05); near(-50.0, compact.top, 0.05)
    }

    @Test
    fun quadraticAndGarbageDoNotHang() {
        val q = SvgPathParser.parse("M0 0 Q50 100 100 0 T200 0 Z 5 5 ?? L").single()
        near(50.0, q.bounds()!!.bottom, 0.01)
        assertTrue(SvgPathParser.parse("").isEmpty())
    }

    @Test
    fun importsLayersShapesCssAndTransforms() {
        val svg = """<?xml version="1.0"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 100" width="400" height="200">
  <defs><style>.cls-1{fill:#ff0000;stroke:#00f;stroke-width:2px}.cls-2,.x{fill:none}</style>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff"/><stop offset="1" style="stop-color:rgb(0,128,0);stop-opacity:.5"/></linearGradient>
    <clipPath id="c"><circle cx="50" cy="50" r="20"/></clipPath>
  </defs>
  <g id="Arka_x20_plan" data-name="Arka plan"><rect class="cls-1" x="10" y="10" width="50" height="30" rx="5"/></g>
  <g id="Layer_2" display="none"><g transform="translate(100 0) scale(2)" clip-path="url(#c)" opacity="0.5">
     <polygon points="0,0 10,0 5,10" fill="url(#g)" fill-rule="evenodd"/></g>
     <text x="5" y="20" font-size="10" font-weight="700">Merhaba <tspan>dünya</tspan></text></g>
  <ellipse cx="150" cy="50" rx="10" ry="5" style="fill:currentColor" color="teal"/>
</svg>""".toByteArray()
        assertTrue(SvgImporter.sniff(svg))
        val doc = SvgImporter.import(svg, "t").document
        near(400.0, doc.artboards.single().bounds.width)
        assertEquals(listOf("Arka plan", "Layer 2", "Katman 3"), doc.layers.map { it.name })
        assertEquals(listOf(true, false, true), doc.layers.map { it.visible })

        // viewBox 2 kat büyütülür: katman içeriği dönüşümlü bir grupta durur.
        val g0 = doc.layers[0].children.single() as GroupNode
        val rect = g0.children.single() as PathNode
        assertEquals(0xFF0000, rgb(rect.fill)); assertEquals(0x0000FF, rgb(rect.stroke!!.paint)); near(2.0, rect.stroke!!.width)
        assertEquals(8, rect.subpaths.single().anchors.size)
        val rb = doc.boundsOf(rect.id)!!
        near(20.0, rb.left); near(20.0, rb.top); near(120.0, rb.right); near(80.0, rb.bottom)

        val g1 = doc.layers[1].children.single() as GroupNode
        val clipped = g1.children[0] as GroupNode
        near(0.5, clipped.opacity)
        assertNotNull(clipped.clip)
        val poly = clipped.children.single() as PathNode
        assertEquals(FillRule.EvenOdd, poly.fillRule)
        val grad = poly.fill as Paint.LinearGradient
        near(0.5, grad.stops[1].color.a)
        // Nesne kutusuna göre gradyan: (0,0)-(1,0) üçgenin 10 birimlik kutusuna yayılır.
        near(10.0, grad.transform.apply(grad.end).x)
        val text = g1.children[1] as TextNode
        assertEquals("Merhaba dünya", text.text)
        assertTrue(text.bold)
        near(10.0, text.fontSize)

        val ell = doc.layers[2].children.single() as PathNode
        assertEquals(0x008080, rgb(ell.fill))
        assertNull(ell.stroke)
        near(280.0, doc.boundsOf(ell.id)!!.left)
    }

    @Test
    fun exportThenImportKeepsStructure() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
  <g id="A"><path d="M0 0L10 0L10 10Z" fill="#123456" stroke="#000" stroke-dasharray="2 1"/><circle cx="50" cy="50" r="5" fill-opacity=".25"/></g>
  <g id="B" opacity=".5"><rect width="5" height="5"/></g></svg>""".toByteArray()
        val a = SvgImporter.import(svg, "x").document
        val b = SvgImporter.import(SvgExporter.export(a).toByteArray(), "x").document
        assertEquals(a.layers.map { it.name to it.children.size }, b.layers.map { it.name to it.children.size })
        near(0.5, b.layers[1].opacity)
        val p = b.layers[0].children[0] as PathNode
        assertEquals(0x123456, rgb(p.fill))
        assertEquals(listOf(2.0, 1.0), p.stroke!!.dash)
        near(0.25, ((b.layers[0].children[1] as PathNode).fill as Paint.Solid).color.a)
        near(a.layers[0].children[1].bounds()!!.left, b.layers[0].children[1].bounds()!!.left)
    }
}
