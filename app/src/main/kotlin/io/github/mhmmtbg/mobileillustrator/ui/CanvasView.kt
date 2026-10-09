package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.Handle
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.editor.Viewport
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.boundsOf
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer

/**
 * Tuval. Tek parmak etkin araca gider; iki parmak her zaman kaydırır ve yakınlaştırır.
 * İkinci parmak indiğinde yarım kalan araç işlemi iptal edilir.
 */
@Composable
fun CanvasView(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val renderer = remember { DocumentRenderer() }
    val density = LocalDensity.current
    val tolerancePx = with(density) { 14.dp.toPx() }
    SideEffect { vm.touchTolerancePx = tolerancePx }

    Canvas(
        modifier
            .semantics { contentDescription = "Tuval" }
            .onSizeChanged { vm.onCanvasSize(it.toSize()) }
            .pointerInput(vm) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var multiTouch = false
                    var dragging = false
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.isEmpty() -> finished = true
                            pressed.size >= 2 -> {
                                if (!multiTouch) {
                                    multiTouch = true
                                    vm.onDragCancel()
                                }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (centroid != Offset.Unspecified) vm.transformViewport(centroid, pan, zoom)
                                event.changes.forEach { it.consume() }
                            }
                            !multiTouch -> {
                                val change = pressed.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    if (!dragging && (change.position - down.position).getDistance() > slop) {
                                        dragging = true
                                    }
                                    if (dragging) {
                                        vm.onDrag(down.position, change.position)
                                        change.consume()
                                    }
                                }
                            }
                            // İki parmaktan biri kalktıysa kalan parmak bir şey çizmesin.
                        }
                    }
                    if (!multiTouch) {
                        if (dragging) vm.onDragEnd() else vm.onTap(down.position)
                    }
                }
            },
    ) {
        val state = vm.state
        val viewport = vm.viewport
        val document = state.document

        clipRect {
            drawRect(AppColors.Pasteboard)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val count = native.save()
                native.translate(viewport.offset.x, viewport.offset.y)
                native.scale(viewport.scale, viewport.scale)
                renderer.draw(native, document)
                native.restoreToCount(count)
            }
            for (ab in document.artboards) outline(ab.bounds, viewport, Color(0x66000000), 1.dp.toPx())

            val editing = vm.editedPath()
            if (state.tool == Tool.Direct && editing != null) {
                val (node, world) = editing
                val selected = state.nodeEdit?.anchor
                // Yolun iskeleti
                for (sp in node.subpaths) {
                    val pts = sp.transformed(world).flatten(12)
                    for (i in 0 until pts.size - 1) {
                        drawLine(AppColors.Selection, viewport.toScreen(pts[i]), viewport.toScreen(pts[i + 1]), 1.dp.toPx())
                    }
                    if (sp.closed && pts.size > 1) {
                        drawLine(AppColors.Selection, viewport.toScreen(pts.last()), viewport.toScreen(pts.first()), 1.dp.toPx())
                    }
                }
                for ((si, sp) in node.subpaths.withIndex()) {
                    for ((ai, a) in sp.anchors.withIndex()) {
                        val p = viewport.toScreen(world.apply(a.point))
                        val isSel = selected != null && selected.subpath == si && selected.index == ai
                        if (isSel) {
                            for (h in listOfNotNull(a.handleIn, a.handleOut)) {
                                val hp = viewport.toScreen(world.apply(h))
                                drawLine(AppColors.Selection, p, hp, 1.dp.toPx())
                                drawCircle(Color.White, 4.5.dp.toPx(), hp)
                                drawCircle(AppColors.Selection, 4.5.dp.toPx(), hp, style = Stroke(1.5.dp.toPx()))
                            }
                        }
                        anchorMark(p, filled = isSel)
                    }
                }
            } else if (state.selection.isNotEmpty() && state.pen == null) {
                document.boundsOf(state.selection)?.let { selectionBox(it, viewport, vm.rotateHandleOffsetPx(), state.tool == Tool.Select) }
            }

            state.pen?.let { pen ->
                for ((i, a) in pen.anchors.withIndex()) {
                    val p = viewport.toScreen(a.point)
                    if (i == pen.anchors.size - 1) {
                        for (h in listOfNotNull(a.handleIn, a.handleOut)) {
                            val hp = viewport.toScreen(h)
                            drawLine(AppColors.Selection, p, hp, 1.dp.toPx())
                            drawCircle(AppColors.Selection, 3.5.dp.toPx(), hp)
                        }
                    }
                    anchorMark(p, filled = i == pen.anchors.size - 1)
                }
                // İlk düğümün çevresindeki halka: buraya dokunmak yolu kapatır.
                if (pen.anchors.size >= 2) {
                    drawCircle(AppColors.Accent, 9.dp.toPx(), viewport.toScreen(pen.anchors[0].point), style = Stroke(1.5.dp.toPx()))
                }
            }

            state.marquee?.let { m ->
                val tl = viewport.toScreen(Vec2(m.left, m.top))
                val size = Size((m.width * viewport.scale).toFloat(), (m.height * viewport.scale).toFloat())
                drawRect(AppColors.Selection.copy(alpha = 0.12f), tl, size)
                drawRect(
                    AppColors.Selection, tl, size,
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
        }
    }
}

private fun DrawScope.anchorMark(p: Offset, filled: Boolean) {
    val half = 4.dp.toPx()
    val tl = Offset(p.x - half, p.y - half)
    drawRect(if (filled) AppColors.Selection else Color.White, tl, Size(half * 2, half * 2))
    drawRect(AppColors.Selection, tl, Size(half * 2, half * 2), style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.outline(rect: Rect, viewport: Viewport, color: Color, width: Float) {
    val tl = viewport.toScreen(Vec2(rect.left, rect.top))
    val size = Size((rect.width * viewport.scale).toFloat(), (rect.height * viewport.scale).toFloat())
    drawRect(color, tl, size, style = Stroke(width))
}

private fun DrawScope.selectionBox(rect: Rect, viewport: Viewport, rotateOffset: Float, handles: Boolean) {
    outline(rect, viewport, AppColors.Selection, 1.5.dp.toPx())
    if (!handles) return
    val top = viewport.toScreen(Handle.N.on(rect))
    val rot = Offset(top.x, top.y - rotateOffset)
    drawLine(AppColors.Selection, top, rot, 1.dp.toPx())
    drawCircle(Color.White, 6.dp.toPx(), rot)
    drawCircle(AppColors.Selection, 6.dp.toPx(), rot, style = Stroke(1.5.dp.toPx()))
    val small = rect.width * viewport.scale < 56.dp.toPx() || rect.height * viewport.scale < 56.dp.toPx()
    for (h in Handle.entries) {
        if (small && !h.isCorner) continue
        anchorMark(viewport.toScreen(h.on(rect)), filled = false)
    }
}
