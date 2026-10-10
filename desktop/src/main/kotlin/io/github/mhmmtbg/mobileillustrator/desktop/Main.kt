package io.github.mhmmtbg.mobileillustrator.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.ExportFormat
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.ui.AppTheme
import io.github.mhmmtbg.mobileillustrator.ui.EditorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Frame
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Windows (ve diğer masaüstü) sürümünün girişi. Ekranlar Android sürümüyle aynıdır; burada pencere,
 * klavye kısayolları ve çökme kaydı kurulur.
 *
 * Komut satırında bir dosya yolu verilirse ("Birlikte aç") o dosya açılır.
 */
fun main(args: Array<String>) {
    val root = DesktopDocumentIo.defaultRoot()
    val io = DesktopDocumentIo(root)
    DesktopHost.applyLanguage(root)
    installCrashLog(io.crashFile)

    // "--self-test <dosya.png>": paketlenmiş uygulamanın gerçekten çalıştığını denetler (CI). Pencere açılır,
    // örnek dosya yüklenir, PNG olarak dışa aktarılır ve uygulama kapanır.
    val selfTest = if (args.firstOrNull() == "--self-test") File(args.getOrElse(1) { "self-test.png" }) else null

    application {
        var frame: Frame? = null
        val host = remember { DesktopHost(root) { frame } }
        val scope = rememberCoroutineScope()
        val vm = remember {
            EditorViewModel(io, scope).also { vm -> args.firstOrNull()?.takeIf { File(it).isFile }?.let(vm::open) }
        }
        fun save() {
            if (vm.canSaveInPlace) vm.save() else host.pickSaveLocation(vm.suggestedFileName(ExportFormat.Ai), vm::saveAs)
        }
        Window(
            onCloseRequest = {
                vm.flushAutosaveNow()
                exitApplication()
            },
            title = "Mobile Illustrator",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
            icon = androidx.compose.ui.res.painterResource("icon.png"),
            onKeyEvent = { event -> handleShortcut(event, vm, host, ::save) },
        ) {
            frame = window
            if (selfTest != null) {
                LaunchedEffect(Unit) {
                    delay(1500)
                    vm.openSample()
                    withTimeout(60_000) {
                        while (vm.state.busy != null || vm.state.document.layers.none { it.children.isNotEmpty() }) delay(100)
                    }
                    val bytes = withContext(Dispatchers.Default) { io.encode(vm.state.document, ExportFormat.Png) }
                    selfTest.writeBytes(bytes)
                    println("self-test: ${vm.state.document.layers.size} katman, ${bytes.size} bayt PNG")
                    exitApplication()
                }
            }
            key(host.languageVersion) {
                AppTheme {
                    EditorScreen(vm, host)
                }
            }
        }
    }
}

/** Klavye kısayolları. Metin alanı odaktaysa tuşlar önce ona gider; buraya yalnızca kullanılmayanlar ulaşır. */
private fun handleShortcut(event: KeyEvent, vm: EditorViewModel, host: DesktopHost, save: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    if (command) {
        when (event.key) {
            Key.Z -> if (event.isShiftPressed) vm.redo() else vm.undo()
            Key.Y -> vm.redo()
            Key.S -> save()
            Key.O -> host.pickDocument(vm::open)
            Key.A -> vm.selectAll()
            Key.D -> vm.duplicateSelection()
            Key.C -> vm.copySelection()
            Key.X -> vm.cutSelection()
            Key.V -> vm.paste()
            Key.W -> vm.closeTab(vm.state.session.id)
            Key.Tab -> vm.tabs.let { tabs ->
                // Ctrl+Tab sonraki, Ctrl+Shift+Tab önceki sekmeye geçer.
                val i = tabs.indexOfFirst { it.active }
                if (tabs.size > 1 && i >= 0) vm.switchTab(tabs[(i + (if (event.isShiftPressed) tabs.size - 1 else 1)) % tabs.size].id)
            }
            Key.I -> if (event.isShiftPressed) host.pickDocument(vm::importInto) else return false
            Key.G -> if (event.isShiftPressed) vm.ungroupSelection() else vm.groupSelection()
            Key.Zero -> vm.fitToScreen()
            else -> return false
        }
        return true
    }
    when (event.key) {
        Key.Delete, Key.Backspace -> vm.deleteSelection()
        Key.Escape -> host.onBack?.invoke() ?: run { if (vm.state.pen != null) vm.cancelPen() }
        Key.Enter -> if (vm.state.pen != null) vm.finishPen() else return false
        Key.V -> vm.selectTool(Tool.Select)
        Key.A -> vm.selectTool(Tool.Direct)
        Key.P -> vm.selectTool(Tool.Pen)
        Key.N -> vm.selectTool(Tool.Pencil)
        Key.M -> vm.selectTool(Tool.Rectangle)
        Key.L -> vm.selectTool(Tool.Ellipse)
        Key.T -> vm.selectTool(Tool.Text)
        Key.I -> vm.selectTool(Tool.Eyedropper)
        Key.H -> vm.selectTool(Tool.Hand)
        else -> return false
    }
    return true
}

/** Bir hata uygulamayı düşürürse ayrıntısı dosyaya yazılır; bir sonraki açılışta gösterilir. Hiçbir yere gönderilmez. */
private fun installCrashLog(file: File) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        try {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            file.writeText(
                "Mobile Illustrator (masaüstü)\n${System.getProperty("os.name")} ${System.getProperty("os.version")}, Java ${System.getProperty("java.version")}\n" +
                    "İş parçacığı: ${thread.name}\n\n$trace",
            )
        } catch (e: Throwable) {
            // Kayıt yazılamasa da çökme olağan yoluna devam etmeli.
        }
        previous?.uncaughtException(thread, error)
    }
}
