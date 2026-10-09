package io.github.mhmmtbg.mobileillustrator.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.ExportFormat
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.L10n
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.render.BooleanOp
import io.github.mhmmtbg.mobileillustrator.render.PathBoolean
import io.github.mhmmtbg.mobileillustrator.render.TextOutliner
import io.github.mhmmtbg.mobileillustrator.ai.AiImporter
import io.github.mhmmtbg.mobileillustrator.ui.AppTheme
import io.github.mhmmtbg.mobileillustrator.ui.EditorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.Image
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Masaüstü sürümünün çizicisi ve ekranı: pencere açmadan, görüntüye çizerek denenir. */
class DesktopSmokeTest {
    private fun newRoot(): File = File(System.getProperty("java.io.tmpdir"), "mi-test-" + System.nanoTime()).apply { mkdirs() }

    private fun pixel(image: Image, x: Int, y: Int): Int {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        return bitmap.getColor(x, y)
    }

    @Test
    fun sampleFileRendersWithItsColours() {
        val io = DesktopDocumentIo(newRoot())
        val opened = io.openAsset("samples/gopher.ai", "Gopher")
        assertEquals(3, opened.result.document.layers.size)
        val png = io.encode(opened.result.document, ExportFormat.Png)
        val image = Image.makeFromEncoded(png)
        assertTrue(image.width > 1000 && image.height > 500, "boyut ${image.width}x${image.height}")
        // Gopher'ın gövdesi (görüntünün ortası, biraz aşağısı) turkuaz olmalı; sol üst köşe saydam.
        val body = pixel(image, image.width * 46 / 100, image.height * 62 / 100)
        val r = (body shr 16) and 0xFF
        val g = (body shr 8) and 0xFF
        val b = body and 0xFF
        assertTrue(g > r + 20 && g > 150 && b > 150, "gövde rengi %08X".format(body))
        assertEquals(0, pixel(image, 2, 2) ushr 24, "zemin saydam olmalı")
    }

    @Test
    fun exportedFileOpensAgainWithTheSameLayers() {
        val io = DesktopDocumentIo(newRoot())
        val doc = io.openAsset("samples/gopher.ai", "Gopher").result.document
        val again = AiImporter.import(io.encode(doc, ExportFormat.Ai), "Gopher")
        assertTrue(again.writtenByThisApp)
        assertEquals(doc.layers.map { it.name }, again.document.layers.map { it.name })
        assertEquals(doc.layers.map { it.children.size }, again.document.layers.map { it.children.size })
    }

    @Test
    fun shapesCombineAndTextBecomesOutlines() {
        fun square(x: Double) = PathNode(subpaths = listOf(Shapes.rect(Rect(x, 0.0, x + 100.0, 100.0))), fill = Paint.Solid(Rgba.rgb(0xFF0000)))
        val united = assertNotNull(PathBoolean.combine(listOf(square(0.0), square(50.0)), BooleanOp.Unite))
        val xs = united.subpaths.flatMap { sp -> sp.anchors.map { it.point.x } }
        assertEquals(0.0, xs.min(), 0.01)
        assertEquals(150.0, xs.max(), 0.01)
        val intersection = assertNotNull(PathBoolean.combine(listOf(square(0.0), square(50.0)), BooleanOp.Intersect))
        assertEquals(50.0, intersection.subpaths.flatMap { sp -> sp.anchors.map { it.point.x } }.min(), 0.01)

        val text = TextNode(text = "Merhaba", fontSize = 40.0, fill = Paint.Solid(Rgba.rgb(0)))
        assertTrue(TextOutliner.outline(text).size >= 7, "her harf en az bir kapalı yol vermeli")
        assertTrue(TextOutliner.measure(text) > 60.0)
    }

    @Test
    fun editorScreenComposesAndOpensTheSample() {
        L10n.english = false
        val root = newRoot()
        val io = DesktopDocumentIo(root)
        val host = DesktopHost(root) { null }
        lateinit var vm: EditorViewModel
        SwingUtilities.invokeAndWait { vm = EditorViewModel(io, CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
        val scene = ImageComposeScene(1280, 820, Density(1f)) { AppTheme { EditorScreen(vm, host) } }
        var time = 0L
        fun frames(count: Int): Image {
            var last: Image? = null
            repeat(count) {
                SwingUtilities.invokeAndWait { last = scene.render(time) }
                time += 50_000_000L
                Thread.sleep(30)
            }
            return last!!
        }
        frames(5)
        SwingUtilities.invokeAndWait { vm.openSample() }
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && (vm.state.busy != null || vm.state.document.layers.size != 3)) frames(2)
        assertEquals(3, vm.state.document.layers.size, "örnek dosya açılmalı")
        val image = frames(10)
        // Tuvalin ortasında beyaz çalışma yüzeyi ya da çizim olmalı; koyu zemin kalmamalı.
        val centre = pixel(image, 640, 300)
        assertTrue(((centre shr 16) and 0xFF) > 120, "tuval ortası %08X".format(centre))
        SwingUtilities.invokeAndWait { scene.close() }
        assertTrue(Document.blank().layers.isNotEmpty())
    }

    @Test
    fun handToolOnlyPansAndSelectionIsRevealedInLayers() {
        val io = DesktopDocumentIo(newRoot())
        lateinit var vm: EditorViewModel
        SwingUtilities.invokeAndWait {
            vm = EditorViewModel(io, CoroutineScope(SupervisorJob() + Dispatchers.Main))
            vm.onCanvasSize(Size(1000f, 800f))
            vm.openSample()
        }
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && (vm.state.busy != null || vm.state.document.layers.size != 3)) Thread.sleep(50)
        SwingUtilities.invokeAndWait {
            // El aracı: sürüklemek görünümü kaydırır, dokunmak hiçbir şeyi seçmez ya da çizmez.
            val nodes = vm.state.document.layers.sumOf { it.children.size }
            val before = vm.viewport
            vm.selectTool(Tool.Hand)
            vm.onDrag(Offset(400f, 400f), Offset(450f, 430f))
            vm.onDrag(Offset(400f, 400f), Offset(500f, 460f))
            vm.onDragEnd()
            vm.onTap(Offset(500f, 400f))
            assertEquals(before.scale, vm.viewport.scale)
            assertEquals(before.offset.x + 100f, vm.viewport.offset.x, 0.5f)
            assertEquals(before.offset.y + 60f, vm.viewport.offset.y, 0.5f)
            assertTrue(vm.state.selection.isEmpty())
            assertEquals(nodes, vm.state.document.layers.sumOf { it.children.size })
            assertTrue(!vm.state.history.canUndo, "kaydırma belgeyi değiştirmemeli")

            // Seçili nesne katman panelinde gösterilir: panel açılır ve nesnenin katmanı genişler.
            val layer = vm.state.document.layers.first { it.name == "Pallette" }
            vm.selectNode(layer.children.first().id)
            vm.toggleLayersPanel()
            vm.toggleLayersPanel()
            assertTrue(!vm.state.layersOpen)
            vm.revealSelectionInLayers()
            assertTrue(vm.state.layersOpen)
            assertTrue(layer.id in vm.state.expanded)
            assertEquals(1, vm.state.revealTick)
        }
    }
}
