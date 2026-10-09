package io.github.mhmmtbg.mobileillustrator.ai

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.Artboard
import io.github.mhmmtbg.mobileillustrator.model.ClipPath
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.ImportException
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.bounds
import io.github.mhmmtbg.mobileillustrator.pdf.PngDecoder
import io.github.mhmmtbg.mobileillustrator.pdf.PngEncoder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundTripTest {
    private fun near(e: Double, a: Double, eps: Double = 1e-3) = assertTrue(abs(e - a) < eps, "beklenen $e, gelen $a")
    private fun solid(hex: Int, a: Double = 1.0) = Paint.Solid(Rgba.rgb(hex).copy(a = a))

    private fun roundTrip(doc: Document): Document {
        val r = AiImporter.import(AiExporter.export(doc), doc.name)
        assertEquals(emptyList(), r.warnings)
        return r.document
    }

    private fun single(vararg nodes: io.github.mhmmtbg.mobileillustrator.model.Node, w: Double = 400.0, h: Double = 300.0) = Document(
        name = "test",
        artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, w, h))),
        layers = listOf(Layer(name = "Katman 1", children = nodes.toList())),
    )

    @Test
    fun pathGeometryStyleAndNameSurvive() {
        val curve = SubPath(
            listOf(
                Anchor(Vec2(10.0, 20.0), handleOut = Vec2(40.0, 5.0)),
                Anchor(Vec2(90.0, 60.0), handleIn = Vec2(70.0, 80.0)),
                Anchor(Vec2(30.0, 110.0)),
            ),
            closed = true,
        )
        val node = PathNode(
            name = "Şekil ğüşiöç",
            subpaths = listOf(curve),
            fill = solid(0x3B6FE0, 0.5),
            stroke = Stroke(solid(0x14213D), 3.5, LineCap.Round, dash = listOf(4.0, 2.0), dashOffset = 1.0),
            fillRule = FillRule.EvenOdd,
        )
        val back = roundTrip(single(node)).layers.single().children.single() as PathNode
        assertEquals("Şekil ğüşiöç", back.name)
        assertEquals(FillRule.EvenOdd, back.fillRule)
        val sp = back.subpaths.single()
        assertTrue(sp.closed)
        assertEquals(3, sp.anchors.size)
        near(10.0, sp.anchors[0].point.x); near(20.0, sp.anchors[0].point.y)
        near(40.0, sp.anchors[0].handleOut!!.x); near(5.0, sp.anchors[0].handleOut!!.y)
        near(70.0, sp.anchors[1].handleIn!!.x); near(80.0, sp.anchors[1].handleIn!!.y)
        assertNull(sp.anchors[2].handleIn)
        val fill = back.fill as Paint.Solid
        assertEquals(Rgba.rgb(0x3B6FE0).toArgb() and 0xFFFFFF, fill.color.toArgb() and 0xFFFFFF)
        near(0.5, fill.color.a)
        val st = back.stroke!!
        near(3.5, st.width)
        assertEquals(LineCap.Round, st.cap)
        assertEquals(2, st.dash.size); near(4.0, st.dash[0]); near(1.0, st.dashOffset)
        near(1.0, (st.paint as Paint.Solid).color.a)
    }

    @Test
    fun layersKeepOrderVisibilityLockAndOpacity() {
        val r = PathNode(subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 10.0, 10.0))), fill = solid(0xFF0000))
        val doc = Document(
            name = "katmanlar",
            artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 100.0, 100.0))),
            layers = listOf(
                Layer(name = "Arka plan", children = listOf(r), locked = true),
                Layer(name = "Gizli", children = listOf(r.copy(id = "b")), visible = false),
                Layer(name = "Üst 日本", children = listOf(r.copy(id = "c"), r.copy(id = "d")), opacity = 0.4),
            ),
        )
        val back = roundTrip(doc)
        assertEquals(listOf("Arka plan", "Gizli", "Üst 日本"), back.layers.map { it.name })
        assertEquals(listOf(true, false, false), back.layers.map { it.locked })
        assertEquals(listOf(true, false, true), back.layers.map { it.visible })
        near(0.4, back.layers[2].opacity)
        assertEquals(2, back.layers[2].children.size)
    }

    @Test
    fun groupsClipsTransformsAndHiddenObjects() {
        val inner = PathNode(name = "iç", subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 50.0, 50.0))), fill = solid(0x00FF00))
        val clipped = GroupNode(
            name = "Maske",
            children = listOf(inner, inner.copy(id = "x", name = "Yol", visible = false)),
            clip = ClipPath(listOf(Shapes.ellipse(Rect(0.0, 0.0, 40.0, 40.0)))),
            transform = Matrix.translate(100.0, 50.0) * Matrix.scale(2.0),
            opacity = 0.5,
        )
        val locked = inner.copy(id = "l", name = "Kilitli", locked = true)
        val back = roundTrip(single(clipped, locked)).layers.single().children
        assertEquals(2, back.size)
        val g = back[0] as GroupNode
        assertEquals("Maske", g.name)
        near(0.5, g.opacity)
        val clip = assertNotNull(g.clip)
        // Dönüşüm koordinatlara işlenir: 40'lık elips 2 kat büyüyüp (100,50)'ye taşınır.
        val cb = clip.subpaths.single().bounds()!!
        near(100.0, cb.left); near(50.0, cb.top); near(180.0, cb.right); near(130.0, cb.bottom)
        assertEquals(2, g.children.size)
        assertEquals("iç", g.children[0].name)
        assertEquals(false, g.children[1].visible)
        val gb = g.children[0].bounds()!!
        near(200.0, gb.right); near(150.0, gb.bottom)
        assertEquals("Kilitli", back[1].name)
        assertTrue(back[1].locked)
    }

    @Test
    fun gradientsSurviveWithStopsAndPlacement() {
        val lin = Paint.LinearGradient(
            Vec2(0.0, 0.0), Vec2(100.0, 0.0),
            listOf(GradientStop(0.0, Rgba.rgb(0xFF0000)), GradientStop(0.3, Rgba.rgb(0x00FF00)), GradientStop(1.0, Rgba.rgb(0x0000FF))),
        )
        val rad = Paint.RadialGradient(Vec2(50.0, 50.0), 40.0, listOf(GradientStop(0.0, Rgba.White), GradientStop(1.0, Rgba.Black)))
        val a = PathNode(subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 100.0, 100.0))), fill = lin, transform = Matrix.translate(20.0, 30.0))
        val b = PathNode(id = "b", subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 100.0, 100.0))), fill = rad)
        val back = roundTrip(single(a, b)).layers.single().children
        val l = (back[0] as PathNode).fill as Paint.LinearGradient
        val start = l.transform.apply(l.start)
        val end = l.transform.apply(l.end)
        near(20.0, start.x); near(30.0, start.y); near(120.0, end.x); near(30.0, end.y)
        // Ara durak yerinde ve yeşil olmalı
        val mid = l.stops.minByOrNull { abs(it.offset - 0.3) }!!
        near(0.3, mid.offset, 1e-3)
        assertEquals(0x00FF00, mid.color.toArgb() and 0xFFFFFF)
        assertEquals(0xFF0000, l.stops.first().color.toArgb() and 0xFFFFFF)
        assertEquals(0x0000FF, l.stops.last().color.toArgb() and 0xFFFFFF)
        val r = (back[1] as PathNode).fill as Paint.RadialGradient
        val c = r.transform.apply(r.center)
        near(50.0, c.x); near(50.0, c.y)
        near(40.0, r.radius * r.transform.meanScale)
    }

    @Test
    fun textStaysEditableIncludingTurkish() {
        val t = TextNode(
            name = "Başlık",
            text = "Çığ İşçi (şöğüı) \\ €",
            fontSize = 32.0,
            fontFamily = "serif",
            bold = true,
            fill = solid(0x112233),
            transform = Matrix.translate(40.0, 120.0) * Matrix.rotate(0.3),
        )
        val back = roundTrip(single(t)).layers.single().children.single() as TextNode
        assertEquals(t.text, back.text)
        assertEquals("Başlık", back.name)
        assertEquals("serif", back.fontFamily)
        assertTrue(back.bold)
        near(32.0, back.fontSize)
        near(t.transform.a, back.transform.a); near(t.transform.b, back.transform.b)
        near(40.0, back.transform.e); near(120.0, back.transform.f)
        assertEquals(0x112233, (back.fill as Paint.Solid).color.toArgb() and 0xFFFFFF)
    }

    @Test
    fun nonLatinTextIsOutlinedButStillComesBackAsText() {
        val t = TextNode(text = "Привет 日本", fontSize = 20.0, transform = Matrix.translate(10.0, 50.0), fill = solid(0xAA0000))
        var asked = 0
        val bytes = AiExporter.export(
            single(t),
            AiExporter.Options(outlineText = { asked++; listOf(Shapes.rect(Rect(0.0, -15.0, 80.0, 0.0))) }),
        )
        assertEquals(1, asked)
        val back = AiImporter.import(bytes, "t").document.layers.single().children.single() as TextNode
        assertEquals("Привет 日本", back.text)
        assertEquals(0xAA0000, (back.fill as Paint.Solid).color.toArgb() and 0xFFFFFF)
        near(50.0, back.transform.f)
    }

    @Test
    fun imagesSurviveWithAlpha() {
        val png = PngEncoder.encode(3, 2) { y, out ->
            for (x in 0 until 3) {
                out[x * 4] = (x * 100).toByte(); out[x * 4 + 1] = (y * 200).toByte(); out[x * 4 + 2] = 7
                out[x * 4 + 3] = if (x == 2) 0 else (-1).toByte()
            }
        }
        val node = ImageNode(image = ImageData(png, "image/png", 3, 2), transform = Matrix.translate(10.0, 20.0) * Matrix.scale(5.0))
        val back = roundTrip(single(node)).layers.single().children.single() as ImageNode
        assertEquals(3, back.image.width)
        near(10.0, back.transform.e); near(20.0, back.transform.f); near(5.0, back.transform.a); near(5.0, back.transform.d)
        val raw = PngDecoder.decode(back.image.bytes)!!
        assertEquals(100, raw.rgb[3].toInt() and 0xFF)
        assertEquals(200, raw.rgb[3 * 3 + 1].toInt() and 0xFF)
        assertEquals(0, raw.alpha!![2].toInt() and 0xFF)
        assertEquals(255, raw.alpha!![0].toInt() and 0xFF)
    }

    @Test
    fun multipleArtboardsBecomePages() {
        fun box(x: Double) = PathNode(id = "n$x", subpaths = listOf(Shapes.rect(Rect(x + 10, 10.0, x + 60, 60.0))), fill = solid(0x123456))
        val doc = Document(
            name = "çoklu",
            artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 200.0, 100.0)), Artboard(bounds = Rect(260.0, 0.0, 460.0, 100.0))),
            layers = listOf(Layer(name = "L", children = listOf(box(0.0), box(260.0)))),
        )
        val back = roundTrip(doc)
        assertEquals(2, back.artboards.size)
        near(260.0, back.artboards[1].bounds.left)
        val kids = back.layers.single().children
        assertEquals(2, kids.size)
        near(270.0, kids[1].bounds()!!.left)
    }

    @Test
    fun rejectsNonPdfInput() {
        assertFailsWith<ImportException> { AiImporter.import("%!PS-Adobe-3.0\n%%Creator: Adobe Illustrator(R) 8.0".toByteArray(), "eski") }
        assertFailsWith<ImportException> { AiImporter.import("merhaba".toByteArray(), "x") }
    }

    @Test
    fun readsHandWrittenPdfWithBrokenXref() {
        // xref bilerek yanlış: okuyucu dosyayı tarayarak toparlamalı.
        val pdf = """%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 100] /Contents 4 0 R >> endobj
4 0 obj << /Length 9999 >>
stream
1 0 0 rg 10 10 50 30 re f
0 0 1 RG 2 w 0 0 m 100 100 l S
endstream
endobj
xref
0 1
0000000000 65535 f 
trailer << /Root 1 0 R /Size 5 >>
startxref
7
%%EOF
""".toByteArray(Charsets.ISO_8859_1)
        val doc = AiImporter.import(pdf, "elle").document
        val kids = doc.layers.single().children
        assertEquals(2, kids.size)
        val rect = kids[0] as PathNode
        // PDF'te y yukarı: (10,10)-(60,40) kutusu belgede y=60..90 aralığına düşer.
        val b = rect.bounds()!!
        near(10.0, b.left); near(60.0, b.top); near(60.0, b.right); near(90.0, b.bottom)
        assertEquals(0xFF0000, (rect.fill as Paint.Solid).color.toArgb() and 0xFFFFFF)
        near(2.0, (kids[1] as PathNode).stroke!!.width)
    }
}

class BlendTest {
    @Test
    fun blendModesSurviveOnPathsAndGroups() {
        fun box(id: String) = io.github.mhmmtbg.mobileillustrator.model.PathNode(
            id = id,
            subpaths = listOf(Shapes.rect(Rect(0.0, 0.0, 10.0, 10.0))),
            fill = Paint.Solid(Rgba.rgb(0x336699)),
        )
        val multiply = box("a").copy(blendMode = io.github.mhmmtbg.mobileillustrator.model.BlendMode.Multiply, opacity = 0.5)
        val group = GroupNode(
            name = "Gölge",
            children = listOf(box("b"), box("c")),
            blendMode = io.github.mhmmtbg.mobileillustrator.model.BlendMode.Screen,
        )
        val doc = Document(
            name = "karışım",
            artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 50.0, 50.0))),
            layers = listOf(Layer(name = "L", children = listOf(multiply, group))),
        )
        val r = AiImporter.import(AiExporter.export(doc), "karışım")
        assertEquals(emptyList(), r.warnings)
        val kids = r.document.layers.single().children
        assertEquals(io.github.mhmmtbg.mobileillustrator.model.BlendMode.Multiply, kids[0].blendMode)
        assertTrue(abs(kids[0].opacity - 0.5) < 1e-3)
        val g = kids[1] as GroupNode
        assertEquals("Gölge", g.name)
        assertEquals(io.github.mhmmtbg.mobileillustrator.model.BlendMode.Screen, g.blendMode)
        assertEquals(2, g.children.size)
        assertEquals(io.github.mhmmtbg.mobileillustrator.model.BlendMode.Normal, g.children[0].blendMode)
    }
}
