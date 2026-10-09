package io.github.mhmmtbg.mobileillustrator.editor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.AnchorRef
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.History
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathFit
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.ZMove
import io.github.mhmmtbg.mobileillustrator.model.addLayer
import io.github.mhmmtbg.mobileillustrator.model.addNode
import io.github.mhmmtbg.mobileillustrator.model.anchorAt
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.model.boundsOf
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
import io.github.mhmmtbg.mobileillustrator.model.ungroup
import io.github.mhmmtbg.mobileillustrator.model.updateLayer
import io.github.mhmmtbg.mobileillustrator.model.updateNode
import io.github.mhmmtbg.mobileillustrator.model.withHandle
import io.github.mhmmtbg.mobileillustrator.model.withLocked
import io.github.mhmmtbg.mobileillustrator.model.withName
import io.github.mhmmtbg.mobileillustrator.model.withOpacity
import io.github.mhmmtbg.mobileillustrator.model.withVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val io = DocumentIo(app)

    var state by mutableStateOf(freshState(Document.blank()))
        private set

    var viewport by mutableStateOf(Viewport())
        private set

    /** Parmak için ekranda sabit kalan dokunma payı (piksel); arayüz yoğunluğa göre ayarlar. */
    var touchTolerancePx: Float = 24f

    private var canvasSize = Size.Zero
    private var fitPending = true
    private var drag: Drag? = null
    private var autosaveJob: Job? = null

    private sealed interface Drag {
        data class Pan(val last: Offset) : Drag
        data class Move(val ids: Set<String>, val start: Vec2) : Drag
        data class Scale(val ids: Set<String>, val bounds: Rect, val handle: Handle) : Drag
        data class Rotate(val ids: Set<String>, val center: Vec2, val startAngle: Double) : Drag
        data class Marquee(val start: Vec2) : Drag
        data class Create(val start: Vec2, val nodeId: String, val layerId: String) : Drag
        data class Pencil(val points: ArrayList<Vec2>, val nodeId: String, val layerId: String) : Drag
        data object PenHandle : Drag
        data class AnchorDrag(val nodeId: String, val ref: AnchorRef, val toLocal: Matrix, val origin: List<SubPath>, val start: Vec2) : Drag
        data class HandleDrag(val nodeId: String, val ref: AnchorRef, val outgoing: Boolean, val toLocal: Matrix, val origin: List<SubPath>) : Drag
    }

    init {
        restoreAutosave()
    }

    private fun freshState(doc: Document) = EditorState(history = History(present = doc), activeLayerId = doc.layers.last().id)

    private val present: Document get() = state.history.present

    // ---- Görünüm ----------------------------------------------------------

    fun onCanvasSize(size: Size) {
        val old = canvasSize
        canvasSize = size
        if (size.width <= 0 || size.height <= 0) return
        if (fitPending) {
            fitPending = false
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
        viewport = Viewport(scale, Offset(canvasSize.width / 2 - c.x.toFloat() * scale, canvasSize.height / 2 - c.y.toFloat() * scale))
    }

    /** İki parmak hareketi: [centroid] etrafında [zoom] kadar yakınlaştırır, [pan] kadar kaydırır. */
    fun transformViewport(centroid: Offset, pan: Offset, zoom: Float) {
        val v = viewport
        val newScale = (v.scale * zoom).coerceIn(Viewport.MIN_SCALE, Viewport.MAX_SCALE)
        val k = newScale / v.scale
        viewport = Viewport(newScale, centroid - (centroid - v.offset) * k + pan)
    }

    private fun tolerance(): Double = touchTolerancePx / viewport.scale.toDouble()

    // ---- Geçmiş -----------------------------------------------------------

    private fun commit(doc: Document, then: (EditorState) -> EditorState = { it }) {
        val changed = doc != present
        state = then(state.copy(history = state.history.push(doc), preview = null))
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
            nodeEdit = state.nodeEdit?.takeIf { doc.findNode(it.nodeId) is PathNode }?.copy(anchor = null),
            activeLayerId = if (doc.findLayer(state.activeLayerId) != null) state.activeLayerId else doc.layers.last().id,
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
            else -> {}
        }
    }

    private fun defaultFontSize(): Double {
        val h = present.artboardBounds()?.height ?: 1000.0
        return (h / 18).coerceIn(8.0, 400.0).let { Math.round(it).toDouble() }
    }

    fun onDrag(startScreen: Offset, currentScreen: Offset) {
        val d = drag ?: beginDrag(startScreen).also { drag = it }
        val base = present
        val now = viewport.toDocument(currentScreen)
        when (d) {
            is Drag.Pan -> {
                viewport = viewport.copy(offset = viewport.offset + (currentScreen - d.last))
                drag = d.copy(last = currentScreen)
            }
            is Drag.Move -> {
                val delta = now - d.start
                state = state.copy(preview = base.transformNodes(d.ids, Matrix.translate(delta.x, delta.y)))
            }
            is Drag.Scale -> state = state.copy(preview = base.transformNodes(d.ids, scaleMatrix(d, now)))
            is Drag.Rotate -> {
                val angle = atan2(now.y - d.center.y, now.x - d.center.x) - d.startAngle
                val m = Matrix.translate(d.center.x, d.center.y) * Matrix.rotate(angle) * Matrix.translate(-d.center.x, -d.center.y)
                state = state.copy(preview = base.transformNodes(d.ids, m))
            }
            is Drag.Marquee -> state = state.copy(marquee = Rect.of(d.start, now))
            is Drag.Create -> state = state.copy(preview = base.addNode(d.layerId, shapeNode(d, now)))
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
        when (d) {
            is Drag.Marquee -> {
                val rect = state.marquee
                state = state.copy(marquee = null)
                if (rect != null) state = state.withSelection(present.nodesIn(rect).mapTo(HashSet()) { it.id })
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
        if (state.marquee != null) state = state.copy(marquee = null)
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
                return Drag.Move(ids, start)
            }
            Tool.Direct -> return beginDirectDrag(start, startScreen)
            Tool.Pen -> {
                penTap(start)
                return if (state.pen != null) Drag.PenHandle else Drag.Pan(startScreen)
            }
            Tool.Text -> return Drag.Pan(startScreen)
            Tool.Pencil, Tool.Rectangle, Tool.Ellipse, Tool.Line -> {
                val layer = drawingLayer() ?: run {
                    state = state.copy(message = "Etkin katman kilitli ya da gizli")
                    return Drag.Pan(startScreen)
                }
                return if (state.tool == Tool.Pencil) Drag.Pencil(arrayListOf(start), newId(), layer.id) else Drag.Create(start, newId(), layer.id)
            }
        }
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
        val stroke = s.stroke?.let { Stroke(Paint.Solid(it), s.strokeWidth) }
        return when (s.tool) {
            Tool.Line -> PathNode(
                id = d.nodeId,
                name = "Çizgi",
                subpaths = listOf(Shapes.line(d.start, now)),
                stroke = Stroke(Paint.Solid(s.stroke ?: Rgba.Black), s.strokeWidth),
                opacity = s.opacity,
            )
            Tool.Ellipse -> PathNode(
                id = d.nodeId, name = "Elips", subpaths = listOf(Shapes.ellipse(Rect.of(d.start, now))),
                fill = fill, stroke = stroke, opacity = s.opacity,
            )
            else -> PathNode(
                id = d.nodeId, name = "Dikdörtgen", subpaths = listOf(Shapes.rect(Rect.of(d.start, now))),
                fill = fill, stroke = stroke, opacity = s.opacity,
            )
        }
    }

    private fun freehandStroke() =
        Stroke(Paint.Solid(state.stroke ?: Rgba.Black), state.strokeWidth, cap = LineCap.Round, join = LineJoin.Round)

    // ---- Kalem ------------------------------------------------------------

    private fun penTap(p: Vec2) {
        val pen = state.pen
        if (pen == null) {
            val layer = drawingLayer() ?: run {
                state = state.copy(message = "Etkin katman kilitli ya da gizli")
                return
            }
            state = state.copy(selection = emptySet(), nodeEdit = null)
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
            stroke = Stroke(Paint.Solid(state.stroke ?: Rgba.Black), state.strokeWidth),
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
            stroke = state.stroke?.let { Stroke(Paint.Solid(it), state.strokeWidth) }
                ?: if (state.fill == null) Stroke(Paint.Solid(Rgba.Black), state.strokeWidth) else null,
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
            state.copy(selection = emptySet(), nodeEdit = null)
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
        if (tool == Tool.Pen || tool == Tool.Pencil) s = s.copy(selection = emptySet())
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
        if (state.selection.isEmpty()) return
        onDragCancel()
        val doc = state.selection.fold(present) { d, id -> d.removeNode(id) }
        commit(doc) { it.copy(selection = emptySet(), nodeEdit = null) }
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
            state = state.copy(message = "Gruplamak için en az iki nesne seçin")
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
                    node.restyled({ it }, { st -> color?.let { c -> (st ?: Stroke(Paint.Solid(c), width)).copy(paint = Paint.Solid(c)) } })
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

    fun setOpacity(opacity: Double, done: Boolean) {
        state = state.copy(opacity = opacity)
        state.pen?.let { setPen(it); return }
        val doc = state.selection.fold(present) { d, id -> d.updateNode(id) { it.withOpacity(opacity) } }
        if (done) commit(doc) else state = state.copy(preview = doc.takeIf { it != present })
    }

    /** Seçilen nesnenin stilini araç çubuğuna yansıtır; gradyanlar göstergede ilk renkleriyle görünür. */
    private fun EditorState.withSelection(ids: Set<String>): EditorState {
        val doc = history.present
        val node = ids.singleOrNull()?.let { doc.findNode(it) } ?: return copy(selection = ids, nodeEdit = nodeEdit?.takeIf { it.nodeId in ids })
        val sample = node.styleSample()
        val f = sample?.first
        val st = sample?.second
        return copy(
            selection = ids,
            nodeEdit = nodeEdit?.takeIf { it.nodeId in ids },
            fill = if (sample == null) fill else (f as? Paint.Solid)?.color ?: if (f == null) null else fill,
            stroke = if (sample == null) stroke else (st?.paint as? Paint.Solid)?.color ?: if (st == null) null else stroke,
            strokeWidth = st?.width ?: strokeWidth,
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
            state = state.copy(message = "Son katman silinemez")
            return
        }
        val doc = present.removeLayer(id)
        commit(doc) { s ->
            s.copy(
                selection = s.selection.filterTo(HashSet()) { doc.findNode(it) != null },
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
                fun collect(nodes: List<io.github.mhmmtbg.mobileillustrator.model.Node>) {
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
    fun selectNode(id: String) {
        val node = present.findNode(id) ?: return
        if (state.pen != null) finishPen()
        state = state.withSelection(setOf(id)).copy(
            nodeEdit = if (state.tool == Tool.Direct && node is PathNode) NodeEdit(id) else null,
            tool = if (state.tool == Tool.Direct) Tool.Direct else Tool.Select,
        )
    }

    // ---- Metin ------------------------------------------------------------

    fun editSelectedText() {
        val id = state.selection.singleOrNull() ?: return
        val t = present.findNode(id) as? TextNode ?: return
        state = state.copy(textPrompt = TextPrompt(t.id, Vec2.Zero, t.text, t.fontSize, t.fontFamily, t.bold, t.italic))
    }

    fun dismissTextPrompt() {
        state = state.copy(textPrompt = null)
    }

    fun confirmText(prompt: TextPrompt) {
        state = state.copy(textPrompt = null)
        val text = prompt.text.replace('\n', ' ').trim()
        if (text.isEmpty()) return
        val size = prompt.fontSize.coerceIn(1.0, 5000.0)
        if (prompt.nodeId != null) {
            commit(
                present.updateNode(prompt.nodeId) { n ->
                    (n as? TextNode)?.copy(
                        text = text, name = text.take(24), fontSize = size, fontFamily = prompt.fontFamily,
                        bold = prompt.bold, italic = prompt.italic, measuredWidth = null,
                    ) ?: n
                },
            )
            return
        }
        val layer = drawingLayer() ?: run {
            state = state.copy(message = "Etkin katman kilitli ya da gizli")
            return
        }
        val node = TextNode(
            name = text.take(24), text = text, fontSize = size, fontFamily = prompt.fontFamily, bold = prompt.bold, italic = prompt.italic,
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

    fun newDocument(width: Double, height: Double) {
        if (state.pen != null) cancelPen()
        load(Document.blank(width.coerceIn(1.0, 20000.0), height.coerceIn(1.0, 20000.0)), emptyList())
    }

    private fun load(doc: Document, warnings: List<String>) {
        drag = null
        state = freshState(doc).copy(warnings = warnings, layersOpen = state.layersOpen)
        fitPending = true
        if (canvasSize.width > 0) { fitPending = false; fitToScreen() }
        scheduleAutosave()
    }

    fun open(uri: Uri) = background("Açılıyor…") {
        val r = io.open(uri)
        withContext(Dispatchers.Main) { load(r.document, r.warnings) }
    }

    fun openSample() = background("Açılıyor…") {
        val r = io.openAsset("samples/gopher.ai", "Gopher")
        withContext(Dispatchers.Main) { load(r.document, r.warnings) }
    }

    fun export(uri: Uri, format: ExportFormat) {
        if (state.pen != null) finishPen()
        val doc = present
        background("Kaydediliyor…") {
            io.export(doc, uri, format)
            withContext(Dispatchers.Main) { state = state.copy(message = "Kaydedildi") }
        }
    }

    fun placeImage(uri: Uri) = background("Görsel ekleniyor…") {
        val image = io.readImage(uri)
        withContext(Dispatchers.Main) {
            val layer = drawingLayer() ?: run {
                state = state.copy(message = "Etkin katman kilitli ya da gizli")
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

    private fun background(label: String, block: suspend () -> Unit) {
        state = state.copy(busy = label)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { block() }
            } catch (e: Throwable) {
                state = state.copy(message = e.message?.takeIf { it.isNotBlank() } ?: "İşlem başarısız oldu")
            } finally {
                state = state.copy(busy = null)
            }
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1500)
            val doc = present
            withContext(Dispatchers.Default) {
                try { io.autosave(doc) } catch (e: Throwable) { /* yer yoksa sessizce geç */ }
            }
        }
    }

    private fun restoreAutosave() {
        if (!io.hasAutosave()) return
        state = state.copy(busy = "Son çalışma açılıyor…")
        viewModelScope.launch {
            val doc = withContext(Dispatchers.Default) {
                try { io.loadAutosave() } catch (e: Throwable) { null }
            }
            state = state.copy(busy = null)
            // Kullanıcı bu arada bir şey açtıysa ya da çizdiyse üzerine yazma.
            if (doc != null && !state.history.canUndo && present.layers.all { it.children.isEmpty() }) {
                drag = null
                state = freshState(doc)
                fitPending = true
                if (canvasSize.width > 0) { fitPending = false; fitToScreen() }
            }
        }
    }

    /** Uygulama arka plana giderken bekleyen otomatik kaydı hemen yaz. */
    fun flushAutosave() {
        val job = autosaveJob ?: return
        if (!job.isActive) return
        job.cancel()
        val doc = present
        viewModelScope.launch(Dispatchers.Default) {
            try { io.autosave(doc) } catch (e: Throwable) { }
        }
    }
}
