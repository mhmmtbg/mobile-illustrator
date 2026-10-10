package io.github.mhmmtbg.mobileillustrator.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.mhmmtbg.mobileillustrator.editor.BundledFonts
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Sekmeler, belgeler arası pano, dışarıdan ekleme, katman panelinde çoklu seçim ve uygulamayla gelen fontlar. */
class TabsTest {
    private fun newRoot(): File = File(System.getProperty("java.io.tmpdir"), "mi-tabs-" + System.nanoTime()).apply { mkdirs() }

    private class Session(val io: DesktopDocumentIo, val vm: EditorViewModel) {
        fun ui(block: () -> Unit) = SwingUtilities.invokeAndWait(block)
        fun idle(what: String = "iş bitmeli") {
            val deadline = System.currentTimeMillis() + 60_000
            Thread.sleep(60)
            while (System.currentTimeMillis() < deadline && vm.state.busy != null) Thread.sleep(40)
            assertEquals(null, vm.state.busy, what)
        }
        fun drag(from: Vec2, to: Vec2) = ui {
            val a = vm.viewport.toScreen(from)
            val b = vm.viewport.toScreen(to)
            vm.onDrag(a, Offset((a.x + b.x) / 2, (a.y + b.y) / 2))
            vm.onDrag(a, b)
            vm.onDragEnd()
        }
        val objects: Int get() = vm.state.document.layers.sumOf { it.children.size }
    }

    private fun start(): Session {
        val io = DesktopDocumentIo(newRoot())
        lateinit var vm: EditorViewModel
        SwingUtilities.invokeAndWait {
            vm = EditorViewModel(io, CoroutineScope(SupervisorJob() + Dispatchers.Main))
            vm.onCanvasSize(Size(1000f, 800f))
        }
        return Session(io, vm).also { it.idle("açılış") }
    }

    @Test
    fun openingKeepsTheCurrentDocumentInItsTabAndTheClipboardCrossesDocuments() {
        val s = start()
        val vm = s.vm
        s.ui { vm.openSample() }
        s.idle()
        // Hiç dokunulmamış boş belgenin yerine geçilir: tek sekme.
        assertEquals(listOf("Gopher"), vm.tabs.map { it.name })
        val gopher = vm.state.session.id
        val license = vm.state.document.layers.first { it.name == "License" }
        s.ui { vm.selectLayerObjects(license.id); assertTrue(vm.copySelection()) }
        assertTrue(vm.canPaste)

        s.ui { vm.newDocument(800.0, 800.0) }
        s.idle()
        assertEquals(2, vm.tabs.size, "yeni belge ayrı sekmede açılmalı")
        assertTrue(vm.tabs[1].active)
        assertEquals(0, s.objects)
        s.ui { vm.paste() }
        assertEquals(license.children.size, s.objects)
        assertEquals(license.children.size, vm.state.selection.size)
        // Kopyalar yeni kimlik alır; özgün belgeyle hiçbir düğüm paylaşılmaz.
        assertTrue(vm.state.selection.none { id -> license.children.any { it.id == id } })
        val second = vm.state.session.id

        s.ui { vm.switchTab(gopher) }
        assertEquals("Gopher", vm.state.document.name)
        assertEquals(license.children.map { it.id }.toSet(), vm.state.selection, "sekmenin seçimi korunmalı")
        assertEquals(3, vm.state.document.layers.size)
        assertEquals(false, vm.state.history.canUndo)
        // Aynı belgeye yapıştırmak kopyayı kaydırarak ekler.
        s.ui { vm.switchTab(second); vm.undo(); vm.redo() }
        assertEquals(license.children.size, s.objects)

        s.ui { vm.closeTab(second) }
        assertEquals(listOf("Gopher"), vm.tabs.map { it.name })
        assertTrue(vm.tabs.single().active)
        s.ui { vm.flushAutosaveNow() }
        Thread.sleep(300)
        assertEquals(2, s.io.store.list().size, "kapatılan sekmenin belgesi depoda kalmalı")
        // Kapatılan belge galeriden yeniden açılınca yine ayrı sekmeye gelir.
        s.ui { vm.openStored(second) }
        s.idle()
        assertEquals(2, vm.tabs.size)
        assertEquals(license.children.size, s.objects)
        // Son sekme de kapanırsa boş bir belge kalır.
        s.ui { vm.closeTab(vm.state.session.id); vm.closeTab(vm.state.session.id) }
        assertEquals(1, vm.tabs.size)
        assertEquals(0, s.objects)
    }

    @Test
    fun layersPanelMultiSelectCopiesWholeLayers() {
        val s = start()
        val vm = s.vm
        s.ui { vm.openSample() }
        s.idle()
        val doc = vm.state.document
        val palette = doc.layers.first { it.name == "Pallette" }
        val a = palette.children[0].id
        val b = palette.children[1].id
        s.ui { vm.toggleMultiSelect(); vm.toggleNodeSelected(a); vm.toggleNodeSelected(b) }
        assertEquals(setOf(a, b), vm.state.selection)
        s.ui { vm.toggleNodeSelected(a) }
        assertEquals(setOf(b), vm.state.selection)
        val license = doc.layers.first { it.name == "License" }
        s.ui { vm.toggleLayerSelected(license.id) }
        assertEquals(setOf(license.id), vm.state.layerSelection)
        assertTrue(vm.state.selection.containsAll(license.children.map { it.id }))
        assertTrue(b in vm.state.selection)
        s.ui { vm.copySelection(); vm.newDocument(500.0, 500.0) }
        s.idle()
        s.ui { vm.paste() }
        // Katman, katman olarak; tek seçilen nesne etkin katmana eklenir.
        assertEquals(listOf("Katman 1", "License"), vm.state.document.layers.map { it.name })
        assertEquals(listOf(1, license.children.size), vm.state.document.layers.map { it.children.size })
        // Seçili katman silinince katmanla birlikte gider.
        s.ui { vm.toggleLayerSelected(vm.state.document.layers[1].id); vm.deleteSelection() }
        assertEquals(listOf("Katman 1"), vm.state.document.layers.map { it.name })
        assertEquals(emptySet(), vm.state.layerSelection)
    }

    @Test
    fun aFileCanBeAddedIntoTheOpenDocument() {
        val s = start()
        val vm = s.vm
        s.ui { vm.openSample() }
        s.idle()
        val before = vm.state.document
        val svg = File(newRoot(), "ek.svg")
        svg.writeText(
            """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><g id="Bant"><rect x="10" y="10" width="180" height="80" fill="#e5484d"/></g></svg>""",
        )
        s.ui { vm.importInto(svg.path) }
        s.idle()
        val after = vm.state.document
        assertEquals("Gopher", after.name)
        assertEquals(1, vm.tabs.size, "dışarıdan eklemek yeni sekme açmamalı")
        assertEquals(before.layers.size + 1, after.layers.size)
        assertEquals(before.layers, after.layers.take(before.layers.size), "var olan katmanlar değişmemeli")
        assertEquals(1, vm.state.selection.size)
        // Eklenen içerik çalışma yüzeyinin ortasına gelir.
        val box = vm.selectionBounds()!!
        val board = after.artboards.first().bounds
        assertEquals(board.center.x, box.center.x, 1.0)
        assertEquals(board.center.y, box.center.y, 1.0)
        s.ui { vm.undo() }
        assertEquals(before.layers.size, vm.state.document.layers.size)
    }

    @Test
    fun marqueeSelectsEverythingItTouchesAndExplainsLockedLayers() {
        val s = start()
        val vm = s.vm
        s.ui { vm.newDocument(1000.0, 1000.0) }
        s.idle()
        s.ui { vm.selectTool(Tool.Rectangle) }
        s.drag(Vec2(100.0, 100.0), Vec2(400.0, 400.0))
        s.ui { vm.selectTool(Tool.Rectangle) }
        s.drag(Vec2(600.0, 600.0), Vec2(900.0, 900.0))
        assertEquals(2, s.objects)
        s.ui { vm.selectTool(Tool.Select); vm.onTap(vm.viewport.toScreen(Vec2(500.0, 50.0))) }
        assertEquals(0, vm.state.selection.size)
        // Kutu ilk şeklin yalnızca köşesine değiyor: yine de seçilir. İkinci şekle değmiyor.
        s.drag(Vec2(20.0, 20.0), Vec2(130.0, 130.0))
        assertEquals(1, vm.state.selection.size)
        s.ui { vm.onTap(vm.viewport.toScreen(Vec2(500.0, 50.0))) }
        // Boş bir noktadan başlayan kutu iki şeklin de köşesine değiyor.
        s.drag(Vec2(390.0, 610.0), Vec2(610.0, 390.0))
        assertEquals(2, vm.state.selection.size)
        // Katman kilitliyse hiçbir şey seçilmez ama nedeni söylenir.
        s.ui { vm.toggleLayerLocked(vm.state.activeLayerId); vm.messageShown() }
        s.drag(Vec2(390.0, 610.0), Vec2(610.0, 390.0))
        assertEquals(0, vm.state.selection.size)
        assertNotNull(vm.state.message)
    }

    @Test
    fun bundledFontsLoadAndCoverTurkishLetters() {
        DesktopDocumentIo(newRoot())
        assertTrue(BundledFonts.names.size >= 40)
        assertEquals(BundledFonts.names.size, BundledFonts.names.toSet().size)
        for (name in BundledFonts.names) {
            val face = assertNotNull(DocumentRenderer.customFonts?.invoke(name), "font yüklenemedi: $name")
            for (ch in "ğĞşŞıİöÖüÜçÇ") assertTrue(face.getUTF32Glyph(ch.code).toInt() != 0, "$name fontunda '$ch' yok")
            assertNotNull(javaClass.classLoader.getResource("fonts/licenses/" + name.replace(" ", "") + ".txt"), "lisans metni eksik: $name")
        }
    }
}
