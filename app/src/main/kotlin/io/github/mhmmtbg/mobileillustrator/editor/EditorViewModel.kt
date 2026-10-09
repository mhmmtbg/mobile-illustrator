package io.github.mhmmtbg.mobileillustrator.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.History
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.addNode
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.model.findLayer
import io.github.mhmmtbg.mobileillustrator.model.findNode
import io.github.mhmmtbg.mobileillustrator.model.hitTest
import io.github.mhmmtbg.mobileillustrator.model.mapPaths
import io.github.mhmmtbg.mobileillustrator.model.newId
import io.github.mhmmtbg.mobileillustrator.model.removeNode
import io.github.mhmmtbg.mobileillustrator.model.transformedBy
import io.github.mhmmtbg.mobileillustrator.model.updateNode
import kotlin.math.min

class EditorViewModel : ViewModel() {

    var state by mutableStateOf(
        sampleDocument().let { doc -> EditorState(history = History(present = doc), activeLayerId = doc.layers.last().id) },
    )
        private set

    var viewport by mutableStateOf(Viewport())
        private set

    private var canvasSize = Size.Zero
    private var fittedOnce = false
    private var drag: Drag? = null

    private sealed interface Drag {
        /** Boş alanda tek parmak: tuvali kaydırır. */
        data class Pan(val last: Offset) : Drag
        data class Move(val ids: Set<String>, val start: Vec2) : Drag
        data class Create(val start: Vec2, val nodeId: String, val layerId: String) : Drag
    }

    // ---- Görünüm ----------------------------------------------------------

    fun onCanvasSize(size: Size) {
        canvasSize = size
        if (!fittedOnce && size.width > 0 && size.height > 0) {
            fittedOnce = true
            fitToScreen()
        }
    }

    fun fitToScreen() {
        val bounds = state.document.artboardBounds() ?: return
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || bounds.width <= 0 || bounds.height <= 0) return
        val margin = 0.92f
        val scale = min(canvasSize.width / bounds.width.toFloat(), canvasSize.height / bounds.height.toFloat()) * margin
        val c = bounds.center
        viewport = Viewport(
            scale = scale,
            offset = Offset(
                canvasSize.width / 2 - c.x.toFloat() * scale,
                canvasSize.height / 2 - c.y.toFloat() * scale,
            ),
        )
    }

    /** İki parmak hareketi: [centroid] etrafında [zoom] kadar yakınlaştırır, [pan] kadar kaydırır. */
    fun transformViewport(centroid: Offset, pan: Offset, zoom: Float) {
        val v = viewport
        val newScale = (v.scale * zoom).coerceIn(Viewport.MIN_SCALE, Viewport.MAX_SCALE)
        val k = newScale / v.scale
        viewport = Viewport(newScale, centroid - (centroid - v.offset) * k + pan)
    }

    // ---- İşaretçi ---------------------------------------------------------

    fun onTap(screen: Offset) {
        if (state.tool != Tool.Select) return
        val hit = state.history.present.hitTest(viewport.toDocument(screen), tolerance())
        state = state.withSelection(hit?.let { setOf(it.id) } ?: emptySet())
    }

    fun onDrag(startScreen: Offset, currentScreen: Offset) {
        val d = drag ?: beginDrag(startScreen).also { drag = it }
        val base = state.history.present
        when (d) {
            is Drag.Pan -> {
                viewport = viewport.copy(offset = viewport.offset + (currentScreen - d.last))
                drag = d.copy(last = currentScreen)
            }
            is Drag.Move -> {
                val delta = viewport.toDocument(currentScreen) - d.start
                val m = Matrix.translate(delta.x, delta.y)
                state = state.copy(preview = d.ids.fold(base) { doc, id -> doc.updateNode(id) { it.transformedBy(m) } })
            }
            is Drag.Create -> {
                val now = viewport.toDocument(currentScreen)
                val s = state
                val node = when (s.tool) {
                    Tool.Line -> PathNode(
                        id = d.nodeId,
                        name = "Çizgi",
                        subpaths = listOf(Shapes.line(d.start, now)),
                        stroke = Stroke(Paint.Solid(s.stroke ?: Rgba.Black), s.strokeWidth),
                    )
                    Tool.Ellipse -> PathNode(
                        id = d.nodeId,
                        name = "Elips",
                        subpaths = listOf(Shapes.ellipse(Rect.of(d.start, now))),
                        fill = s.fill?.let(Paint::Solid),
                        stroke = s.stroke?.let { Stroke(Paint.Solid(it), s.strokeWidth) },
                    )
                    else -> PathNode(
                        id = d.nodeId,
                        name = "Dikdörtgen",
                        subpaths = listOf(Shapes.rect(Rect.of(d.start, now))),
                        fill = s.fill?.let(Paint::Solid),
                        stroke = s.stroke?.let { Stroke(Paint.Solid(it), s.strokeWidth) },
                    )
                }
                state = s.copy(preview = base.addNode(d.layerId, node))
            }
        }
    }

    fun onDragEnd() {
        val d = drag
        drag = null
        val preview = state.preview ?: return
        state = when (d) {
            is Drag.Create -> state.copy(history = state.history.push(preview), preview = null).withSelection(setOf(d.nodeId))
            else -> state.copy(history = state.history.push(preview), preview = null)
        }
    }

    /** İkinci parmak indi ya da hareket iptal edildi: yarım kalan işlem atılır. */
    fun onDragCancel() {
        drag = null
        if (state.preview != null) state = state.copy(preview = null)
    }

    private fun beginDrag(startScreen: Offset): Drag {
        val start = viewport.toDocument(startScreen)
        val doc = state.history.present
        if (state.tool == Tool.Select) {
            val hit = doc.hitTest(start, tolerance()) ?: return Drag.Pan(startScreen)
            val ids = if (hit.id in state.selection) state.selection else setOf(hit.id)
            if (ids != state.selection) state = state.withSelection(ids)
            return Drag.Move(ids, start)
        }
        val layer = doc.findLayer(state.activeLayerId)?.takeIf { it.visible && !it.locked }
            ?: doc.layers.lastOrNull { it.visible && !it.locked }
            ?: return Drag.Pan(startScreen)
        return Drag.Create(start, newId(), layer.id)
    }

    /** Parmak için ekranda sabit kalan dokunma payı, belge birimine çevrilmiş. */
    private fun tolerance(): Double = touchTolerancePx / viewport.scale.toDouble()

    /** Ekran yoğunluğuna göre arayüz tarafından ayarlanır. */
    var touchTolerancePx: Float = 24f

    // ---- Komutlar ---------------------------------------------------------

    fun selectTool(tool: Tool) {
        onDragCancel()
        state = state.copy(tool = tool)
    }

    fun selectPaintTarget(target: PaintTarget) {
        state = state.copy(paintTarget = target)
    }

    fun undo() = replaceHistory(state.history.undo())

    fun redo() = replaceHistory(state.history.redo())

    private fun replaceHistory(h: History<Document>) {
        onDragCancel()
        state = state.copy(history = h, selection = state.selection.filterTo(HashSet()) { h.present.findNode(it) != null })
    }

    fun deleteSelection() {
        if (state.selection.isEmpty()) return
        onDragCancel()
        val doc = state.selection.fold(state.history.present) { d, id -> d.removeNode(id) }
        state = state.copy(history = state.history.push(doc), selection = emptySet())
    }

    /** Etkin hedefin (dolgu ya da kontur) rengini ayarlar; `null` "yok" demektir. */
    fun setColor(color: Rgba?) {
        onDragCancel()
        val s = state
        val width = s.strokeWidth
        val doc = when (s.paintTarget) {
            PaintTarget.Fill -> restyleSelection(s) { it.copy(fill = color?.let(Paint::Solid)) }
            PaintTarget.Stroke -> restyleSelection(s) { p ->
                p.copy(stroke = color?.let { c -> (p.stroke ?: Stroke(Paint.Solid(c), width)).copy(paint = Paint.Solid(c)) })
            }
        }
        state = when (s.paintTarget) {
            PaintTarget.Fill -> s.copy(fill = color, history = s.history.push(doc))
            PaintTarget.Stroke -> s.copy(stroke = color, history = s.history.push(doc))
        }
    }

    /** Kaydırıcı sürüklenirken [commit] `false` gelir; bırakılınca tek bir geçmiş kaydı yazılır. */
    fun setStrokeWidth(width: Double, commit: Boolean) {
        val s = state.copy(strokeWidth = width)
        val doc = restyleSelection(s) { p -> p.copy(stroke = p.stroke?.copy(width = width)) }
        state = if (commit) {
            s.copy(history = s.history.push(doc), preview = null)
        } else {
            s.copy(preview = doc.takeIf { it != s.history.present })
        }
    }

    private fun restyleSelection(s: EditorState, f: (PathNode) -> PathNode): Document =
        s.selection.fold(s.history.present) { doc, id -> doc.updateNode(id) { it.mapPaths(f) } }

    /** Seçilen nesnenin stilini araç çubuğuna yansıtır; gradyan dolgular göstergeyi değiştirmez. */
    private fun EditorState.withSelection(ids: Set<String>): EditorState {
        val path = ids.singleOrNull()?.let { history.present.findNode(it) } as? PathNode
            ?: return copy(selection = ids)
        val f = path.fill
        val st = path.stroke
        return copy(
            selection = ids,
            fill = if (f == null) null else (f as? Paint.Solid)?.color ?: fill,
            stroke = if (st == null) null else (st.paint as? Paint.Solid)?.color ?: stroke,
            strokeWidth = st?.width ?: strokeWidth,
        )
    }
}
