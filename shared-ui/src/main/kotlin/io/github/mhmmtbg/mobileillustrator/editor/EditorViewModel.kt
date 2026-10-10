package io.github.mhmmtbg.mobileillustrator.editor

import io.github.mhmmtbg.mobileillustrator.model.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.mhmmtbg.mobileillustrator.model.Align
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.GradientSpec
import io.github.mhmmtbg.mobileillustrator.model.Snap
import io.github.mhmmtbg.mobileillustrator.model.SnapResult
import io.github.mhmmtbg.mobileillustrator.model.align
import io.github.mhmmtbg.mobileillustrator.model.distribute
import io.github.mhmmtbg.mobileillustrator.model.makeClippingMask
import io.github.mhmmtbg.mobileillustrator.model.releaseClippingMask
import io.github.mhmmtbg.mobileillustrator.model.withGradient
import io.github.mhmmtbg.mobileillustrator.model.bounds
import io.github.mhmmtbg.mobileillustrator.render.BooleanOp
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import io.github.mhmmtbg.mobileillustrator.render.PathBoolean
import io.github.mhmmtbg.mobileillustrator.model.AnchorRef
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.History
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathFit
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.TextWarp
import io.github.mhmmtbg.mobileillustrator.model.WarpStyle
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.ZMove
import io.github.mhmmtbg.mobileillustrator.model.addLayer
import io.github.mhmmtbg.mobileillustrator.model.addNode
import io.github.mhmmtbg.mobileillustrator.model.anchorAt
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.model.boundsOf
import io.github.mhmmtbg.mobileillustrator.model.Clip
import io.github.mhmmtbg.mobileillustrator.model.Pasted
import io.github.mhmmtbg.mobileillustrator.model.clip
import io.github.mhmmtbg.mobileillustrator.model.paste
import io.github.mhmmtbg.mobileillustrator.model.merged
import io.github.mhmmtbg.mobileillustrator.model.lockedNodesIn
import io.github.mhmmtbg.mobileillustrator.model.colorGroups
import io.github.mhmmtbg.mobileillustrator.model.recolored
import io.github.mhmmtbg.mobileillustrator.model.LegendOptions
import io.github.mhmmtbg.mobileillustrator.model.colorCodesText
import io.github.mhmmtbg.mobileillustrator.model.withColorLegend
import io.github.mhmmtbg.mobileillustrator.model.legendOptions
import io.github.mhmmtbg.mobileillustrator.model.deleteAnchor
import io.github.mhmmtbg.mobileillustrator.model.duplicate
import io.github.mhmmtbg.mobileillustrator.model.findLayer
import io.github.mhmmtbg.mobileillustrator.model.findNode
import io.github.mhmmtbg.mobileillustrator.model.group
import io.github.mhmmtbg.mobileillustrator.model.hitTest
import io.github.mhmmtbg.mobileillustrator.model.hitTestDeep
import io.github.mhmmtbg.mobileillustrator.model.insertAnchor
import io.github.mhmmtbg.mobileillustrator.model.moveLayer
import io.github.mhmmtbg.mobileillustrator.model.moveNodesToLayer
import io.github.mhmmtbg.mobileillustrator.model.movedBy
import io.github.mhmmtbg.mobileillustrator.model.nearest
import io.github.mhmmtbg.mobileillustrator.model.newId
import io.github.mhmmtbg.mobileillustrator.model.nextLayerName
import io.github.mhmmtbg.mobileillustrator.model.nodesIn
import io.github.mhmmtbg.mobileillustrator.model.parentMatrix
import io.github.mhmmtbg.mobileillustrator.model.removeLayer
import io.github.mhmmtbg.mobileillustrator.model.removeNode
import io.github.mhmmtbg.mobileillustrator.model.reorderNode
import io.github.mhmmtbg.mobileillustrator.model.replaceAnchor
import io.github.mhmmtbg.mobileillustrator.model.restyled
import io.github.mhmmtbg.mobileillustrator.model.styleSample
import io.github.mhmmtbg.mobileillustrator.model.toggleSmooth
import io.github.mhmmtbg.mobileillustrator.model.transformNodes
import io.github.mhmmtbg.mobileillustrator.model.transformedBy
import io.github.mhmmtbg.mobileillustrator.model.ungroup
import io.github.mhmmtbg.mobileillustrator.model.updateLayer
import io.github.mhmmtbg.mobileillustrator.model.updateNode
import io.github.mhmmtbg.mobileillustrator.model.withHandle
import io.github.mhmmtbg.mobileillustrator.model.withLocked
import io.github.mhmmtbg.mobileillustrator.model.withName
import io.github.mhmmtbg.mobileillustrator.model.withOpacity
import io.github.mhmmtbg.mobileillustrator.model.withVisible
import io.github.mhmmtbg.mobileillustrator.render.TextOutliner
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import io.github.mhmmtbg.mobileillustrator.model.FillRule
import io.github.mhmmtbg.mobileillustrator.model.ImageData
import io.github.mhmmtbg.mobileillustrator.model.layerOf
import io.github.mhmmtbg.mobileillustrator.trace.PixelImage
import io.github.mhmmtbg.mobileillustrator.trace.TraceCancelled
import io.github.mhmmtbg.mobileillustrator.trace.TraceOptions
import io.github.mhmmtbg.mobileillustrator.trace.TraceResult
import io.github.mhmmtbg.mobileillustrator.trace.Tracer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Düzenleyicinin durumu ve işlemleri. Platformdan bağımsızdır: dosya işleri [io] üzerinden yapılır,
 * eşzamanlı işler [scope] içinde (ana iş parçacığında) başlatılır.
 */
class EditorViewModel(private val io: DocumentIo, private val scope: CoroutineScope) {

    var state by mutableStateOf(freshState(Document.blank(), DocSession(io.store.newId())))
        private set

    var viewport by mutableStateOf(Viewport())
        private set

    /** Parmak için ekranda sabit kalan dokunma payı (piksel); arayüz yoğunluğa göre ayarlar. */
    var touchTolerancePx: Float = 24f

    /** Seçim aracıyla taşınan, ölçeklenen ya da döndürülen üst düzey nesneler; tuval bunları ayrı çizer. */
    var movingIds by mutableStateOf<Set<String>>(emptySet())
        private set

    private var canvasSize = Size.Zero
    private var fitPending = true

    // ---- Sekmeler -----------------------------------------------------------

    /** Arka sekmede bekleyen belge: düzenleyici durumu ve görünümüyle birlikte saklanır. */
    private class Parked(val state: EditorState, val viewport: Viewport, val fitted: Boolean)

    private val parked = HashMap<String, Parked>()

    /** Açık belgelerin sekme sırası (belge kimlikleri); etkin belge de içindedir. */
    private var tabOrder by mutableStateOf(listOf(state.session.id))

    /** Sekme şeridi için açık belgeler. */
    val tabs: List<TabInfo>
        get() {
            val current = state
            return tabOrder.mapNotNull { id ->
                val s = if (id == current.session.id) current else parked[id]?.state ?: return@mapNotNull null
                TabInfo(id, s.history.present.name, id == current.session.id, s.session.unsaved)
            }
        }

    /** Görünüm "ekrana sığdır" durumunda mı (kullanıcı kaydırıp yakınlaştırmadıysa). */
    private var fitted = false
    private var drag: Drag? = null
    private var autosaveJob: Job? = null

    private sealed interface Drag {
        data class Pan(val last: Offset) : Drag
        data class Move(val ids: Set<String>, val start: Vec2, val bounds: Rect? = null, val targets: List<Rect> = emptyList()) : Drag
        data class Scale(val ids: Set<String>, val bounds: Rect, val handle: Handle) : Drag
        data class Rotate(val ids: Set<String>, val center: Vec2, val startAngle: Double) : Drag
        data class Marquee(val start: Vec2) : Drag
        data class Create(val start: Vec2, val nodeId: String, val layerId: String, val targets: List<Rect> = emptyList()) : Drag
        data class Pencil(val points: ArrayList<Vec2>, val nodeId: String, val layerId: String) : Drag
        data object PenHandle : Drag
        data class AnchorDrag(val nodeId: String, val ref: AnchorRef, val toLocal: Matrix, val origin: List<SubPath>, val start: Vec2) : Drag
        data class HandleDrag(val nodeId: String, val ref: AnchorRef, val outgoing: Boolean, val toLocal: Matrix, val origin: List<SubPath>) : Drag
    }

    /** Yüklenmiş fontların adları (metin penceresinde listelenir). */
    var fonts by mutableStateOf<List<String>>(emptyList())
        private set

    /** Cihazın hazır fontları (görünen ad, aile adı). */
    var systemFonts by mutableStateOf(listOf("Sans" to "sans-serif", "Serif" to "serif", "Mono" to "monospace"))
        private set

    init {
        scope.launch {
            fonts = withContext(Dispatchers.Default) { try { io.fonts.list() } catch (e: Throwable) { emptyList() } }
            systemFonts = withContext(Dispatchers.Default) { try { io.systemFonts() } catch (e: Throwable) { systemFonts } }
        }
        restoreLastDocument()
        // Önceki açılış çökmeyle bittiyse kaydı göster ve dosyayı sil (bir kez gösterilir).
        scope.launch {
            val report = withContext(Dispatchers.Default) {
                try { io.takeCrashReport() } catch (e: Throwable) { null }
            }
            if (report != null) state = state.copy(crashReport = report)
        }
    }

    fun dismissCrashReport() {
        state = state.copy(crashReport = null)
    }

    fun importFont(uri: String) = background(tr("Font yükleniyor…")) {
        val name = io.importFont(uri)
        val list = io.fonts.list()
        withContext(Dispatchers.Main) {
            fonts = list
            // Açık metin penceresi varsa yeni font seçili gelsin.
            state = state.copy(textPrompt = state.textPrompt?.copy(fontFamily = DocumentRenderer.CUSTOM_FONT_PREFIX + name), message = tr("\"%s\" yüklendi", name))
        }
    }

    private fun freshState(doc: Document, session: DocSession) =
        EditorState(history = History(present = doc), activeLayerId = doc.layers.last().id, session = session)

    private val present: Document get() = state.history.present

    // ---- Görünüm ----------------------------------------------------------

    fun onCanvasSize(size: Size) {
        val old = canvasSize
        canvasSize = size
        if (size.width <= 0 || size.height <= 0) return
        if (fitPending) {
            fitPending = false
            fitToScreen()
        } else if (old.width > 0 && old != size && fitted) {
            // Belge ekrana sığdırılmış duruyordu (kullanıcı yakınlaştırmadı): yeni boyuta yeniden sığdır.
            fitToScreen()
        } else if (old.width > 0 && old != size) {
            // Ekran döndü ya da panel açıldı: görünen merkez yerinde kalsın.
            val center = viewport.toDocument(Offset(old.width / 2, old.height / 2))
            viewport = viewport.copy(
                offset = Offset(size.width / 2 - center.x.toFloat() * viewport.scale, size.height / 2 - center.y.toFloat() * viewport.scale),
            )
        }
    }

    fun fitToScreen() {
        val bounds = state.document.artboardBounds() ?: return
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || bounds.width <= 0 || bounds.height <= 0) {
            fitPending = true
            return
        }
        val scale = (min(canvasSize.width / bounds.width.toFloat(), canvasSize.height / bounds.height.toFloat()) * 0.92f)
            .coerceIn(Viewport.MIN_SCALE, Viewport.MAX_SCALE)
        val c = bounds.center
        fitted = true
        viewport = Viewport(scale, Offset(canvasSize.width / 2 - c.x.toFloat() * scale, canvasSize.height / 2 - c.y.toFloat() * scale))
    }

    /** İki parmak hareketi: [centroid] etrafında [zoom] kadar yakınlaştırır, [pan] kadar kaydırır. */
    fun transformViewport(centroid: Offset, pan: Offset, zoom: Float) {
        fitted = false
        val v = viewport
        val newScale = (v.scale * zoom).coerceIn(Viewport.MIN_SCALE, Viewport.MAX_SCALE)
        val k = newScale / v.scale
        viewport = Viewport(newScale, centroid - (centroid - v.offset) * k + pan)
    }

    private fun tolerance(): Double = touchTolerancePx / viewport.scale.toDouble()

    // ---- Geçmiş -----------------------------------------------------------

    private fun commit(doc: Document, then: (EditorState) -> EditorState = { it }) {
        val changed = doc != present
        var next = state.copy(history = state.history.push(doc), preview = null)
        // Bağlı bir dosya varsa bu değişiklik henüz ona yazılmadı.
        if (changed && next.session.linkUri != null && !next.session.unsaved) next = next.copy(session = next.session.copy(unsaved = true))
        state = then(next)
        if (changed) scheduleAutosave()
    }

    fun undo() {
        val pen = state.pen
        if (pen != null) {
            // Kalemle çizerken geri al, son düğümü kaldırır.
            if (pen.anchors.size <= 1) cancelPen() else setPen(pen.copy(anchors = pen.anchors.dropLast(1)))
            return
        }
        replaceHistory(state.history.undo())
    }

    fun redo() {
        if (state.pen != null) return
        replaceHistory(state.history.redo())
    }

    private fun replaceHistory(h: History<Document>) {
        onDragCancel()
        val doc = h.present
        state = state.copy(
            history = h,
            preview = null,
            selection = state.selection.filterTo(HashSet()) { doc.findNode(it) != null },
            layerSelection = state.layerSelection.filterTo(HashSet()) { doc.findLayer(it) != null },
            nodeEdit = state.nodeEdit?.takeIf { doc.findNode(it.nodeId) is PathNode }?.copy(anchor = null),
            activeLayerId = if (doc.findLayer(state.activeLayerId) != null) state.activeLayerId else doc.layers.last().id,
            session = if (state.session.linkUri != null) state.session.copy(unsaved = true) else state.session,
        )
        scheduleAutosave()
    }

    // ---- İşaretçi ---------------------------------------------------------

    fun onTap(screen: Offset) {
        val p = viewport.toDocument(screen)
        when (state.tool) {
            Tool.Select -> {
                val hit = present.hitTest(p, tolerance())
                state = state.withSelection(hit?.let { setOf(it.id) } ?: emptySet())
            }
            Tool.Direct -> directTap(p)
            Tool.Pen -> penTap(p)
            Tool.Text -> state = state.copy(
                textPrompt = TextPrompt(nodeId = null, position = p, fontSize = defaultFontSize()),
            )
            Tool.Eyedropper -> eyedrop(p)
            else -> {}
        }
    }

    private fun defaultFontSize(): Double {
        val h = present.artboardBounds()?.height ?: 1000.0
        return (h / 18).coerceIn(8.0, 400.0).let { Math.round(it).toDouble() }
    }

    fun onDrag(startScreen: Offset, currentScreen: Offset) {
        val d = drag ?: beginDrag(startScreen).also {
            drag = it
            movingIds = when (it) {
                is Drag.Move -> it.ids
                is Drag.Scale -> it.ids
                is Drag.Rotate -> it.ids
                else -> emptySet()
            }
        }
        val base = present
        val now = viewport.toDocument(currentScreen)
        when (d) {
            is Drag.Pan -> {
                fitted = false
                viewport = viewport.copy(offset = viewport.offset + (currentScreen - d.last))
                drag = d.copy(last = currentScreen)
            }
            is Drag.Move -> {
                var delta = now - d.start
                var guides: SnapResult? = null
                if (d.bounds != null && d.targets.isNotEmpty()) {
                    val snap = Snap.box(d.bounds.translate(delta.x, delta.y), d.targets, snapThreshold())
                    delta = Vec2(delta.x + snap.dx, delta.y + snap.dy)
                    guides = snap.takeIf { it.guidesX.isNotEmpty() || it.guidesY.isNotEmpty() }
                }
                state = state.copy(preview = base.transformNodes(d.ids, Matrix.translate(delta.x, delta.y)), guides = guides)
            }
            is Drag.Scale -> state = state.copy(preview = base.transformNodes(d.ids, scaleMatrix(d, now)))
            is Drag.Rotate -> {
                val angle = atan2(now.y - d.center.y, now.x - d.center.x) - d.startAngle
                val m = Matrix.translate(d.center.x, d.center.y) * Matrix.rotate(angle) * Matrix.translate(-d.center.x, -d.center.y)
                state = state.copy(preview = base.transformNodes(d.ids, m))
            }
            is Drag.Marquee -> state = state.copy(marquee = Rect.of(d.start, now))
            is Drag.Create -> {
                var end = now
                var guides: SnapResult? = null
                if (d.targets.isNotEmpty()) {
                    val snap = Snap.point(now, d.targets, snapThreshold())
                    end = Vec2(now.x + snap.dx, now.y + snap.dy)
                    guides = snap.takeIf { it.guidesX.isNotEmpty() || it.guidesY.isNotEmpty() }
                }
                state = state.copy(preview = base.addNode(d.layerId, shapeNode(d, end)), guides = guides)
            }
            is Drag.Pencil -> {
                val last = d.points.last()
                if (last.distanceTo(now) * viewport.scale > 1.5) d.points += now
                val line = PathNode(
                    id = d.nodeId,
                    subpaths = listOf(SubPath(d.points.map { Anchor(it) })),
                    stroke = freehandStroke(),
                )
                state = state.copy(preview = base.addNode(d.layerId, line))
            }
            Drag.PenHandle -> {
                val pen = state.pen ?: return
                val a = pen.anchors.last()
                val mirror = Vec2(2 * a.point.x - now.x, 2 * a.point.y - now.y)
                setPen(pen.copy(anchors = pen.anchors.dropLast(1) + Anchor(a.point, handleIn = mirror, handleOut = now)))
            }
            is Drag.AnchorDrag -> {
                val delta = d.toLocal.apply(now) - d.toLocal.apply(d.start)
                val original = d.origin.anchorAt(d.ref) ?: return
                val moved = d.origin.replaceAnchor(d.ref, original.movedBy(delta))
                state = state.copy(preview = base.updateNode(d.nodeId) { (it as? PathNode)?.copy(subpaths = moved) ?: it })
            }
            is Drag.HandleDrag -> {
                val original = d.origin.anchorAt(d.ref) ?: return
                val moved = d.origin.replaceAnchor(d.ref, original.withHandle(d.outgoing, d.toLocal.apply(now)))
                state = state.copy(preview = base.updateNode(d.nodeId) { (it as? PathNode)?.copy(subpaths = moved) ?: it })
            }
        }
    }

    fun onDragEnd() {
        val d = drag
        drag = null
        if (movingIds.isNotEmpty()) movingIds = emptySet()
        if (state.guides != null) state = state.copy(guides = null)
        when (d) {
            is Drag.Marquee -> {
                val rect = state.marquee
                state = state.copy(marquee = null)
                if (rect != null) {
                    // Kutunun dokunduğu (tamamı içinde olmasa da) bütün nesneler seçilir.
                    val ids = present.nodesIn(rect).mapTo(HashSet()) { it.id }
                    state = state.withSelection(ids)
                    if (ids.isEmpty()) {
                        val lockedCount = present.lockedNodesIn(rect)
                        if (lockedCount > 0) state = state.copy(message = tr("Kutudaki %s nesne kilitli; katman panelinden kilidi açın", lockedCount))
                    }
                }
            }
            is Drag.Create -> state.preview?.let { p -> commit(p) { it.withSelection(setOf(d.nodeId)) } }
            is Drag.Pencil -> {
                state = state.copy(preview = null)
                val sp = PathFit.fit(d.points, 2.0 / viewport.scale) ?: return
                val node = PathNode(id = d.nodeId, subpaths = listOf(sp), stroke = freehandStroke())
                commit(present.addNode(d.layerId, node)) { it.withSelection(setOf(d.nodeId)) }
            }
            Drag.PenHandle, is Drag.Pan, null -> {}
            else -> state.preview?.let { commit(it) }
        }
    }

    /** İkinci parmak indi ya da hareket iptal edildi: yarım kalan işlem atılır. */
    fun onDragCancel() {
        val d = drag
        drag = null
        if (movingIds.isNotEmpty()) movingIds = emptySet()
        if (state.marquee != null || state.guides != null) state = state.copy(marquee = null, guides = null)
        if (d == null || d == Drag.PenHandle || state.pen != null) return
        if (state.preview != null) state = state.copy(preview = null)
    }

    private fun beginDrag(startScreen: Offset): Drag {
        val start = viewport.toDocument(startScreen)
        val doc = present
        val tol = tolerance()
        when (state.tool) {
            Tool.Select -> {
                selectionHandleAt(startScreen)?.let { return it }
                val hit = doc.hitTest(start, tol) ?: return Drag.Marquee(start)
                val ids = if (hit.id in state.selection) state.selection else setOf(hit.id)
                if (ids != state.selection) state = state.withSelection(ids)
                return Drag.Move(ids, start, doc.boundsOf(ids), snapTargets(ids))
            }
            Tool.Direct -> return beginDirectDrag(start, startScreen)
            Tool.Pen -> {
                penTap(start)
                return if (state.pen != null) Drag.PenHandle else Drag.Pan(startScreen)
            }
            Tool.Text -> return Drag.Pan(startScreen)
            Tool.Pencil, Tool.Rectangle, Tool.Ellipse, Tool.Line -> {
                val layer = drawingLayer() ?: run {
                    state = state.copy(message = tr("Etkin katman kilitli ya da gizli"))
                    return Drag.Pan(startScreen)
                }
                if (state.tool == Tool.Pencil) return Drag.Pencil(arrayListOf(start), newId(), layer.id)
                val targets = snapTargets(emptySet())
                val snap = if (targets.isEmpty()) null else Snap.point(start, targets, snapThreshold())
                return Drag.Create(if (snap == null) start else Vec2(start.x + snap.dx, start.y + snap.dy), newId(), layer.id, targets)
            }
            Tool.Eyedropper, Tool.Hand -> return Drag.Pan(startScreen)
        }
    }

    private fun snapThreshold(): Double = touchTolerancePx * 0.45 / viewport.scale

    /**
     * Yapışılabilecek kutular: çalışma yüzeyleri ve taşınmayan üst düzey nesneler. Çok kalabalık belgelerde
     * yalnızca çalışma yüzeyleri kullanılır; hem hız hem de her yere yapışıp durmaması için.
     */
    private fun snapTargets(moving: Set<String>): List<Rect> {
        if (!state.snapping) return emptyList()
        val doc = present
        val out = ArrayList<Rect>()
        for (ab in doc.artboards) out += ab.bounds
        val count = doc.layers.sumOf { it.children.size }
        if (count <= 600) {
            for (layer in doc.layers) {
                if (!layer.visible) continue
                for (n in layer.children) {
                    if (!n.visible || n.id in moving) continue
                    n.bounds()?.let(out::add)
                }
            }
        }
        return out
    }

    fun toggleSnapping() {
        state = state.copy(snapping = !state.snapping, message = if (state.snapping) tr("Yakalama kapalı") else tr("Yakalama açık"))
    }

    private fun drawingLayer(): Layer? {
        val doc = present
        return doc.findLayer(state.activeLayerId)?.takeIf { it.visible && !it.locked }
    }

    /** Seçim kutusunun tutamaçlarının ekrandaki yerleri; döndürme tutamacı üst kenarın biraz yukarısındadır. */
    fun rotateHandleOffsetPx(): Float = touchTolerancePx * 2.6f

    private fun selectionHandleAt(screen: Offset): Drag? {
        val ids = state.selection
        if (ids.isEmpty()) return null
        val b = present.boundsOf(ids) ?: return null
        val reach = touchTolerancePx * 1.3f
        val top = viewport.toScreen(Handle.N.on(b))
        val rot = Offset(top.x, top.y - rotateHandleOffsetPx())
        if ((rot - screen).getDistance() < reach) {
            val c = b.center
            val p = viewport.toDocument(screen)
            return Drag.Rotate(ids, c, atan2(p.y - c.y, p.x - c.x))
        }
        val small = b.width * viewport.scale < touchTolerancePx * 4 || b.height * viewport.scale < touchTolerancePx * 4
        var best: Handle? = null
        var bestD = reach
        for (h in Handle.entries) {
            // Küçük nesnelerde kenar tutamaçları nesneyi taşımayı engellemesin.
            if (small && !h.isCorner) continue
            if (!h.isCorner && (b.width == 0.0 || b.height == 0.0)) continue
            val dist = (viewport.toScreen(h.on(b)) - screen).getDistance()
            if (dist < bestD) { bestD = dist; best = h }
        }
        return best?.let { Drag.Scale(ids, b, it) }
    }

    private fun scaleMatrix(d: Drag.Scale, now: Vec2): Matrix {
        val anchor = d.handle.opposite.on(d.bounds)
        val from = d.handle.on(d.bounds)
        val vx = from.x - anchor.x
        val vy = from.y - anchor.y
        var sx = 1.0
        var sy = 1.0
        if (d.handle.isCorner) {
            // Köşeler orantılı ölçekler: parmağın köşegen üzerindeki izdüşümü.
            val len2 = vx * vx + vy * vy
            val s = if (len2 == 0.0) 1.0 else ((now.x - anchor.x) * vx + (now.y - anchor.y) * vy) / len2
            sx = max(s, 0.01)
            sy = sx
        } else if (vx != 0.0) {
            sx = max((now.x - anchor.x) / vx, 0.01)
        } else if (vy != 0.0) {
            sy = max((now.y - anchor.y) / vy, 0.01)
        }
        return Matrix.translate(anchor.x, anchor.y) * Matrix.scale(sx, sy) * Matrix.translate(-anchor.x, -anchor.y)
    }

    private fun shapeNode(d: Drag.Create, now: Vec2): PathNode {
        val s = state
        val fill = s.fill?.let(Paint::Solid)
        val stroke = s.stroke?.let(::styledStroke)
        return when (s.tool) {
            Tool.Line -> PathNode(
                id = d.nodeId,
                name = tr("Çizgi"),
                subpaths = listOf(Shapes.line(d.start, now)),
                stroke = styledStroke(s.stroke ?: Rgba.Black),
                opacity = s.opacity,
            )
            Tool.Ellipse -> PathNode(
                id = d.nodeId, name = tr("Elips"), subpaths = listOf(Shapes.ellipse(Rect.of(d.start, now))),
                fill = fill, stroke = stroke, opacity = s.opacity,
            )
            else -> PathNode(
                id = d.nodeId, name = tr("Dikdörtgen"), subpaths = listOf(Shapes.rect(Rect.of(d.start, now))),
                fill = fill, stroke = stroke, opacity = s.opacity,
            )
        }
    }

    /** Araç çubuğundaki geçerli kontur ayarlarıyla (kalınlık, uç, köşe, çizgi türü) kontur. */
    private fun styledStroke(color: Rgba): Stroke {
        val s = state
        return Stroke(Paint.Solid(color), s.strokeWidth, s.strokeCap, s.strokeJoin, dash = s.dashStyle.forWidth(s.strokeWidth))
    }

    private fun freehandStroke() =
        Stroke(Paint.Solid(state.stroke ?: Rgba.Black), state.strokeWidth, cap = LineCap.Round, join = LineJoin.Round)

    // ---- Kalem ------------------------------------------------------------

    private fun penTap(p: Vec2) {
        val pen = state.pen
        if (pen == null) {
            val layer = drawingLayer() ?: run {
                state = state.copy(message = tr("Etkin katman kilitli ya da gizli"))
                return
            }
            state = state.copy(selection = emptySet(), layerSelection = emptySet(), nodeEdit = null)
            setPen(PenState(newId(), layer.id, listOf(Anchor(p))))
            return
        }
        // İlk düğüme dokunmak yolu kapatır.
        if (pen.anchors.size >= 2 && pen.anchors[0].point.distanceTo(p) < tolerance()) {
            finishPen(close = true)
            return
        }
        setPen(pen.copy(anchors = pen.anchors + Anchor(p)))
    }

    private fun setPen(pen: PenState) {
        val node = PathNode(
            id = pen.nodeId,
            subpaths = listOf(SubPath(pen.anchors)),
            fill = state.fill?.let(Paint::Solid),
            stroke = styledStroke(state.stroke ?: Rgba.Black),
            opacity = state.opacity,
        )
        state = state.copy(pen = pen, preview = present.addNode(pen.layerId, node))
    }

    fun finishPen(close: Boolean = false) {
        val pen = state.pen ?: return
        drag = null
        if (pen.anchors.size < 2) {
            cancelPen()
            return
        }
        val node = PathNode(
            id = pen.nodeId,
            subpaths = listOf(SubPath(pen.anchors, closed = close)),
            fill = state.fill?.let(Paint::Solid),
            stroke = state.stroke?.let(::styledStroke) ?: if (state.fill == null) styledStroke(Rgba.Black) else null,
            opacity = state.opacity,
        )
        state = state.copy(pen = null)
        commit(present.addNode(pen.layerId, node)) { it.withSelection(setOf(pen.nodeId)) }
    }

    fun cancelPen() {
        drag = null
        state = state.copy(pen = null, preview = null)
    }

    // ---- Doğrudan seçim ---------------------------------------------------

    private fun worldOf(nodeId: String): Matrix? {
        val node = present.findNode(nodeId) ?: return null
        return present.parentMatrix(nodeId) * node.transform
    }

    /** Düzenlenen yolun düğümleri, belge koordinatında (tuval üzerine çizmek için). */
    fun editedPath(): Pair<PathNode, Matrix>? {
        val edit = state.nodeEdit ?: return null
        val node = state.document.findNode(edit.nodeId) as? PathNode ?: return null
        val world = state.document.parentMatrix(edit.nodeId) * node.transform
        return node to world
    }

    private fun anchorNear(node: PathNode, world: Matrix, p: Vec2, tol: Double): AnchorRef? {
        var best: AnchorRef? = null
        var bestD = tol
        for ((si, sp) in node.subpaths.withIndex()) {
            for ((ai, a) in sp.anchors.withIndex()) {
                val d = world.apply(a.point).distanceTo(p)
                if (d < bestD) { bestD = d; best = AnchorRef(si, ai) }
            }
        }
        return best
    }

    private fun directTap(p: Vec2) {
        val doc = present
        val tol = tolerance()
        val edit = state.nodeEdit
        val node = edit?.let { doc.findNode(it.nodeId) as? PathNode }
        if (edit != null && node != null) {
            val world = worldOf(edit.nodeId)
            val inv = world?.inverse()
            if (world != null && inv != null) {
                anchorNear(node, world, p, tol)?.let { ref ->
                    state = state.copy(nodeEdit = edit.copy(anchor = ref))
                    return
                }
                // Yolun üzerine dokunmak oraya yeni düğüm ekler.
                val loc = node.subpaths.nearest(inv.apply(p))
                if (loc != null && loc.distance * world.meanScale < tol * 0.8) {
                    val (subpaths, ref) = node.subpaths.insertAnchor(loc) ?: return
                    commit(doc.updateNode(node.id) { (it as PathNode).copy(subpaths = subpaths) }) {
                        it.copy(nodeEdit = NodeEdit(node.id, ref))
                    }
                    return
                }
            }
        }
        val hit = doc.hitTestDeep(p, tol)
        state = if (hit == null) {
            state.copy(selection = emptySet(), layerSelection = emptySet(), nodeEdit = null)
        } else {
            state.withSelection(setOf(hit.id)).copy(nodeEdit = if (hit is PathNode) NodeEdit(hit.id) else null)
        }
    }

    private fun beginDirectDrag(start: Vec2, startScreen: Offset): Drag {
        val doc = present
        val tol = tolerance()
        val edit = state.nodeEdit
        val node = edit?.let { doc.findNode(it.nodeId) as? PathNode }
        if (edit != null && node != null) {
            val world = worldOf(edit.nodeId)
            val inv = world?.inverse()
            if (world != null && inv != null) {
                // Seçili düğümün tutamaçları düğümlerden önce denenir (üst üste gelebilirler).
                edit.anchor?.let { ref ->
                    val a = node.subpaths.anchorAt(ref)
                    for (outgoing in listOf(true, false)) {
                        val h = (if (outgoing) a?.handleOut else a?.handleIn) ?: continue
                        if (world.apply(h).distanceTo(start) < tol) return Drag.HandleDrag(node.id, ref, outgoing, inv, node.subpaths)
                    }
                }
                anchorNear(node, world, start, tol)?.let { ref ->
                    state = state.copy(nodeEdit = edit.copy(anchor = ref))
                    return Drag.AnchorDrag(node.id, ref, inv, node.subpaths, start)
                }
            }
        }
        val hit = doc.hitTestDeep(start, tol) ?: return Drag.Pan(startScreen)
        state = state.withSelection(setOf(hit.id)).copy(nodeEdit = if (hit is PathNode) NodeEdit(hit.id) else null)
        return Drag.Move(setOf(hit.id), start)
    }

    fun toggleSmoothAnchor() {
        val edit = state.nodeEdit ?: return
        val ref = edit.anchor ?: return
        commit(present.updateNode(edit.nodeId) { n -> (n as? PathNode)?.let { it.copy(subpaths = it.subpaths.toggleSmooth(ref)) } ?: n })
    }

    // ---- Komutlar ---------------------------------------------------------

    fun selectTool(tool: Tool) {
        if (state.pen != null) finishPen()
        onDragCancel()
        var s = state.copy(tool = tool, marquee = null)
        if (tool == Tool.Direct) {
            // Seçili tek bir yol varsa doğrudan düzenlemeye geç.
            val only = s.selection.singleOrNull()?.let { present.findNode(it) }
            s = s.copy(nodeEdit = (only as? PathNode)?.let { NodeEdit(it.id) })
        } else {
            s = s.copy(nodeEdit = null)
        }
        if (tool == Tool.Pen || tool == Tool.Pencil) s = s.copy(selection = emptySet(), layerSelection = emptySet())
        state = s
    }

    fun selectPaintTarget(target: PaintTarget) {
        state = state.copy(paintTarget = target)
    }

    fun deleteSelection() {
        val edit = state.nodeEdit
        val ref = edit?.anchor
        if (state.tool == Tool.Direct && edit != null && ref != null) {
            val node = present.findNode(edit.nodeId) as? PathNode ?: return
            val subpaths = node.subpaths.deleteAnchor(ref)
            if (subpaths.isEmpty()) {
                commit(present.removeNode(node.id)) { it.copy(selection = emptySet(), nodeEdit = null) }
            } else {
                commit(present.updateNode(node.id) { (it as PathNode).copy(subpaths = subpaths) }) { it.copy(nodeEdit = NodeEdit(node.id)) }
            }
            return
        }
        if (state.selection.isEmpty() && state.layerSelection.isEmpty()) return
        onDragCancel()
        var doc = state.selection.fold(present) { d, id -> d.removeNode(id) }
        // Bütün olarak seçilmiş katmanlar da silinir (son katman kalır).
        for (id in state.layerSelection) doc = doc.removeLayer(id)
        commit(doc) {
            it.copy(
                selection = emptySet(), layerSelection = emptySet(), nodeEdit = null,
                activeLayerId = if (doc.findLayer(it.activeLayerId) != null) it.activeLayerId else doc.layers.last().id,
            )
        }
    }

    fun selectAll() {
        val ids = HashSet<String>()
        for (l in present.layers) if (l.visible && !l.locked) for (n in l.children) if (n.visible && !n.locked) ids += n.id
        state = state.copy(tool = Tool.Select, nodeEdit = null).withSelection(ids)
    }

    fun duplicateSelection() {
        if (state.selection.isEmpty()) return
        val off = 24.0 / viewport.scale
        val (doc, ids) = present.duplicate(state.selection, Vec2(off, off))
        commit(doc) { it.withSelection(ids.toSet()) }
    }

    fun groupSelection() {
        val (doc, id) = present.group(state.selection) ?: run {
            state = state.copy(message = tr("Gruplamak için en az iki nesne seçin"))
            return
        }
        commit(doc) { it.withSelection(setOf(id)) }
    }

    fun ungroupSelection() {
        var doc = present
        val out = HashSet<String>()
        var any = false
        for (id in state.selection) {
            val r = doc.ungroup(id)
            if (r == null) { out += id; continue }
            any = true
            doc = r.first
            out += r.second
        }
        if (any) commit(doc) { it.withSelection(out) }
    }

    fun reorderSelection(move: ZMove) {
        if (state.selection.isEmpty()) return
        // Birden çok nesne aynı yöne taşınırken birbirlerinin üzerinden atlamasın.
        val order = present.layers.flatMap { it.children }.map { it.id }.filter { it in state.selection }
        val seq = if (move == ZMove.Forward || move == ZMove.Back) order.asReversed() else order
        val ids = if (seq.isEmpty()) state.selection.toList() else seq
        commit(ids.fold(present) { d, id -> d.reorderNode(id, move) })
    }

    /** Etkin hedefin (dolgu ya da kontur) rengini ayarlar; `null` "yok" demektir. */
    fun setColor(color: Rgba?) {
        val s = state
        val width = s.strokeWidth
        val target = if (s.paintTarget == PaintTarget.Stroke) PaintTarget.Stroke else PaintTarget.Fill
        val next = if (target == PaintTarget.Fill) s.copy(fill = color, paintTarget = target) else s.copy(stroke = color)
        state = next
        s.pen?.let { setPen(it); return }
        onDragCancel()
        val doc = s.selection.fold(present) { d, id ->
            d.updateNode(id) { node ->
                if (target == PaintTarget.Fill) {
                    node.restyled({ color?.let(Paint::Solid) }, { it })
                } else {
                    node.restyled({ it }, { st -> color?.let { c -> (st ?: styledStroke(c).copy(width = width)).copy(paint = Paint.Solid(c)) } })
                }
            }
        }
        commit(doc)
    }

    /** Kaydırıcı sürüklenirken [done] `false` gelir; bırakılınca tek bir geçmiş kaydı yazılır. */
    fun setStrokeWidth(width: Double, done: Boolean) {
        state = state.copy(strokeWidth = width)
        state.pen?.let { setPen(it); return }
        val doc = state.selection.fold(present) { d, id ->
            d.updateNode(id) { n -> n.restyled({ it }, { st -> st?.copy(width = width) }) }
        }
        if (done) commit(doc) else state = state.copy(preview = doc.takeIf { it != present })
    }

    /** Kontur ucu, köşesi ve çizgi türü. Verilmeyenler değişmez. */
    fun setStrokeStyle(cap: LineCap? = null, join: LineJoin? = null, dash: DashStyle? = null) {
        var s = state.copy(
            strokeCap = cap ?: state.strokeCap,
            strokeJoin = join ?: state.strokeJoin,
            dashStyle = dash ?: state.dashStyle,
        )
        // Noktalı çizgi ancak yuvarlak uçla nokta olarak görünür.
        if (dash == DashStyle.Dotted) s = s.copy(strokeCap = LineCap.Round)
        state = s
        s.pen?.let { setPen(it); return }
        val doc = s.selection.fold(present) { d, id ->
            d.updateNode(id) { n ->
                n.restyled({ it }, { st -> st?.copy(cap = s.strokeCap, join = s.strokeJoin, dash = s.dashStyle.forWidth(st.width)) })
            }
        }
        commit(doc)
    }

    /** Seçimin dolgusuna (ya da kontur hedefi etkinse konturuna) gradyan uygular; her nesne kendi kutusuna göre boyanır. */
    fun applyGradient(spec: GradientSpec) {
        if (state.selection.isEmpty()) {
            state = state.copy(message = tr("Gradyan için önce bir nesne seçin"))
            return
        }
        val toStroke = state.paintTarget == PaintTarget.Stroke
        val width = state.strokeWidth
        val doc = state.selection.fold(present) { d, id -> d.updateNode(id) { it.withGradient(spec, toStroke, width) } }
        commit(doc)
    }

    /** Seçili tek nesnenin gradyanı (düzenleyiciyi onunla açmak için); yoksa `null`. */
    fun currentGradient(): GradientSpec? {
        val node = state.selection.singleOrNull()?.let { present.findNode(it) } ?: return null
        val sample = node.styleSample() ?: return null
        return GradientSpec.from(if (state.paintTarget == PaintTarget.Stroke) sample.second?.paint else sample.first)
    }

    private fun eyedrop(p: Vec2) {
        val hit = present.hitTestDeep(p, tolerance()) ?: return
        val sample = hit.styleSample() ?: run {
            state = state.copy(message = tr("Bu nesneden stil alınamaz"))
            return
        }
        val fill = sample.first
        val stroke = sample.second
        val targets = state.selection - hit.id
        if (targets.isEmpty()) {
            // Seçim yoksa stil, bundan sonra çizilecekler için alınır.
            state = state.copy(
                fill = fill?.let { (it as? Paint.Solid)?.color ?: GradientSpec.from(it)?.stops?.firstOrNull()?.color ?: Rgba.Black },
                stroke = (stroke?.paint as? Paint.Solid)?.color,
                strokeWidth = stroke?.width ?: state.strokeWidth,
                strokeCap = stroke?.cap ?: state.strokeCap,
                strokeJoin = stroke?.join ?: state.strokeJoin,
                opacity = hit.opacity,
                message = tr("Stil alındı"),
            )
            return
        }
        val spec = GradientSpec.from(fill)
        val doc = targets.fold(present) { d, id ->
            d.updateNode(id) { n ->
                var out = n.restyled({ if (spec == null) fill else it }, { stroke?.let { st -> st.copy(paint = (st.paint as? Paint.Solid) ?: Paint.Solid(GradientSpec.from(st.paint)?.stops?.firstOrNull()?.color ?: Rgba.Black)) } })
                if (spec != null) out = out.withGradient(spec)
                out.withOpacity(hit.opacity)
            }
        }
        commit(doc) { it.copy(message = tr("Stil uygulandı")) }
    }

    // ---- Düzenleme: hizalama, Pathfinder, maske, sayısal dönüşüm --------------------------

    private fun artboardFor(box: Rect?): Rect? {
        val boards = present.artboards.map { it.bounds }
        if (box == null) return boards.firstOrNull()
        return boards.firstOrNull { it.contains(box.center) } ?: boards.minByOrNull { it.center.distanceTo(box.center) }
    }

    fun alignSelection(mode: Align) {
        if (state.selection.isEmpty()) return
        commit(present.align(state.selection, mode, artboardFor(present.boundsOf(state.selection))))
    }

    fun distributeSelection(horizontal: Boolean) {
        if (state.selection.size < 3) {
            state = state.copy(message = tr("Dağıtmak için en az üç nesne seçin"))
            return
        }
        commit(present.distribute(state.selection, horizontal))
    }

    fun makeClippingMask() {
        val (doc, id) = present.makeClippingMask(state.selection) ?: run {
            state = state.copy(message = tr("Maske için en üstte bir yol ve altında en az bir nesne seçin"))
            return
        }
        commit(doc) { it.withSelection(setOf(id)) }
    }

    fun releaseClippingMask() {
        val id = state.selection.singleOrNull() ?: return
        commit(present.releaseClippingMask(id))
    }

    /** Pathfinder: seçili yolları tek bir yola dönüştürür. */
    fun pathfinder(op: BooleanOp) {
        // Alttan üste sıralı, üst düzey yollar
        val ordered = present.layers.flatMap { it.children }.filter { it.id in state.selection }
        val paths = ordered.filterIsInstance<PathNode>()
        if (paths.size < 2 || paths.size != ordered.size) {
            state = state.copy(message = tr("Bu işlem için en az iki yol seçin (metni önce yola çevirin, grubu çözün)"))
            return
        }
        val result = try {
            PathBoolean.combine(paths, op)
        } catch (e: Throwable) {
            null
        }
        if (result == null) {
            state = state.copy(message = tr("Şekiller birleştirilemedi"))
            return
        }
        // Illustrator'daki gibi: çıkarmada alttaki nesnenin, diğerlerinde en üstteki nesnenin görünümü kalır.
        val styleFrom = if (op == BooleanOp.MinusFront) paths.first() else paths.last()
        var doc = present
        for (n in paths) if (n.id != styleFrom.id) doc = doc.removeNode(n.id)
        if (result.subpaths.isEmpty()) {
            commit(doc.removeNode(styleFrom.id)) { it.copy(selection = emptySet(), nodeEdit = null, message = tr("Sonuç boş: şekiller kesişmiyor")) }
            return
        }
        val m = styleFrom.transform
        val merged = styleFrom.copy(
            subpaths = result.subpaths,
            fillRule = result.rule,
            transform = Matrix.Identity,
            // Sonuç belge uzayında; boyalar da oraya taşınır.
            fill = styleFrom.fill?.let { if (m.isIdentity) it else it.transformedBy(m) },
            stroke = styleFrom.stroke?.let { st -> if (m.isIdentity) st else st.copy(paint = st.paint.transformedBy(m)) },
        )
        commit(doc.updateNode(styleFrom.id) { merged }) { it.withSelection(setOf(styleFrom.id)) }
    }

    fun selectionBounds(): Rect? = present.boundsOf(state.selection)

    /** Seçimi verilen konuma ve boyuta getirir, sonra merkezinin çevresinde [rotateDegrees] kadar döndürür. */
    fun transformSelection(x: Double, y: Double, width: Double, height: Double, rotateDegrees: Double) {
        val b = selectionBounds() ?: return
        val sx = if (b.width > 1e-9 && width > 0) width / b.width else 1.0
        val sy = if (b.height > 1e-9 && height > 0) height / b.height else 1.0
        var m = Matrix.translate(x, y) * Matrix.scale(sx, sy) * Matrix.translate(-b.left, -b.top)
        if (rotateDegrees != 0.0) {
            val cx = x + b.width * sx / 2
            val cy = y + b.height * sy / 2
            m = Matrix.translate(cx, cy) * Matrix.rotate(Math.toRadians(rotateDegrees)) * Matrix.translate(-cx, -cy) * m
        }
        commit(present.transformNodes(state.selection, m))
    }

    fun flipSelection(horizontal: Boolean) {
        val b = selectionBounds() ?: return
        val c = b.center
        val m = Matrix.translate(c.x, c.y) * (if (horizontal) Matrix.scale(-1.0, 1.0) else Matrix.scale(1.0, -1.0)) * Matrix.translate(-c.x, -c.y)
        commit(present.transformNodes(state.selection, m))
    }

    fun setOpacity(opacity: Double, done: Boolean) {
        state = state.copy(opacity = opacity)
        state.pen?.let { setPen(it); return }
        val doc = state.selection.fold(present) { d, id -> d.updateNode(id) { it.withOpacity(opacity) } }
        if (done) commit(doc) else state = state.copy(preview = doc.takeIf { it != present })
    }

    /** Seçilen nesnenin stilini araç çubuğuna yansıtır; gradyanlar göstergede ilk renkleriyle görünür. */
    private fun EditorState.withSelection(ids: Set<String>): EditorState {
        val doc = history.present
        val node = ids.singleOrNull()?.let { doc.findNode(it) }
            ?: return copy(selection = ids, layerSelection = emptySet(), nodeEdit = nodeEdit?.takeIf { it.nodeId in ids })
        val sample = node.styleSample()
        val f = sample?.first
        val st = sample?.second
        return copy(
            selection = ids,
            layerSelection = emptySet(),
            nodeEdit = nodeEdit?.takeIf { it.nodeId in ids },
            fill = if (sample == null) fill else (f as? Paint.Solid)?.color ?: if (f == null) null else fill,
            stroke = if (sample == null) stroke else (st?.paint as? Paint.Solid)?.color ?: if (st == null) null else stroke,
            strokeWidth = st?.width ?: strokeWidth,
            strokeCap = st?.cap ?: strokeCap,
            strokeJoin = st?.join ?: strokeJoin,
            dashStyle = st?.let { DashStyle.of(it.dash, it.width) } ?: dashStyle,
            opacity = node.opacity,
        )
    }

    // ---- Katmanlar --------------------------------------------------------

    fun toggleLayersPanel() {
        state = state.copy(layersOpen = !state.layersOpen)
    }

    fun toggleExpanded(id: String) {
        state = state.copy(expanded = if (id in state.expanded) state.expanded - id else state.expanded + id)
    }

    fun setActiveLayer(id: String) {
        if (present.findLayer(id) != null) state = state.copy(activeLayerId = id)
    }

    fun addLayer() {
        val layer = Layer(name = present.nextLayerName())
        commit(present.addLayer(layer, state.activeLayerId)) { it.copy(activeLayerId = layer.id) }
    }

    fun deleteLayer(id: String) {
        if (present.layers.size <= 1) {
            state = state.copy(message = tr("Son katman silinemez"))
            return
        }
        val doc = present.removeLayer(id)
        commit(doc) { s ->
            s.copy(
                selection = s.selection.filterTo(HashSet()) { doc.findNode(it) != null },
                layerSelection = s.layerSelection - id,
                nodeEdit = null,
                activeLayerId = if (s.activeLayerId == id) doc.layers.last().id else s.activeLayerId,
            )
        }
    }

    fun renameLayer(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        commit(present.updateLayer(id) { it.copy(name = clean) })
    }

    fun toggleLayerVisible(id: String) = commitLayerFlag(id) { it.copy(visible = !it.visible) }
    fun toggleLayerLocked(id: String) = commitLayerFlag(id) { it.copy(locked = !it.locked) }

    private fun commitLayerFlag(id: String, f: (Layer) -> Layer) {
        val doc = present.updateLayer(id, f)
        val layer = doc.findLayer(id) ?: return
        commit(doc) { s ->
            if (layer.visible && !layer.locked) s else {
                // Gizlenen ya da kilitlenen katmandaki nesneler seçili kalmasın.
                val gone = HashSet<String>()
                fun collect(nodes: List<Node>) {
                    for (n in nodes) { gone += n.id; if (n is GroupNode) collect(n.children) }
                }
                collect(layer.children)
                s.copy(selection = s.selection - gone, nodeEdit = s.nodeEdit?.takeIf { it.nodeId !in gone })
            }
        }
    }

    fun setLayerOpacity(id: String, opacity: Double, done: Boolean) {
        val doc = present.updateLayer(id) { it.copy(opacity = opacity.coerceIn(0.0, 1.0)) }
        if (done) commit(doc) else state = state.copy(preview = doc.takeIf { it != present })
    }

    fun moveLayer(id: String, up: Boolean) = commit(present.moveLayer(id, up))

    fun moveSelectionToLayer(id: String) {
        if (state.selection.isEmpty()) return
        commit(present.moveNodesToLayer(state.selection, id)) { it.copy(activeLayerId = id) }
    }

    fun toggleNodeVisible(id: String) {
        val doc = present.updateNode(id) { it.withVisible(!it.visible) }
        commit(doc) { s -> if (doc.findNode(id)?.visible == false) s.copy(selection = s.selection - id, nodeEdit = s.nodeEdit?.takeIf { it.nodeId != id }) else s }
    }

    fun toggleNodeLocked(id: String) {
        val doc = present.updateNode(id) { it.withLocked(!it.locked) }
        commit(doc) { s -> if (doc.findNode(id)?.locked == true) s.copy(selection = s.selection - id, nodeEdit = s.nodeEdit?.takeIf { it.nodeId != id }) else s }
    }

    fun renameNode(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        commit(present.updateNode(id) { it.withName(clean) })
    }

    /** Katman panelinden nesne seçimi (iç içe nesneler dahil). */
    /** Katman panelinden seçim: nesne tuvalde de seçilir ve ekranın dışındaysa görünüme getirilir. */
    fun selectNode(id: String) {
        val node = present.findNode(id) ?: return
        if (state.pen != null) finishPen()
        state = state.withSelection(setOf(id)).copy(
            nodeEdit = if (state.tool == Tool.Direct && node is PathNode) NodeEdit(id) else null,
            tool = if (state.tool == Tool.Direct) Tool.Direct else Tool.Select,
        )
        bringIntoView(present.boundsOf(setOf(id)) ?: return)
    }

    /** [box] ekranda hiç görünmüyorsa görünümü (ölçeği değiştirmeden) onu ortalayacak biçimde kaydırır. */
    private fun bringIntoView(box: Rect) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        // Tuvalin alt kısmını bir panel örtüyorsa görünür alan o kadar kısadır.
        val height = (canvasSize.height - obscuredBottomPx).coerceAtLeast(canvasSize.height * 0.25f)
        val visible = Rect.of(viewport.toDocument(Offset.Zero), viewport.toDocument(Offset(canvasSize.width, height)))
        if (visible.contains(box.center)) return
        val c = box.center
        fitted = false
        viewport = viewport.copy(
            offset = Offset(canvasSize.width / 2 - c.x.toFloat() * viewport.scale, height / 2 - c.y.toFloat() * viewport.scale),
        )
    }

    /** Tuvalin alt kenarından yukarı doğru, panellerin örttüğü yükseklik (piksel); arayüz bildirir. */
    var obscuredBottomPx: Float = 0f

    /**
     * Tuvalde seçili nesneyi katman panelinde gösterir: panel açılır, nesnenin içinde bulunduğu katman ve
     * gruplar genişletilir, liste o satıra kaydırılır.
     */
    fun revealSelectionInLayers() {
        val id = state.selection.firstOrNull() ?: return
        val path = ArrayList<String>()
        fun search(nodes: List<Node>): Boolean {
            for (n in nodes) {
                if (n.id == id) return true
                if (n is GroupNode) {
                    path += n.id
                    if (search(n.children)) return true
                    path.removeAt(path.size - 1)
                }
            }
            return false
        }
        val layer = present.layers.firstOrNull { l -> path.clear(); search(l.children) } ?: return
        state = state.copy(layersOpen = true, expanded = state.expanded + layer.id + path, revealTick = state.revealTick + 1)
    }

    // ---- Katman panelinde çoklu seçim ----------------------------------------

    fun toggleMultiSelect() {
        state = if (state.multiSelect) state.copy(multiSelect = false, layerSelection = emptySet()) else state.copy(multiSelect = true)
    }

    /** Çoklu seçim kipinde nesne satırına dokunuldu: nesne seçime eklenir ya da seçimden çıkar. */
    fun toggleNodeSelected(id: String) {
        present.findNode(id) ?: return
        if (state.pen != null) finishPen()
        val layers = state.layerSelection
        val ids = if (id in state.selection) state.selection - id else state.selection + id
        // Bütün olarak seçili bir katmanın nesnesi çıkarıldıysa katman artık "bütün" seçili değildir.
        val owner = if (id in state.selection) present.layerOf(id)?.id else null
        state = state.withSelection(ids).copy(
            layerSelection = if (owner != null) layers - owner else layers,
            nodeEdit = null,
            tool = Tool.Select,
        )
    }

    /** Çoklu seçim kipinde katman satırına dokunuldu: katman, içindeki her şeyle birlikte seçilir ya da bırakılır. */
    fun toggleLayerSelected(layerId: String) {
        val layer = present.findLayer(layerId) ?: return
        if (state.pen != null) finishPen()
        val kids = layer.children.map { it.id }.toSet()
        val layers = state.layerSelection
        state = if (layerId in layers) {
            state.withSelection(state.selection - kids).copy(layerSelection = layers - layerId, nodeEdit = null)
        } else {
            // Kilitli ya da gizli katmanın nesneleri tuvalde seçilmez; katman yine de kopyalanabilir.
            val add = if (layer.visible && !layer.locked) layer.children.filter { it.visible && !it.locked }.map { it.id } else emptyList()
            state.withSelection(state.selection + add).copy(layerSelection = layers + layerId, nodeEdit = null, tool = Tool.Select)
        }
    }

    // ---- Renklere göre gruplama ----------------------------------------------

    /** Katman panelini katman listesi ile renk listesi arasında değiştirir. */
    fun toggleColorView() {
        state = state.copy(colorView = !state.colorView, layersOpen = true)
    }

    /** Bir rengi kullanan (görünür, kilitsiz) bütün nesneleri seçer. */
    fun selectColor(rgb: Int) {
        val group = present.colorGroups().firstOrNull { it.rgb == rgb } ?: return
        if (state.pen != null) finishPen()
        val hidden = HashSet<String>()
        fun walk(nodes: List<Node>, off: Boolean) {
            for (n in nodes) {
                val h = off || !n.visible
                if (h) hidden += n.id
                if (n is GroupNode) walk(n.children, h)
            }
        }
        for (l in present.layers) walk(l.children, !l.visible)
        val ids = group.nodeIds.filterTo(HashSet()) { it !in hidden }
        state = state.withSelection(ids).copy(nodeEdit = null, tool = Tool.Select)
        present.boundsOf(ids)?.let(::bringIntoView)
    }

    /** Bir rengi, kullanıldığı her yerde (dolgu, kontur, gradyan durağı) tek seferde değiştirir. */
    fun recolor(rgb: Int, to: Rgba) {
        val count = present.colorGroups().firstOrNull { it.rgb == rgb }?.nodeIds?.size ?: return
        onDragCancel()
        var doc = present.recolored(rgb, to)
        // Tasarımda renk lejantı varsa o da yeni renklerle yenilenir (yeri ve boyutu baştan kurulur).
        present.legendOptions()?.let { options -> doc.withColorLegend(options)?.let { doc = it.document } }
        commit(doc) { s ->
            s.copy(selection = s.selection.filterTo(HashSet()) { doc.findNode(it) != null }, message = tr("%s nesnenin rengi değişti", count))
        }
    }

    /** Tasarımdaki renklerin kodları (onaltılık, RGB, CMYK); paylaşmak için düz metin. */
    fun colorCodesText(options: LegendOptions): String = present.colorCodesText(options)

    /** Tasarımda kullanılan renklerin lejantını çalışma yüzeyinin altına ekler (varsa eskisini yeniler). */
    fun addColorLegend(options: LegendOptions) {
        if (state.pen != null) finishPen()
        onDragCancel()
        val result = present.withColorLegend(options) ?: run {
            state = state.copy(message = tr("Belgede renkli nesne yok"))
            return
        }
        val doc = result.document
        commit(doc) {
            it.copy(
                tool = Tool.Select, nodeEdit = null,
                activeLayerId = if (doc.findLayer(it.activeLayerId) != null) it.activeLayerId else doc.layers.last().id,
                message = tr("Renk lejantı eklendi: %s renk", result.colors),
            ).withSelection(setOf(result.groupId))
        }
        present.boundsOf(result.groupId)?.let(::bringIntoView)
    }

    // ---- Pano: kopyala, kes, yapıştır (belgeler arasında da) -------------------

    private var clipboard: Clip? = null
    private var clipSource: String? = null
    private var pasteCount = 0

    /** Panoda yapıştırılabilecek bir şey var mı. */
    var canPaste by mutableStateOf(false)
        private set

    /** Seçili nesneleri (ve bütün olarak seçili katmanları) panoya alır. */
    fun copySelection(): Boolean {
        if (state.pen != null) finishPen()
        val clip = present.clip(state.selection, state.layerSelection)
        if (clip.isEmpty) {
            state = state.copy(message = tr("Kopyalamak için önce bir nesne seçin"))
            return false
        }
        clipboard = clip
        clipSource = state.session.id
        pasteCount = 0
        canPaste = true
        state = state.copy(
            message = if (clip.layers.isEmpty()) tr("%s nesne kopyalandı", clip.count) else tr("%s katman kopyalandı", clip.layers.size),
        )
        return true
    }

    fun cutSelection() {
        if (!copySelection()) return
        // Kesilen içerik aynı yere geri yapıştırılabilsin.
        clipSource = null
        deleteSelection()
    }

    /**
     * Panodakileri etkin belgeye ekler. Aynı belgede kopyanın üstüne binmesin diye biraz kaydırılır; başka belgede
     * aynı konumda durur, o konum ekranda görünmüyorsa görünen alanın ortasına gelir.
     */
    fun paste() {
        val clip = clipboard ?: run {
            state = state.copy(message = tr("Pano boş"))
            return
        }
        if (state.pen != null) finishPen()
        onDragCancel()
        val target = if (clip.nodes.isEmpty()) present.findLayer(state.activeLayerId) ?: present.layers.last() else drawingLayer() ?: run {
            state = state.copy(message = tr("Etkin katman kilitli ya da gizli"))
            return
        }
        var offset = Vec2.Zero
        val box = clip.bounds()
        if (clipSource == state.session.id) {
            pasteCount++
            val step = 24.0 / viewport.scale * pasteCount
            offset = Vec2(step, step)
        } else if (box != null && canvasSize.width > 0 && canvasSize.height > 0) {
            val visible = Rect.of(viewport.toDocument(Offset.Zero), viewport.toDocument(Offset(canvasSize.width, canvasSize.height)))
            if (!visible.intersects(box)) offset = visible.center - box.center
        }
        val pasted = present.paste(clip, target.id, offset)
        commit(pasted.document) {
            it.copy(tool = Tool.Select, nodeEdit = null, activeLayerId = pasted.layerIds.lastOrNull() ?: target.id, expanded = it.expanded + pasted.layerIds)
                .withSelection(selectable(pasted))
                .copy(message = tr("Yapıştırıldı"))
        }
        present.boundsOf(state.selection)?.let(::bringIntoView)
    }

    /** Eklenen nesnelerden tuvalde seçilebilenler (kilitli ya da gizli katmandakiler dışında). */
    private fun selectable(pasted: Pasted): Set<String> {
        val added = pasted.nodeIds.toHashSet()
        val out = HashSet<String>()
        for (l in pasted.document.layers) {
            if (!l.visible || l.locked) continue
            for (n in l.children) if (n.id in added && n.visible && !n.locked) out += n.id
        }
        return out
    }

    /** "Dışarıdan ekle": seçilen .ai, .pdf ya da .svg dosyasının katmanlarını açık belgeye ekler. */
    fun importInto(uri: String) {
        if (state.pen != null) finishPen()
        val tab = state.session.id
        background(tr("Ekleniyor…")) {
            val opened = io.open(uri)
            withContext(Dispatchers.Main) {
                if (state.session.id != tab) return@withContext
                val pasted = present.merged(opened.result.document)
                if (pasted.layerIds.isEmpty()) {
                    state = state.copy(message = tr("Dosyada eklenecek nesne yok"))
                    return@withContext
                }
                commit(pasted.document) {
                    it.copy(
                        tool = Tool.Select, nodeEdit = null, activeLayerId = pasted.layerIds.last(), layersOpen = true,
                        warnings = opened.result.warnings,
                    ).withSelection(selectable(pasted)).copy(message = tr("%s katman eklendi", pasted.layerIds.size))
                }
                present.boundsOf(state.selection)?.let(::bringIntoView)
            }
        }
    }

    // ---- Metin ------------------------------------------------------------

    fun editSelectedText() {
        val id = state.selection.singleOrNull() ?: return
        val t = present.findNode(id) as? TextNode ?: return
        state = state.copy(textPrompt = TextPrompt(t.id, Vec2.Zero, t.text, t.fontSize, t.fontFamily, t.bold, t.italic, t.align, t.lineHeight, t.tracking))
    }

    /** Seçili metni yola çevirir (Illustrator'daki "Anahat Oluştur"); artık düğümleriyle düzenlenebilir. */
    fun outlineSelectedText() {
        val id = state.selection.singleOrNull() ?: return
        val t = present.findNode(id) as? TextNode ?: return
        val shapes = try {
            TextOutliner.outline(t)
        } catch (e: Throwable) {
            emptyList()
        }
        if (shapes.isEmpty()) {
            state = state.copy(message = tr("Bu metin yola çevrilemedi"))
            return
        }
        val path = PathNode(
            id = t.id, name = t.name, visible = t.visible, locked = t.locked, opacity = t.opacity, transform = t.transform,
            blendMode = t.blendMode, subpaths = shapes, fill = t.fill, stroke = t.stroke,
        )
        commit(present.updateNode(id) { path }) { it.withSelection(setOf(id)) }
    }

    /**
     * Seçili metni eğer. [style] `null` ise eğme kaldırılır. Sürgü oynarken [done] yanlıştır (yalnızca önizlenir),
     * bırakılınca doğru verilir ve değişiklik geçmişe işlenir.
     */
    fun setTextWarp(style: WarpStyle?, bend: Double, done: Boolean) {
        val id = state.selection.singleOrNull() ?: return
        if (present.findNode(id) !is TextNode) return
        val warp = style?.let { TextWarp(it, bend.coerceIn(-1.0, 1.0)) }
        val doc = present.updateNode(id) { (it as? TextNode)?.copy(warp = warp) ?: it }
        if (done) commit(doc) else state = state.copy(preview = doc.takeIf { it != present })
    }

    /** Katmandaki (görünür ve kilitsiz) tüm nesneleri seçer. */
    fun selectLayerObjects(layerId: String) {
        val layer = present.findLayer(layerId) ?: return
        if (state.pen != null) finishPen()
        if (!layer.visible || layer.locked) {
            state = state.copy(message = tr("Etkin katman kilitli ya da gizli"))
            return
        }
        val ids = layer.children.filter { it.visible && !it.locked }.map { it.id }.toSet()
        state = state.withSelection(ids).copy(activeLayerId = layerId, nodeEdit = null, tool = Tool.Select)
        present.boundsOf(ids)?.let(::bringIntoView)
    }

    fun dismissTextPrompt() {
        state = state.copy(textPrompt = null)
    }

    fun confirmText(prompt: TextPrompt) {
        state = state.copy(textPrompt = null)
        val text = prompt.text.trim('\n', ' ')
        if (text.isBlank()) return
        val title = text.substringBefore('\n').take(24)
        val leading = prompt.lineHeight.coerceIn(0.5, 5.0)
        val size = prompt.fontSize.coerceIn(1.0, 5000.0)
        val tracking = prompt.tracking.coerceIn(-500.0, 5000.0)
        if (prompt.nodeId != null) {
            commit(
                present.updateNode(prompt.nodeId) { n ->
                    (n as? TextNode)?.copy(
                        text = text, name = title, fontSize = size, fontFamily = prompt.fontFamily, align = prompt.align, lineHeight = leading,
                        tracking = tracking,
                        // Metin değişti: özgün fontun harf biçimleri artık geçerli değil, cihaz fontuna geçilir.
                        bold = prompt.bold, italic = prompt.italic, measuredWidth = null, outline = null,
                    ) ?: n
                },
            )
            return
        }
        val layer = drawingLayer() ?: run {
            state = state.copy(message = tr("Etkin katman kilitli ya da gizli"))
            return
        }
        val node = TextNode(
            name = title, text = text, fontSize = size, fontFamily = prompt.fontFamily, bold = prompt.bold, italic = prompt.italic,
            align = prompt.align, lineHeight = leading, tracking = tracking,
            fill = Paint.Solid(state.fill ?: Rgba.Black), transform = Matrix.translate(prompt.position.x, prompt.position.y),
            opacity = state.opacity,
        )
        commit(present.addNode(layer.id, node)) { it.copy(tool = Tool.Select).withSelection(setOf(node.id)) }
    }

    // ---- Dosya ------------------------------------------------------------

    fun messageShown() {
        if (state.message != null) state = state.copy(message = null)
    }

    fun dismissWarnings() {
        state = state.copy(warnings = emptyList())
    }

    fun suggestedFileName(format: ExportFormat): String =
        present.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Adsiz" } + "." + format.extension

    /** Bağlı dosyanın üzerine sorulmadan yazılabilir mi? Değilse arayüz "Farklı kaydet" penceresini açar. */
    val canSaveInPlace: Boolean get() = state.session.linkUri != null && state.session.linkWritable

    fun renameDocument(name: String) {
        val clean = name.trim().ifEmpty { return }
        commit(present.copy(name = clean))
    }

    /** Belgeyi ekrana yükler. [stored] doluysa belge depodan geldi demektir ve yeniden yazılması gerekmez. */
    /**
     * Belgeyi ekrana yükler. [stored] doluysa belge depodan geldi demektir ve yeniden yazılması gerekmez.
     * [newTab] doğruysa açık belge kapanmaz, arka sekmede kalır; hiç dokunulmamış boş belgenin yerine ise doğrudan geçilir.
     */
    private fun load(doc: Document, warnings: List<String>, session: DocSession, stored: Boolean, newTab: Boolean = true) {
        leaveDocument()
        val old = state
        val replace = !newTab || !worthStoring(old)
        if (!replace) {
            parked[old.session.id] = park(old)
            persistLater(old)
        }
        state = freshState(doc, session).copy(
            warnings = warnings, layersOpen = old.layersOpen, snapping = old.snapping, multiSelect = old.multiSelect, colorView = old.colorView,
            busy = old.busy, crashReport = old.crashReport,
        )
        tabOrder = if (replace) tabOrder.map { if (it == old.session.id) session.id else it } else tabOrder + session.id
        if (stored) storedDocs[session.id] = doc
        // Çok sekme belleği doldurmasın: en eski arka sekme kapanır (belge "Belgelerim"de durur).
        while (tabOrder.size > MAX_TABS) {
            val oldest = tabOrder.first { it != session.id }
            parked.remove(oldest)
            tabOrder = tabOrder - oldest
        }
        fitPending = true
        if (canvasSize.width > 0) { fitPending = false; fitToScreen() }
        if (!stored) scheduleAutosave()
    }

    /** Belge değişmeden önce yarım kalan işleri toparlar (kalemle çizilen yol biter, açık pencereler kapanır). */
    private fun leaveDocument() {
        if (state.pen != null) finishPen()
        onDragCancel()
        drag = null
        if (state.trace != null) cancelTrace()
        if (state.textPrompt != null) state = state.copy(textPrompt = null)
    }

    private fun park(s: EditorState) = Parked(
        s.copy(preview = null, marquee = null, guides = null, busy = null, message = null, textPrompt = null, trace = null, gallery = null, crashReport = null, pen = null),
        viewport, fitted || fitPending,
    )

    /** Arka sekmedeki belgeyi öne getirir; önceki belgenin araç ve panel ayarları korunur. */
    private fun show(target: Parked, from: EditorState) {
        state = target.state.copy(
            layersOpen = from.layersOpen, snapping = from.snapping, multiSelect = from.multiSelect, colorView = from.colorView, busy = from.busy,
            crashReport = from.crashReport, layerSelection = if (from.multiSelect) target.state.layerSelection else emptySet(),
        )
        viewport = target.viewport
        fitted = target.fitted
        // Sekme beklerken ekran boyutu değişmiş olabilir.
        if (fitted) {
            fitPending = true
            if (canvasSize.width > 0) { fitPending = false; fitToScreen() }
        }
        val id = state.session.id
        backgroundScope.launch { try { if (io.store.exists(id)) io.store.setCurrent(id) } catch (e: Throwable) { } }
    }

    fun switchTab(id: String) {
        if (id == state.session.id) return
        val target = parked[id] ?: return
        leaveDocument()
        val old = state
        parked.remove(id)
        parked[old.session.id] = park(old)
        show(target, old)
        persistLater(old)
    }

    /** Sekmeyi kapatır. Belge silinmez: son hali "Belgelerim"de durur. */
    fun closeTab(id: String) {
        if (id == state.session.id) leaveDocument()
        val closing = stateOf(id) ?: return
        val kept = worthStoring(closing)
        persistLater(closing)
        dropTab(id)
        if (kept) state = state.copy(message = tr("Sekme kapatıldı; belge \"Belgelerim\"de duruyor"))
    }

    /** Sekmeyi kaydetmeden listeden çıkarır. Son sekmeyse yerine boş bir belge açılır. */
    private fun dropTab(id: String) {
        if (id != state.session.id) {
            if (parked.remove(id) != null) tabOrder = tabOrder - id
            return
        }
        autosaveJob?.cancel()
        val i = tabOrder.indexOf(id)
        val next = tabOrder.getOrNull(i + 1) ?: tabOrder.getOrNull(i - 1)
        val target = next?.let { parked.remove(it) }
        val old = state
        if (target == null) {
            state = freshState(Document.blank(name = tr("Adsız")), DocSession(io.store.newId())).copy(
                layersOpen = old.layersOpen, snapping = old.snapping, busy = old.busy, crashReport = old.crashReport, gallery = old.gallery,
            )
            tabOrder = listOf(state.session.id)
            fitPending = true
            if (canvasSize.width > 0) { fitPending = false; fitToScreen() }
        } else {
            show(target, old)
            state = state.copy(gallery = old.gallery)
            tabOrder = tabOrder - id
        }
    }

    /** Kimliği verilen açık belgenin (etkin ya da arka sekmedeki) durumu. */
    private fun stateOf(id: String): EditorState? = if (state.session.id == id) state else parked[id]?.state

    /** Arka planda biten bir iş, başladığı sekmenin durumunu günceller (kullanıcı bu arada sekme değiştirmiş olabilir). */
    private fun editTab(id: String, f: (EditorState) -> EditorState) {
        if (state.session.id == id) state = f(state)
        else parked[id]?.let { parked[id] = Parked(f(it.state), it.viewport, it.fitted) }
    }

    fun newDocument(width: Double, height: Double) {
        if (state.pen != null) finishPen()
        background(tr("Hazırlanıyor…")) {
            persistNow()
            withContext(Dispatchers.Main) {
                load(Document.blank(width.coerceIn(1.0, 20000.0), height.coerceIn(1.0, 20000.0), tr("Adsız")), emptyList(), DocSession(io.store.newId()), stored = false)
            }
        }
    }

    /** Dosyayı yeni sekmede açar; açık belge kapanmaz. Dosya zaten açıksa o sekmeye geçilir. */
    fun open(uri: String) {
        if (state.pen != null) finishPen()
        tabOrder.firstOrNull { stateOf(it)?.session?.linkUri == uri }?.let { open ->
            switchTab(open)
            return
        }
        background(tr("Açılıyor…")) {
            val opened = io.open(uri)
            io.persistAccess(uri)
            persistNow()
            val session = DocSession(
                id = io.store.newId(),
                linkUri = uri,
                linkFormat = opened.format,
                // Başka programın dosyasının üzerine sorulmadan yazılmaz: ilk kayıtta yeni dosya adı istenir.
                linkWritable = opened.result.writtenByThisApp,
            )
            withContext(Dispatchers.Main) { load(opened.result.document, opened.result.warnings, session, stored = false) }
        }
    }

    fun openSample() {
        if (state.pen != null) finishPen()
        background(tr("Açılıyor…")) {
            val opened = io.openAsset("samples/gopher.ai", "Gopher")
            persistNow()
            withContext(Dispatchers.Main) { load(opened.result.document, opened.result.warnings, DocSession(io.store.newId()), stored = false) }
        }
    }

    // ---- Belgelerim ---------------------------------------------------------

    fun showGallery() {
        if (state.pen != null) finishPen()
        background(tr("Belgeler yükleniyor…")) {
            persistNow()
            val list = io.store.list()
            withContext(Dispatchers.Main) { state = state.copy(gallery = list) }
        }
    }

    fun hideGallery() {
        state = state.copy(gallery = null)
    }

    fun openStored(id: String) {
        if (id == state.session.id) {
            hideGallery()
            return
        }
        if (id in parked) {
            hideGallery()
            switchTab(id)
            return
        }
        background(tr("Açılıyor…")) {
            persistNow()
            val meta = io.store.read(id) ?: throw java.io.IOException(tr("Belge bulunamadı"))
            val doc = io.loadFromStore(id)
            io.store.setCurrent(id)
            withContext(Dispatchers.Main) {
                load(doc, emptyList(), DocSession(meta.id, meta.linkUri, meta.linkFormat, meta.linkWritable, meta.unsaved), stored = true)
            }
        }
    }

    fun deleteStored(id: String) = background(tr("Siliniyor…")) {
        // Belge bir sekmede açıksa önce sekmesi kapanır (yeniden kaydedilmesin).
        withContext(Dispatchers.Main) { dropTab(id) }
        synchronized(saveLock) {
            storedDocs.remove(id)
            io.store.delete(id)
        }
        val list = io.store.list()
        withContext(Dispatchers.Main) { state = state.copy(gallery = list) }
    }

    // ---- Kaydetme -----------------------------------------------------------

    /** Bağlı dosyanın üzerine yazar. Yalnızca [canSaveInPlace] doğruysa çağrılmalıdır. */
    fun save() {
        val link = state.session.linkUri ?: return
        val format = state.session.linkFormat ?: ExportFormat.Ai
        if (state.pen != null) finishPen()
        val doc = present
        val tab = state.session.id
        background(tr("Kaydediliyor…")) {
            try {
                io.export(doc, link, format)
            } catch (e: java.io.IOException) {
                throw java.io.IOException(tr("%s. \"Farklı kaydet\" ile yeni bir dosyaya kaydedin.", e.message))
            }
            withContext(Dispatchers.Main) {
                editTab(tab) { it.copy(session = it.session.copy(unsaved = it.history.present !== doc)) }
                state = state.copy(message = tr("Kaydedildi"))
            }
            persistMeta(tab)
        }
    }

    /** "Farklı kaydet": yeni dosyaya yazar ve belgeyi artık o dosyaya bağlar. */
    fun saveAs(uri: String) {
        if (state.pen != null) finishPen()
        val doc = present
        val tab = state.session.id
        background(tr("Kaydediliyor…")) {
            io.export(doc, uri, ExportFormat.Ai)
            io.persistAccess(uri)
            withContext(Dispatchers.Main) {
                editTab(tab) {
                    it.copy(session = it.session.copy(linkUri = uri, linkFormat = ExportFormat.Ai, linkWritable = true, unsaved = it.history.present !== doc))
                }
                state = state.copy(message = tr("Kaydedildi"))
            }
            stateOf(tab)?.let(::persistState)
            persistMeta(tab)
        }
    }

    /** Başka biçimde bir kopya yazar; belgenin bağlı olduğu dosya değişmez. */
    fun export(uri: String, format: ExportFormat) {
        if (state.pen != null) finishPen()
        val doc = present
        background(tr("Dışa aktarılıyor…")) {
            io.export(doc, uri, format)
            withContext(Dispatchers.Main) { state = state.copy(message = tr("Dışa aktarıldı")) }
        }
    }

    fun placeImage(uri: String) = background(tr("Görsel ekleniyor…")) {
        val image = io.readImage(uri)
        withContext(Dispatchers.Main) {
            val layer = drawingLayer() ?: run {
                state = state.copy(message = tr("Etkin katman kilitli ya da gizli"))
                return@withContext
            }
            // Görsel, görünen alanın ortasına ve yarısını kaplayacak boyda yerleşir.
            val center = viewport.toDocument(Offset(canvasSize.width / 2, canvasSize.height / 2))
            val viewW = canvasSize.width / viewport.scale
            val viewH = canvasSize.height / viewport.scale
            val k = min(viewW * 0.5 / image.width, viewH * 0.5 / image.height).let { if (it.isFinite() && it > 0) it else 1.0 }
            val node = ImageNode(
                image = image,
                transform = Matrix.translate(center.x - image.width * k / 2, center.y - image.height * k / 2) * Matrix.scale(k),
            )
            commit(present.addNode(layer.id, node)) { it.copy(tool = Tool.Select).withSelection(setOf(node.id)) }
        }
    }

    // ---- Vektöre çevirme (görsel izleme) -------------------------------------

    /** İzlemenin ilerlemesi (0-1); arka plan iş parçacığından güncellenir. */
    var traceProgress by mutableFloatStateOf(0f)
        private set

    private var traceJob: Job? = null

    /** Son hesaplanan iz: "Uygula" bunu belgeye ekler. */
    private var tracedGroup: GroupNode? = null

    /** Çözülmüş pikseller ayrıntı düzeyine göre saklanır (ayar değiştikçe görsel yeniden çözülmesin). */
    private var tracePixels: Triple<ImageData, Int, PixelImage>? = null

    /** Seçili görsel için "Vektöre çevir" penceresini açar. */
    fun startTrace() {
        val node = state.selection.singleOrNull()?.let { present.findNode(it) } as? ImageNode ?: return
        state = state.copy(trace = TraceUi(node.id))
        computeTrace(0)
    }

    fun setTracePreset(preset: TracePreset) {
        val ui = state.trace ?: return
        state = state.copy(trace = ui.copy(options = preset.options, detail = preset.detail, preset = preset, busy = true, error = null))
        computeTrace(0)
    }

    /** Ayar değişti: kısa bir beklemeden sonra (sürgü oynarken her adımda değil) yeniden hesaplanır. */
    fun updateTrace(options: TraceOptions, detail: Int) {
        val ui = state.trace ?: return
        if (ui.options == options && ui.detail == detail) return
        state = state.copy(trace = ui.copy(options = options, detail = detail.coerceIn(100, 2000), preset = null, busy = true, error = null))
        computeTrace(350)
    }

    fun cancelTrace() {
        traceJob?.cancel()
        traceJob = null
        tracedGroup = null
        tracePixels = null
        state = state.copy(trace = null)
    }

    /** İzi belgeye ekler: özgün görsel gizlenir (silinmez), yerine aynı konumda yollardan oluşan bir grup gelir. */
    fun applyTrace() {
        val ui = state.trace ?: return
        val group = tracedGroup
        if (ui.busy || group == null) return
        val image = present.findNode(ui.nodeId) as? ImageNode
        val layer = present.layerOf(ui.nodeId)
        traceJob = null
        tracedGroup = null
        tracePixels = null
        if (image == null || layer == null) {
            state = state.copy(trace = null)
            return
        }
        // Görsel katmanın doğrudan çocuğuysa iz hemen üzerine, değilse katmanın en üstüne eklenir.
        val index = layer.children.indexOfFirst { it.id == image.id }
        var doc = present.updateNode(image.id) { it.withVisible(false) }
        doc = doc.updateLayer(layer.id) { l ->
            val children = l.children.toMutableList()
            if (index >= 0) children.add(index + 1, group) else children.add(group)
            l.copy(children = children)
        }
        commit(doc) { it.copy(trace = null, tool = Tool.Select, message = tr("Vektöre çevrildi: %s şekil", group.children.size)).withSelection(setOf(group.id)) }
    }

    private fun computeTrace(delayMillis: Long) {
        traceJob?.cancel()
        val ui = state.trace ?: return
        val node = present.findNode(ui.nodeId) as? ImageNode ?: return
        val outer = present.parentMatrix(node.id)
        traceProgress = 0f
        traceJob = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            try {
                val built = withContext(Dispatchers.Default) {
                    val cached = tracePixels
                    val pixels = if (cached != null && cached.first === node.image && cached.second == ui.detail) cached.third else {
                        io.decodePixels(node.image, ui.detail).also { tracePixels = Triple(node.image, ui.detail, it) }
                    }
                    var shown = 0f
                    val result = Tracer.trace(
                        pixels.argb, pixels.width, pixels.height, ui.options,
                        progress = { p -> if (p - shown >= 0.02) { shown = p.toFloat(); traceProgress = shown } },
                        cancelled = { !isActive },
                    )
                    val children = result.shapes.map { PathNode(subpaths = it.subpaths, fill = Paint.Solid(it.color), fillRule = FillRule.EvenOdd) }
                    // İz piksel koordinatındadır; görselin kendi boyutuna ve belgedeki yerine taşınır.
                    val toImage = Matrix.scale(node.image.width.toDouble() / pixels.width, node.image.height.toDouble() / pixels.height)
                    val group = GroupNode(name = "İz", children = children, transform = outer * node.transform * toImage, opacity = node.opacity)
                    TraceBuilt(group, result, renderTracePreview(children, pixels.width, pixels.height))
                }
                tracedGroup = built.group
                traceProgress = 1f
                state = state.copy(
                    trace = state.trace?.copy(
                        busy = false, preview = built.preview, shapes = built.result.shapes.size,
                        anchors = built.result.anchorCount, colors = built.result.colorCount, error = null,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: TraceCancelled) {
                // yeni ayarlarla yeniden başlatıldı ya da pencere kapandı
            } catch (e: Throwable) {
                tracedGroup = null
                val reason = if (e is OutOfMemoryError) tr("Görsel için yeterli bellek yok") else (e.message ?: tr("İşlem başarısız oldu"))
                state = state.copy(trace = state.trace?.copy(busy = false, error = reason))
            }
        }
    }

    private class TraceBuilt(val group: GroupNode, val result: TraceResult, val preview: ImageBitmap?)

    /** İzin önizlemesi: şekiller, uzun kenarı en çok 720 piksel olan bir görüntüye çizilir. */
    private fun renderTracePreview(children: List<PathNode>, width: Int, height: Int): ImageBitmap? = try {
        val k = minOf(1f, 720f / maxOf(width, height))
        val w = (width * k).toInt().coerceAtLeast(1)
        val h = (height * k).toInt().coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        val canvas = androidx.compose.ui.graphics.Canvas(image)
        canvas.scale(k, k)
        val preview = Document(name = "", artboards = emptyList(), layers = listOf(Layer(name = "", children = children)))
        DocumentRenderer().draw(canvas.nativeCanvas, preview, artboardColor = null)
        image
    } catch (e: Throwable) {
        null
    }

    private fun background(label: String, block: suspend () -> Unit) {
        state = state.copy(busy = label)
        scope.launch {
            try {
                withContext(Dispatchers.Default) { block() }
            } catch (e: Throwable) {
                state = state.copy(message = e.message?.takeIf { it.isNotBlank() } ?: tr("İşlem başarısız oldu"))
            } finally {
                state = state.copy(busy = null)
            }
        }
    }

    // ---- Otomatik kayıt -----------------------------------------------------

    /** Depoya en son yazılan belge; aynı nesneyse yeniden yazmaya gerek yoktur. */
    private val storedDocs = java.util.concurrent.ConcurrentHashMap<String, Document>()
    private val saveLock = Any()

    private fun metaOf(session: DocSession, doc: Document) = StoredDocument(
        id = session.id, name = doc.name, modified = System.currentTimeMillis(), linkUri = session.linkUri,
        linkFormat = session.linkFormat, linkWritable = session.linkWritable, unsaved = session.unsaved,
        thumbnail = io.store.thumbFile(session.id),
    )

    /** Hiç dokunulmamış boş belge depoya yazılmaz; galeri boş "Adsız"larla dolmasın. */
    private fun worthStoring(s: EditorState): Boolean =
        io.store.exists(s.session.id) || s.history.canUndo || s.session.linkUri != null || s.history.present.layers.any { it.children.isNotEmpty() }

    /** Bekleyen değişiklikleri hemen depoya yazar. Arka plan iş parçacığından çağrılır. */
    private fun persistNow() {
        autosaveJob?.cancel()
        val s = state
        if (persistState(s)) io.store.setCurrent(s.session.id)
    }

    /** Verilen belge durumunu (etkin ya da arka sekmedeki) depoya yazar. @return yazıldıysa `true`. */
    private fun persistState(s: EditorState): Boolean {
        val doc = s.history.present
        if (doc === storedDocs[s.session.id] || !worthStoring(s)) return false
        synchronized(saveLock) {
            io.saveToStore(metaOf(s.session, doc), doc)
            storedDocs[s.session.id] = doc
        }
        return true
    }

    /** Arka sekmeye giden ya da kapanan belgeyi ekranı bekletmeden depoya yazar. */
    private fun persistLater(s: EditorState) {
        backgroundScope.launch {
            try { persistState(s) } catch (e: Throwable) { }
        }
    }

    private fun persistMeta(id: String) {
        val s = stateOf(id) ?: return
        if (io.store.exists(id)) io.store.writeMeta(metaOf(s.session, s.history.present))
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(1500)
            withContext(Dispatchers.Default) {
                try { persistNow() } catch (e: Throwable) { /* yer yoksa sessizce geç; bir sonraki değişiklikte yeniden denenir */ }
            }
        }
    }

    private class Restored(val meta: StoredDocument?, val document: Document?, val others: List<StoredDocument>?)

    private fun restoreLastDocument() {
        state = state.copy(busy = tr("Son çalışma açılıyor…"))
        scope.launch {
            val restored = withContext(Dispatchers.Default) {
                try {
                    io.store.migrateLegacy()
                    val id = io.store.current()
                    val meta = id?.let { io.store.read(it) }
                    if (meta != null) Restored(meta, io.loadFromStore(meta.id), null) else Restored(null, null, io.store.list())
                } catch (e: Throwable) {
                    Restored(null, null, try { io.store.list() } catch (e2: Throwable) { emptyList() })
                }
            }
            state = state.copy(busy = null)
            // Kullanıcı bu arada bir şey açtıysa ya da çizdiyse üzerine yazma.
            val untouched = !state.history.canUndo && present.layers.all { it.children.isEmpty() } && state.session.linkUri == null
            val meta = restored.meta
            val doc = restored.document
            if (!untouched) return@launch
            if (meta != null && doc != null) {
                load(doc, emptyList(), DocSession(meta.id, meta.linkUri, meta.linkFormat, meta.linkWritable, meta.unsaved), stored = true, newTab = false)
            } else if (!restored.others.isNullOrEmpty()) {
                // Son belge açılamadı ama başkaları var: galeriyi göster.
                state = state.copy(gallery = restored.others)
            }
        }
    }

    /** Uygulama arka plana giderken bekleyen otomatik kaydı hemen yaz (ekran kapanınca iptal olmayan kapsamda). */
    fun flushAutosave() {
        val others = parked.values.map { it.state }
        backgroundScope.launch {
            try { persistNow() } catch (e: Throwable) { }
            for (s in others) try { persistState(s) } catch (e: Throwable) { }
        }
    }

    /** Pencere kapanırken: bekleyen otomatik kaydı bitene kadar bekler (masaüstü). */
    fun flushAutosaveNow() {
        try { persistNow() } catch (e: Throwable) { }
        for (p in parked.values.toList()) try { persistState(p.state) } catch (e: Throwable) { }
    }

    private val backgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    companion object {
        /** Aynı anda açık tutulan en çok belge sayısı. */
        const val MAX_TABS = 8
    }
}
