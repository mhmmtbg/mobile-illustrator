package io.github.mhmmtbg.mobileillustrator.ui

import io.github.mhmmtbg.mobileillustrator.model.trName
import io.github.mhmmtbg.mobileillustrator.model.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mhmmtbg.mobileillustrator.editor.EditorState
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.ExportFormat
import io.github.mhmmtbg.mobileillustrator.editor.PaintTarget
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.model.Align
import io.github.mhmmtbg.mobileillustrator.model.GradientSpec
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.render.BooleanOp
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.ZMove
import io.github.mhmmtbg.mobileillustrator.model.findNode
import io.github.mhmmtbg.mobileillustrator.model.anchorAt
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Swatches = listOf(
    0x000000, 0xFFFFFF, 0x8E8E93, 0xE5484D, 0xFF9A3C, 0xFFD43B,
    0x40C057, 0x3FA796, 0x3B9BFF, 0x3B6FE0, 0x7950F2, 0xE64980, 0x14213D, 0x8B5E34,
).map(Rgba::rgb)

private class ToolSpec(val tool: Tool, val icon: ImageVector, val label: String)

/** Etiketler kaynak dildedir; gösterilirken çevrilir (dil değişince liste yeniden kurulmaz). */
private val Tools = listOf(
    ToolSpec(Tool.Select, AppIcons.Select, "Seçim"),
    ToolSpec(Tool.Direct, AppIcons.Direct, "Doğrudan seçim"),
    ToolSpec(Tool.Pen, AppIcons.Pen, "Kalem"),
    ToolSpec(Tool.Pencil, AppIcons.Pencil, "Kurşun kalem"),
    ToolSpec(Tool.Rectangle, AppIcons.Rectangle, "Dikdörtgen"),
    ToolSpec(Tool.Ellipse, AppIcons.Ellipse, "Elips"),
    ToolSpec(Tool.Line, AppIcons.Line, "Çizgi"),
    ToolSpec(Tool.Text, AppIcons.Text, "Metin"),
    ToolSpec(Tool.Eyedropper, AppIcons.Eyedropper, "Damlalık"),
    ToolSpec(Tool.Hand, AppIcons.Hand, "Kaydır"),
)

@Composable
fun EditorScreen(vm: EditorViewModel, host: PlatformHost) {
    val state = vm.state
    var showNew by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showCoffee by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }

    fun openFile() = host.pickDocument(vm::open)
    fun export(format: ExportFormat) = host.pickSaveLocation(vm.suggestedFileName(format)) { vm.export(it, format) }
    fun saveAs() = host.pickSaveLocation(vm.suggestedFileName(ExportFormat.Ai), vm::saveAs)
    // Başka programın dosyası açıksa üzerine yazılmaz; ilk kayıtta yeni dosya adı sorulur.
    fun save() = if (vm.canSaveInPlace) vm.save() else saveAs()
    var renamingDocument by remember { mutableStateOf(false) }
    var showGradient by remember { mutableStateOf(false) }
    var showStrokeOptions by remember { mutableStateOf(false) }
    var showTransform by remember { mutableStateOf(false) }

    // Geri tuşu önce açık panelleri kapatır.
    host.BackHandler(enabled = state.gallery != null || state.layersOpen) {
        if (state.gallery != null) vm.hideGallery() else vm.toggleLayersPanel()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Panel)) {
        // Yatay telefonda araçlar yan şeride alınır; tuval yüksekliği korunur.
        // Tablette (geniş ekran) araçlar yan şeritte durur ve katman paneli tuvali örtmeden yana yerleşir.
        val wide = maxWidth >= 840.dp
        val rail = wide || (maxWidth > maxHeight && maxHeight < 560.dp)
        Column(Modifier.fillMaxSize()) {
            TopBar(
                vm,
                onNew = { showNew = true },
                onOpen = ::openFile,
                onPlaceImage = { host.pickImage(vm::placeImage) },
                onSave = ::save,
                onSaveAs = ::saveAs,
                onRename = { renamingDocument = true },
                onExport = ::export,
                onToggleLanguage = host::toggleLanguage,
                coffee = host.coffee,
                onCoffee = { showCoffee = true },
            )
            // Reklam şeridi: kahve ısmarlanınca kalkar. Belgelerim açıkken oradaki şerit görünür.
            if (state.gallery == null) host.AdBanner(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (rail) ToolRail(vm)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CanvasView(vm, Modifier.fillMaxSize())
                    if (state.layersOpen && !wide) {
                        LayersPanel(vm, Modifier.align(Alignment.TopEnd).fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(0.88f))
                    }
                    // Seçime göre değişen eylemler tuvalin alt kenarının üzerinde durur; belirip kaybolmaları
                    // tuvalin boyutunu (dolayısıyla görünümü) değiştirmez.
                    ContextBar(vm, Modifier.align(Alignment.BottomCenter), onRename = { renaming = it }, onTransform = { showTransform = true })
                    state.busy?.let { BusyOverlay(it) }
                    state.message?.let { msg ->
                        LaunchedEffect(msg) {
                            delay(2800)
                            vm.messageShown()
                        }
                        Text(
                            msg,
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xF0111113))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            color = AppColors.OnPanel,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (state.layersOpen && wide) LayersPanel(vm, Modifier.fillMaxHeight().width(340.dp))
            }
            HorizontalDivider(color = AppColors.Divider)
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(vertical = 4.dp),
            ) {
                PaintRow(vm, onCustomColor = { showPicker = true }, onGradient = { showGradient = true }, onStrokeOptions = { showStrokeOptions = true })
                if (!rail) ToolRow(vm)
            }
        }
    }

    state.gallery?.let { docs ->
        GalleryScreen(
            host = host,
            documents = docs,
            currentId = state.session.id,
            onOpen = vm::openStored,
            onDelete = vm::deleteStored,
            onNew = { vm.hideGallery(); showNew = true },
            onImport = { vm.hideGallery(); openFile() },
            onClose = vm::hideGallery,
        )
    }
    if (showGradient) {
        GradientDialog(vm.currentGradient() ?: GradientSpec.Default, onDismiss = { showGradient = false }) { showGradient = false; vm.applyGradient(it) }
    }
    if (showStrokeOptions) StrokeOptionsDialog(vm, onDismiss = { showStrokeOptions = false })
    if (showTransform) {
        val b = vm.selectionBounds()
        if (b == null) showTransform = false else TransformDialog(b, onDismiss = { showTransform = false }, onFlip = vm::flipSelection) { x, y, w, h, angle ->
            showTransform = false
            vm.transformSelection(x, y, w, h, angle)
        }
    }
    if (renamingDocument) {
        NameDialog(tr("Belgeyi adlandır"), state.history.present.name, onDismiss = { renamingDocument = false }) { renamingDocument = false; vm.renameDocument(it) }
    }
    if (showNew) NewDocumentDialog(onDismiss = { showNew = false }) { w, h -> showNew = false; vm.newDocument(w, h) }
    if (showPicker) {
        val current = if (state.paintTarget == PaintTarget.Stroke) state.stroke else state.fill
        ColorPickerDialog(current ?: Rgba.rgb(0x3B9BFF), onDismiss = { showPicker = false }) { showPicker = false; vm.setColor(it) }
    }
    renaming?.let { id ->
        val node = state.history.present.findNode(id)
        if (node == null) renaming = null else NameDialog(tr("Yeniden adlandır"), node.name, onDismiss = { renaming = null }) { renaming = null; vm.renameNode(id, it) }
    }
    state.textPrompt?.let {
        TextDialog(it, vm.fonts, onLoadFont = { host.pickFont(vm::importFont) }, onDismiss = vm::dismissTextPrompt, onConfirm = vm::confirmText)
    }
    if (state.warnings.isNotEmpty()) WarningsDialog(state.warnings, vm::dismissWarnings)
    state.crashReport?.let { report ->
        CrashDialog(
            onShare = {
                vm.dismissCrashReport()
                host.shareText(tr("Mobile Illustrator çökme raporu"), report)
            },
            onDismiss = vm::dismissCrashReport,
        )
    }
    if (showCoffee) {
        host.coffee?.let { CoffeeDialog(it, onDismiss = { showCoffee = false }) { showCoffee = false; host.buyCoffee() } } ?: run { showCoffee = false }
    }
}

@Composable
private fun BusyOverlay(label: String) {
    // Görünmez tıklama alanı: iş sürerken tuvale dokunuşlar geçmesin.
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = null, indication = null) {}, contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(AppColors.PanelRaised).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), color = AppColors.Accent, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(14.dp))
            Text(label, color = AppColors.OnPanel, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TopBar(
    vm: EditorViewModel,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onPlaceImage: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onRename: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onToggleLanguage: () -> Unit,
    coffee: CoffeeState?,
    onCoffee: () -> Unit,
) {
    val state = vm.state
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            BarButton(AppIcons.Menu, tr("Dosya menüsü")) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                @Composable
                fun item(label: String, action: () -> Unit) =
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                item(tr("Belgelerim")) { vm.showGallery() }
                item(tr("Yeni belge…"), onNew)
                item(tr("Aç… (.ai, .pdf, .svg)"), onOpen)
                item(tr("Örnek dosyayı aç")) { vm.openSample() }
                HorizontalDivider()
                item(tr("Kaydet"), onSave)
                item(tr("Farklı kaydet (.ai)"), onSaveAs)
                item(tr("PDF dışa aktar")) { onExport(ExportFormat.Pdf) }
                item(tr("SVG dışa aktar")) { onExport(ExportFormat.Svg) }
                item(tr("PNG dışa aktar")) { onExport(ExportFormat.Png) }
                HorizontalDivider()
                item(tr("Görsel yerleştir…"), onPlaceImage)
                item(tr("Belgeyi adlandır…"), onRename)
                item(tr("Tümünü seç")) { vm.selectAll() }
                item(if (state.snapping) tr("Yakalama: açık") else tr("Yakalama: kapalı")) { vm.toggleSnapping() }
                // Dil değiştirme: seçim kaydedilir ve ekran yeni dille yeniden kurulur.
                item(tr("Dil: Türkçe")) { onToggleLanguage() }
                if (coffee != null) {
                    HorizontalDivider()
                    item(if (coffee.purchased) tr("Kahve için teşekkürler ☕") else tr("Geliştiriciye kahve ısmarla ☕"), onCoffee)
                }
            }
        }
        // Bağlı dosyaya yazılmamış değişiklik varsa adın başında nokta görünür.
        Text(
            (if (state.session.unsaved) "● " else "") + trName(state.document.name),
            Modifier.padding(start = 4.dp).weight(1f).semantics {
                contentDescription = tr(if (state.session.unsaved) "Belge adı: %s, kaydedilmedi" else "Belge adı: %s", trName(state.document.name))
            },
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.OnPanel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "%${(vm.viewport.scale * 100).roundToInt()}",
            Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.OnPanelMuted,
        )
        BarButton(AppIcons.Fit, tr("Ekrana sığdır"), onClick = vm::fitToScreen)
        BarButton(AppIcons.Undo, tr("Geri al"), enabled = state.history.canUndo || state.pen != null, onClick = vm::undo)
        BarButton(AppIcons.Redo, tr("Yinele"), enabled = state.history.canRedo && state.pen == null, onClick = vm::redo)
        BarButton(AppIcons.Layers, tr("Katmanlar"), active = state.layersOpen, onClick = vm::toggleLayersPanel)
    }
}

@Composable
internal fun BarButton(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    // Etiket düğmenin kendisinde durur ki erişilebilirlik servisleri etkin/pasif durumunu doğru okusun.
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label; selected = active }) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> AppColors.OnPanelMuted.copy(alpha = 0.4f)
                active -> AppColors.Accent
                else -> AppColors.OnPanel
            },
        )
    }
}

/** Seçime ya da etkin araca göre değişen eylemler. Yapılacak bir şey yoksa yer kaplamaz. */
@Composable
private fun ContextBar(vm: EditorViewModel, modifier: Modifier, onRename: (String) -> Unit, onTransform: () -> Unit) {
    val state = vm.state
    val actions = ArrayList<Pair<String, () -> Unit>>()
    // Alt menüler: "Hizala" ve "Şekil" eylemleri aynı satırda açılır.
    var mode by remember { mutableStateOf(0) }
    if (state.selection.isEmpty() && mode != 0) mode = 0
    val doc = state.history.present
    val edit = state.nodeEdit
    when {
        state.pen != null -> {
            actions += tr("Bitir") to { vm.finishPen(close = false) }
            if (state.pen.anchors.size >= 3) actions += tr("Kapat ve bitir") to { vm.finishPen(close = true) }
            actions += tr("İptal") to vm::cancelPen
        }
        state.tool == Tool.Direct && edit?.anchor != null -> {
            val anchor = (doc.findNode(edit.nodeId) as? PathNode)?.subpaths?.anchorAt(edit.anchor)
            val curved = anchor != null && (anchor.handleIn != null || anchor.handleOut != null)
            actions += (if (curved) tr("Köşeye çevir") else tr("Yumuşat")) to vm::toggleSmoothAnchor
            actions += tr("Düğümü sil") to vm::deleteSelection
        }
        state.selection.isNotEmpty() && mode == 1 -> {
            actions += tr("‹ Geri") to { mode = 0 }
            actions += tr("Sola") to { vm.alignSelection(Align.Left) }
            actions += tr("Yatay ortala") to { vm.alignSelection(Align.CenterH) }
            actions += tr("Sağa") to { vm.alignSelection(Align.Right) }
            actions += tr("Üste") to { vm.alignSelection(Align.Top) }
            actions += tr("Dikey ortala") to { vm.alignSelection(Align.CenterV) }
            actions += tr("Alta") to { vm.alignSelection(Align.Bottom) }
            if (state.selection.size >= 3) {
                actions += tr("Yatay dağıt") to { vm.distributeSelection(true) }
                actions += tr("Dikey dağıt") to { vm.distributeSelection(false) }
            }
        }
        state.selection.isNotEmpty() && mode == 2 -> {
            actions += tr("‹ Geri") to { mode = 0 }
            actions += tr("Birleştir") to { mode = 0; vm.pathfinder(BooleanOp.Unite) }
            actions += tr("Öndekini çıkar") to { mode = 0; vm.pathfinder(BooleanOp.MinusFront) }
            actions += tr("Kesiştir") to { mode = 0; vm.pathfinder(BooleanOp.Intersect) }
            actions += tr("Dışla") to { mode = 0; vm.pathfinder(BooleanOp.Exclude) }
            actions += tr("Maske yap") to { mode = 0; vm.makeClippingMask() }
        }
        state.selection.isNotEmpty() -> {
            val nodes = state.selection.mapNotNull { doc.findNode(it) }
            val single = nodes.singleOrNull()
            actions += tr("Hizala") to { mode = 1 }
            if (nodes.size >= 2) actions += tr("Şekil") to { mode = 2 }
            actions += tr("Dönüştür") to onTransform
            if (single is GroupNode && single.clip != null) actions += tr("Maskeyi bırak") to vm::releaseClippingMask
            if (single is TextNode) {
                actions += tr("Metni düzenle") to vm::editSelectedText
                actions += tr("Yola çevir") to vm::outlineSelectedText
            }
            actions += tr("Çoğalt") to vm::duplicateSelection
            if (nodes.size >= 2) actions += tr("Grupla") to vm::groupSelection
            if (nodes.any { it is GroupNode }) actions += tr("Grubu çöz") to vm::ungroupSelection
            actions += tr("Öne") to { vm.reorderSelection(ZMove.Forward) }
            actions += tr("Arkaya") to { vm.reorderSelection(ZMove.Backward) }
            actions += tr("En öne") to { vm.reorderSelection(ZMove.Front) }
            actions += tr("En arkaya") to { vm.reorderSelection(ZMove.Back) }
            actions += tr("Katmanda göster") to vm::revealSelectionInLayers
            if (single != null) actions += tr("Adlandır") to { onRename(single.id) }
        }
    }
    if (actions.isEmpty()) return
    // Silme düğmesi satırın sonunda sabit durur: eylemler kaydırılsa da her zaman görünür.
    val canDelete = state.selection.isNotEmpty() && state.pen == null && !(state.tool == Tool.Direct && edit?.anchor != null)
    Row(
        modifier
            .fillMaxWidth()
            .background(AppColors.Panel.copy(alpha = 0.94f))
            // Çubuğa dokunuş alttaki tuvale geçmesin.
            .clickable(interactionSource = null, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Row(
        Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((label, action) in actions) {
            Text(
                label,
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.PanelRaised)
                    .clickable(role = Role.Button, onClick = action)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (label == tr("Düğümü sil")) Color(0xFFFF8A8A) else AppColors.OnPanel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
    if (canDelete) {
        IconButton(
            onClick = vm::deleteSelection,
            modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = tr("Sil") },
        ) {
            Icon(AppIcons.Delete, contentDescription = null, tint = Color(0xFFFF8A8A))
        }
    }
    }
}

@Composable
private fun PaintRow(vm: EditorViewModel, onCustomColor: () -> Unit, onGradient: () -> Unit, onStrokeOptions: () -> Unit) {
    val state = vm.state
    Column {
        if (state.paintTarget == PaintTarget.Stroke && state.stroke != null) {
            SliderRow(
                label = tr("Kontur kalınlığı"),
                valueText = "${formatWidth(state.strokeWidth)} pt",
                value = state.strokeWidth.toFloat(),
                range = 0.25f..80f,
                onChange = { vm.setStrokeWidth(snapWidth(it), done = false) },
                onDone = { vm.setStrokeWidth(vm.state.strokeWidth, done = true) },
                trailing = tr("Seçenekler") to onStrokeOptions,
            )
        }
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PaintTargetChip(tr("Dolgu"), state.fill, state.paintTarget == PaintTarget.Fill) { vm.selectPaintTarget(PaintTarget.Fill) }
            Spacer(Modifier.width(4.dp))
            PaintTargetChip(tr("Kontur"), state.stroke, state.paintTarget == PaintTarget.Stroke) { vm.selectPaintTarget(PaintTarget.Stroke) }
            Spacer(Modifier.width(4.dp))
            OpacityChip(state, state.paintTarget == PaintTarget.Opacity) { vm.selectPaintTarget(PaintTarget.Opacity) }
            Spacer(Modifier.width(6.dp))
            if (state.paintTarget == PaintTarget.Opacity) {
                Slider(
                    value = state.opacity.toFloat(),
                    onValueChange = { vm.setOpacity((it * 100).roundToInt() / 100.0, done = false) },
                    onValueChangeFinished = { vm.setOpacity(vm.state.opacity, done = true) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(end = 16.dp).semantics { contentDescription = tr("Opaklık") },
                )
            } else {
                val current = if (state.paintTarget == PaintTarget.Fill) state.fill else state.stroke
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwatchButton(null, current == null, tr("Renk yok")) { vm.setColor(null) }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onCustomColor)
                            .semantics { contentDescription = tr("Özel renk") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Add, null, Modifier.size(26.dp).border(1.5.dp, AppColors.OnPanelMuted, CircleShape).padding(3.dp), tint = AppColors.OnPanel)
                    }
                    Text(
                        tr("Gradyan"),
                        Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.PanelRaised)
                            .clickable(role = Role.Button, onClick = onGradient)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        color = AppColors.OnPanel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    for (c in Swatches) {
                        SwatchButton(c, current == c, "#%06X".format(c.toArgb() and 0xFFFFFF)) { vm.setColor(c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit,
    trailing: Pair<String, () -> Unit>? = null,
) {
    Row(Modifier.padding(horizontal = 16.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(valueText, Modifier.width(60.dp), style = MaterialTheme.typography.labelMedium, color = AppColors.OnPanelMuted)
        // Kalınlıkta ince değerler daha çok kullanılır: kaydırıcı karekök ölçeğinde ilerler.
        val lo = kotlin.math.sqrt(range.start)
        val hi = kotlin.math.sqrt(range.endInclusive)
        Slider(
            value = kotlin.math.sqrt(value.coerceIn(range.start, range.endInclusive)),
            onValueChange = { onChange(it * it) },
            onValueChangeFinished = onDone,
            valueRange = lo..hi,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        trailing?.let { (text, action) ->
            Text(
                text,
                Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.PanelRaised)
                    .clickable(role = Role.Button, onClick = action)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = AppColors.OnPanel,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun snapWidth(v: Float): Double = if (v < 2f) (v * 4).roundToInt() / 4.0 else (v * 2).roundToInt() / 2.0

private fun formatWidth(w: Double): String {
    val r = (w * 100).roundToInt() / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

@Composable
private fun ToolRow(vm: EditorViewModel) {
    val active = vm.state.tool
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        for (t in Tools) ToolButton(t, active == t.tool, Modifier.weight(1f).height(46.dp)) { vm.selectTool(t.tool) }
    }
}

@Composable
private fun ToolRail(vm: EditorViewModel) {
    val active = vm.state.tool
    Column(
        Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            .width(56.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (t in Tools) ToolButton(t, active == t.tool, Modifier.size(width = 48.dp, height = 44.dp)) { vm.selectTool(t.tool) }
    }
}

@Composable
private fun ToolButton(spec: ToolSpec, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) AppColors.Accent else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = tr(spec.label); selected = active },
        contentAlignment = Alignment.Center,
    ) {
        Icon(spec.icon, contentDescription = null, tint = if (active) Color(0xFF2B1A0E) else AppColors.OnPanel)
    }
}

@Composable
private fun PaintTargetChip(label: String, color: Rgba?, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) AppColors.PanelRaised else Color.Transparent)
            .border(1.dp, if (active) AppColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.OnPanel)
    }
}

@Composable
private fun OpacityChip(state: EditorState, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) AppColors.PanelRaised else Color.Transparent)
            .border(1.dp, if (active) AppColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = tr("Opaklık ayarı"); selected = active }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("%${(state.opacity * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = AppColors.OnPanel)
    }
}

@Composable
private fun SwatchButton(color: Rgba?, active: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label; selected = active },
        contentAlignment = Alignment.Center,
    ) {
        if (active) Box(Modifier.size(36.dp).border(2.dp, AppColors.Accent, CircleShape))
        ColorDot(color, Modifier.size(26.dp))
    }
}

/** Renk yoksa Illustrator'daki gibi üzeri kırmızı çizgili beyaz daire. */
@Composable
internal fun ColorDot(color: Rgba?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color?.let { Color(it.copy(a = 1.0).toArgb()) } ?: Color.White)
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
            .drawBehind {
                if (color == null) {
                    drawLine(Color(0xFFE5484D), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                }
            },
    )
}
