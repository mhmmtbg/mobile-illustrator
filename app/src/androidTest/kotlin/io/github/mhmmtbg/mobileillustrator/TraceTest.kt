package io.github.mhmmtbg.mobileillustrator

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mhmmtbg.mobileillustrator.model.Artboard
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Paint as ModelPaint
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.platform.AndroidDocumentIo
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import io.github.mhmmtbg.mobileillustrator.trace.TraceOptions
import io.github.mhmmtbg.mobileillustrator.trace.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Vektöre çevirme, cihazın kendi görsel çözücüsü ve çizicisiyle uçtan uca: bir görsel çizilir, PNG'ye sıkıştırılır,
 * çözülüp izlenir, çıkan yollar yeniden çizilir ve özgün görselle karşılaştırılır.
 */
@RunWith(AndroidJUnit4::class)
class TraceTest {
    @Test
    fun tracedShapesRedrawTheImage() {
        val w = 360
        val h = 240
        val source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        Canvas(source).apply {
            drawColor(0xFFFFFFFF.toInt())
            paint.color = 0xFFE03030.toInt()
            drawCircle(110f, 120f, 80f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            drawCircle(110f, 120f, 35f, paint)
            paint.color = 0xFF2850C8.toInt()
            drawRect(220f, 50f, 330f, 180f, paint)
        }
        val png = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val pixels = AndroidDocumentIo(app).decodePixels(ImageData(png, "image/png", w, h), 360)
        assertEquals(w, pixels.width)
        assertEquals(h, pixels.height)

        val result = Tracer.trace(pixels.argb, pixels.width, pixels.height, TraceOptions(colors = 8))
        assertEquals("renk sayısı", 3, result.colorCount)
        assertEquals("şekil sayısı", 3, result.shapes.size)
        assertTrue("düğüm sayısı ${result.anchorCount}", result.anchorCount in 12..40)

        // Yollar Android çizicisiyle yeniden çizilir (çift-tek dolgu: halkanın ortası boş kalmalı).
        val nodes = result.shapes.map { PathNode(subpaths = it.subpaths, fill = ModelPaint.Solid(it.color), fillRule = FillRule.EvenOdd) }
        val doc = Document(name = "iz", artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, w.toDouble(), h.toDouble()))), layers = listOf(Layer(name = "iz", children = nodes)))
        val redrawn = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        DocumentRenderer().draw(Canvas(redrawn), doc)
        val a = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(w * h).also { redrawn.getPixels(it, 0, w, 0, 0, w, h) }
        var different = 0
        for (i in a.indices) {
            val d = abs(((a[i] shr 16) and 0xFF) - ((b[i] shr 16) and 0xFF)) + abs(((a[i] shr 8) and 0xFF) - ((b[i] shr 8) and 0xFF)) + abs((a[i] and 0xFF) - (b[i] and 0xFF))
            if (d > 120) different++
        }
        assertTrue("$different piksel özgün görselden belirgin biçimde farklı", different < w * h * 0.01)
    }
}
