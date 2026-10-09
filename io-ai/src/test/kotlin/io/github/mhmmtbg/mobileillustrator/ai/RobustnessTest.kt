package io.github.mhmmtbg.mobileillustrator.ai

import io.github.mhmmtbg.mobileillustrator.model.Artboard
import io.github.mhmmtbg.mobileillustrator.model.ClipPath
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.ImportException
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.pdf.Filters
import io.github.mhmmtbg.mobileillustrator.pdf.PdfException
import io.github.mhmmtbg.mobileillustrator.pdf.PngEncoder
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/** Güvenilmeyen girdi: okuyucu ne görürse görsün ya sonuç döndürmeli ya da [ImportException] atmalı. */
class RobustnessTest {

    private fun richDocument(): ByteArray {
        val png = PngEncoder.encode(4, 4) { _, out -> for (i in out.indices) out[i] = (i * 37).toByte() }
        val grad = Paint.LinearGradient(
            Vec2(0.0, 0.0), Vec2(50.0, 0.0),
            listOf(GradientStop(0.0, Rgba.rgb(0xFF0000)), GradientStop(0.5, Rgba.rgb(0x00FF00)), GradientStop(1.0, Rgba.rgb(0x0000FF))),
        )
        val box = PathNode(subpaths = listOf(Shapes.ellipse(Rect(0.0, 0.0, 50.0, 40.0))), fill = grad, stroke = Stroke(Paint.Solid(Rgba.Black), 2.0))
        val doc = Document(
            name = "fuzz",
            artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 200.0, 200.0))),
            layers = listOf(
                Layer(name = "A", children = listOf(box, ImageNode(image = ImageData(png, "image/png", 4, 4)))),
                Layer(
                    name = "B", opacity = 0.5,
                    children = listOf(
                        GroupNode(children = listOf(box.copy(id = "x"), TextNode(text = "Merhaba dünya")), clip = ClipPath(listOf(Shapes.rect(Rect(0.0, 0.0, 30.0, 30.0)))), opacity = 0.7),
                    ),
                ),
            ),
        )
        return AiExporter.export(doc)
    }

    private fun mustNotCrash(bytes: ByteArray, label: String) {
        try {
            AiImporter.import(bytes, "fuzz")
        } catch (e: ImportException) {
            // beklenen: anlaşılır hata
        } catch (e: Throwable) {
            fail("$label: okuyucudan beklenmeyen hata kaçtı: $e")
        }
    }

    @Test
    fun mutatedFilesNeverEscapeWithUnexpectedErrors() {
        val base = richDocument()
        val rnd = Random(20261009)
        val started = System.nanoTime()
        for (i in 0 until 600) {
            val b = base.copyOf()
            val out: ByteArray = when (i % 5) {
                0 -> { repeat(1 + rnd.nextInt(8)) { b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() }; b }
                1 -> b.copyOf(rnd.nextInt(b.size)) // kesik dosya
                2 -> { // bir parçayı sil
                    val a = rnd.nextInt(b.size); val len = rnd.nextInt(minOf(400, b.size - a))
                    b.copyOfRange(0, a) + b.copyOfRange(a + len, b.size)
                }
                3 -> { // bir parçayı çoğalt
                    val a = rnd.nextInt(b.size); val len = rnd.nextInt(minOf(400, b.size - a))
                    b.copyOfRange(0, a + len) + b.copyOfRange(a, b.size)
                }
                else -> { // rakamları boz: uzunluklar, başvurular, koordinatlar
                    repeat(6) { val p = rnd.nextInt(b.size); if (b[p] in '0'.code.toByte()..'9'.code.toByte()) b[p] = ('0'.code + rnd.nextInt(10)).toByte() }
                    b
                }
            }
            mustNotCrash(out, "mutasyon $i")
        }
        assertTrue((System.nanoTime() - started) / 1e9 < 120, "bozuk dosyalar makul sürede reddedilmeli")
    }

    @Test
    fun hostileStructuresAreRejectedOrSurvived() {
        fun pdf(content: String, extra: String = "") = ("""%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Contents 4 0 R /Resources << /XObject << /F 5 0 R >> >> >> endobj
4 0 obj << /Length ${content.length} >>
stream
$content
endstream
endobj
$extra
trailer << /Root 1 0 R >>
""").toByteArray(Charsets.ISO_8859_1)

        // Kendini çağıran form: sonsuz özyineleme olmamalı
        val selfForm = "5 0 obj << /Type /XObject /Subtype /Form /BBox [0 0 1 1] /Resources << /XObject << /F 5 0 R >> >> /Length 20 >>\nstream\n/F Do /F Do /F Do /F Do\nendstream\nendobj"
        mustNotCrash(pdf("/F Do", selfForm), "kendini çağıran form")
        // Çok derin iç içe diziler ve sözlükler
        mustNotCrash(pdf("[".repeat(200_000) + " TJ"), "derin dizi")
        mustNotCrash(pdf("/OC " + "<<".repeat(100_000) + " BDC"), "derin sözlük")
        // Dengesiz q ve eksik işlenenler
        mustNotCrash(pdf("q ".repeat(50_000) + "1 0 0 rg re f Q Q Q m l c h S"), "dengesiz durum yığını")
        // Kendi kendine başvuran sayfa ağacı
        mustNotCrash(
            "%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n2 0 obj << /Type /Pages /Kids [2 0 R 2 0 R] /Count 2 >> endobj\ntrailer << /Root 1 0 R >>".toByteArray(),
            "döngülü sayfa ağacı",
        )
        // Döngülü dolaylı uzunluk
        mustNotCrash(pdf("0 0 m").let { String(it, Charsets.ISO_8859_1).replace("/Length 5", "/Length 4 0 R").toByteArray(Charsets.ISO_8859_1) }, "döngülü uzunluk")
        mustNotCrash(ByteArray(0), "boş dosya")
        mustNotCrash("%PDF-".toByteArray(), "yalnızca başlık")
        mustNotCrash(ByteArray(4096) { 0 }, "sıfırlar")
    }

    @Test
    fun decompressionBombIsStoppedByTheSizeCap() {
        // ~260 MB sıfır, birkaç yüz kilobayta sıkışır.
        val deflater = Deflater(9)
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(1 shl 20)
        val buf = ByteArray(1 shl 16)
        repeat(260) {
            deflater.setInput(chunk)
            while (!deflater.needsInput()) out.write(buf, 0, deflater.deflate(buf))
        }
        deflater.finish()
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        val bomb = out.toByteArray()
        assertTrue(bomb.size < 1 shl 20)
        assertFailsWith<PdfException> { Filters.inflate(bomb) }
    }
}
