package io.github.mhmmtbg.mobileillustrator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mhmmtbg.mobileillustrator.ai.AiImporter
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Görsel regresyon: örnek .ai dosyası Android çizicisiyle çizilir ve depoda duran onaylı görüntüyle
 * karşılaştırılır. Çizimde (renk, font, kırpma, katman) istenmeyen bir değişiklik olursa test düşer.
 * Çizilen görüntü her seferinde cihaza yazılır; CI onu indirip sürüm sayfasına ekler.
 */
@RunWith(AndroidJUnit4::class)
class RenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun render(doc: Document, width: Int): Bitmap {
        val box = doc.artboards.first().bounds
        val scale = width / box.width
        val bmp = Bitmap.createBitmap(width, (box.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())
        canvas.scale(scale.toFloat(), scale.toFloat())
        canvas.translate(-box.left.toFloat(), -box.top.toFloat())
        DocumentRenderer().draw(canvas, doc)
        return bmp
    }

    @Test
    fun sampleFileMatchesApprovedRendering() {
        val target = instrumentation.targetContext
        val bytes = target.assets.open("samples/gopher.ai").use { it.readBytes() }
        val doc = AiImporter.import(bytes, "Gopher").document
        val actual = render(doc, 816)
        File(target.filesDir, "render-actual.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val golden = try {
            instrumentation.context.assets.open("golden-gopher.png").use { BitmapFactory.decodeStream(it) }
        } catch (e: java.io.IOException) {
            null
        }
        if (golden == null) {
            fail("Onaylı görüntü (androidTest/assets/golden-gopher.png) yok; çizilen görüntü render-actual.png olarak kaydedildi")
            return
        }
        assertTrue("boyut farklı: ${actual.width}x${actual.height} / ${golden.width}x${golden.height}", actual.width == golden.width && actual.height == golden.height)
        val n = actual.width * actual.height
        val a = IntArray(n).also { actual.getPixels(it, 0, actual.width, 0, 0, actual.width, actual.height) }
        val g = IntArray(n).also { golden.getPixels(it, 0, golden.width, 0, 0, golden.width, golden.height) }
        var total = 0L
        var far = 0
        for (i in 0 until n) {
            val d = abs((a[i] shr 16 and 0xFF) - (g[i] shr 16 and 0xFF)) + abs((a[i] shr 8 and 0xFF) - (g[i] shr 8 and 0xFF)) + abs((a[i] and 0xFF) - (g[i] and 0xFF))
            total += d
            if (d > 96) far++
        }
        val mean = total / 3.0 / n
        // Kenar yumuşatma farklarına pay var; şekil, renk ya da metin kayması bu eşikleri aşar.
        assertTrue("ortalama fark $mean (eşik 1.5)", mean < 1.5)
        assertTrue("belirgin farklı piksel oranı %${far * 100.0 / n} (eşik %0.6)", far * 100.0 / n < 0.6)
    }
}
