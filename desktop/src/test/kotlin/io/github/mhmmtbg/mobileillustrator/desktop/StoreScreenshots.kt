package io.github.mhmmtbg.mobileillustrator.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.PaintTarget
import io.github.mhmmtbg.mobileillustrator.editor.TextPrompt
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.model.GradientSpec
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.Ink
import io.github.mhmmtbg.mobileillustrator.model.L10n
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.ui.AppTheme
import io.github.mhmmtbg.mobileillustrator.ui.ColorPickerDialog
import io.github.mhmmtbg.mobileillustrator.ui.EditorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities

/**
 * Mağaza ekran görüntülerini üretir. Uygulamanın gerçek ekranları (Android ile ortak kod) istenen ekran
 * boyutunda ve dilde görüntüye çizilir; elle ekran görüntüsü almaya gerek kalmaz.
 *
 * Kullanım: ./gradlew :desktop:storeScreenshots   (çıktı: store/raw/<dil>/<cihaz>/)
 */
object StoreScreenshots {
    private class Device(val name: String, val width: Int, val height: Int, val density: Float)

    private val devices = listOf(
        Device("phone", 1080, 1920, 2.625f),
        Device("tablet7", 1200, 1920, 2.0f),
        Device("tablet10", 2560, 1600, 2.0f),
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.getOrElse(0) { "store/raw" })
        for (language in listOf("tr", "en")) {
            for (device in devices) {
                try {
                    capture(File(out, "$language/${device.name}"), language == "en", device)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    System.exit(1)
                }
            }
        }
        System.exit(0)
    }

    private fun capture(out: File, english: Boolean, device: Device) {
        out.mkdirs()
        L10n.english = english
        val root = File(System.getProperty("java.io.tmpdir"), "mi-store-" + System.nanoTime()).apply { mkdirs() }
        val io = DesktopDocumentIo(root)
        val host = DesktopHost(root) { null }
        lateinit var vm: EditorViewModel
        fun ui(block: () -> Unit) = SwingUtilities.invokeAndWait(block)
        ui { vm = EditorViewModel(io, CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
        var overlay by mutableStateOf<(@Composable () -> Unit)?>(null)
        val scene = ImageComposeScene(device.width, device.height, Density(device.density)) {
            AppTheme {
                EditorScreen(vm, host)
                overlay?.invoke()
            }
        }
        var time = 0L
        fun frames(count: Int = 12) = repeat(count) {
            ui { scene.render(time) }
            time += 50_000_000L
            Thread.sleep(8)
        }
        fun settle() {
            frames(6)
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline && vm.state.busy != null) frames(2)
            // Bilgi iletisi (ör. "Kaydedildi") ekranda kalmasın.
            ui { vm.messageShown() }
            frames(8)
        }
        fun shot(name: String) {
            settle()
            ui { File(out, name).writeBytes(scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
            println("${out.path}/$name")
        }
        fun drag(from: Vec2, to: Vec2) = ui {
            val a = vm.viewport.toScreen(from)
            val b = vm.viewport.toScreen(to)
            vm.onDrag(a, Offset((a.x + b.x) / 2, (a.y + b.y) / 2))
            vm.onDrag(a, b)
            vm.onDragEnd()
        }
        settle()

        // Galeri dolu görünsün diye önce iki küçük çalışma daha oluşturulur. Renk, çizilen (seçili) şekle uygulanır.
        fun shape(tool: Tool, from: Vec2, to: Vec2, color: Int) {
            ui { vm.selectTool(tool) }
            drag(from, to)
            ui { vm.selectPaintTarget(PaintTarget.Stroke); vm.setColor(null); vm.selectPaintTarget(PaintTarget.Fill); vm.setColor(Rgba.rgb(color)) }
        }
        ui { vm.newDocument(800.0, 800.0) }
        settle()
        ui { vm.renameDocument(if (english) "Logo draft" else "Logo taslağı") }
        shape(Tool.Ellipse, Vec2(140.0, 140.0), Vec2(660.0, 660.0), 0x3B9BFF)
        ui { vm.applyGradient(GradientSpec(radial = false, angleDegrees = 45.0, stops = listOf(GradientStop(0.0, Rgba.rgb(0x7950F2)), GradientStop(1.0, Rgba.rgb(0x3B9BFF))))) }
        shape(Tool.Rectangle, Vec2(300.0, 300.0), Vec2(500.0, 500.0), 0xFFD43B)
        ui { vm.transformSelection(300.0, 300.0, 200.0, 200.0, 45.0) }
        settle()

        ui { vm.newDocument(1080.0, 1350.0) }
        settle()
        ui { vm.renameDocument(if (english) "Poster" else "Afiş") }
        shape(Tool.Rectangle, Vec2(0.0, 0.0), Vec2(1080.0, 1350.0), 0x14213D)
        shape(Tool.Ellipse, Vec2(240.0, 220.0), Vec2(840.0, 820.0), 0xFF9A3C)
        shape(Tool.Ellipse, Vec2(420.0, 400.0), Vec2(980.0, 960.0), 0xE64980)
        ui { vm.setOpacity(0.8, true) }
        ui {
            vm.confirmText(TextPrompt(nodeId = null, position = Vec2(110.0, 1180.0), text = if (english) "SUMMER FEST" else "YAZ ŞENLİĞİ", fontSize = 120.0, bold = true))
            vm.setColor(Rgba.rgb(0xFFFFFF))
            vm.selectTool(Tool.Select)
        }
        shot("5-poster.png")

        // 1) Gerçek bir Illustrator dosyası açık, bir nesne seçili
        ui { vm.openSample() }
        settle()
        val body = vm.state.document.layers.first { it.name == "Gopher" }.children.filterIsInstance<PathNode>()
            .maxByOrNull { n -> n.subpaths.sumOf { it.anchors.size } }
        ui { vm.selectTool(Tool.Select); body?.let { vm.selectNode(it.id) } }
        // Dik ekranda yatay belge küçük kalır: seçili nesneye yaklaş.
        if (device.height > device.width) {
            ui {
                val box = vm.selectionBounds()
                if (box != null) {
                    val at = vm.viewport.toScreen(box.center)
                    vm.transformViewport(at, Offset(device.width / 2f - at.x, device.height * 0.42f - at.y), 1.9f)
                }
            }
        }
        shot("1-edit.png")

        // 2) Katmanlar
        ui { vm.toggleLayersPanel(); vm.toggleExpanded(vm.state.document.layers.first { it.name == "Gopher" }.id) }
        shot("2-layers.png")
        ui { vm.toggleLayersPanel(); vm.fitToScreen() }

        // 3) Baskı için CMYK renk seçimi
        overlay = { ColorPickerDialog(Rgba.rgb(0x3FA796).copy(ink = Ink.Cmyk(0.62, 0.0, 0.38, 0.05)), onDismiss = {}, onPick = {}) }
        shot("3-color.png")
        overlay = null

        // 4) Belgelerim
        ui { vm.showGallery() }
        shot("4-gallery.png")
        ui { vm.hideGallery() }
        ui { scene.close() }
    }
}
