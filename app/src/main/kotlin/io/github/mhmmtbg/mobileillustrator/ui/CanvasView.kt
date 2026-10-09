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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.Viewport
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Vec2
import io.github.mhmmtbg.mobileillustrator.model.bounds
import io.github.mhmmtbg.mobileillustrator.model.findNode
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer

/**
 * Tuval. Tek parmak etkin araca gider; iki parmak her zaman kaydırır ve yakınlaştırır.
 * İkinci parmak indiğinde yarım kalan araç işlemi iptal edilir.
 */
@Composable
fun CanvasView(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val renderer = remember { DocumentRenderer() }
    val density = LocalDensity.current
    val tolerancePx = with(density) { 12.dp.toPx() }
    SideEffect { vm.touchTolerancePx = tolerancePx }

    Canvas(
        modifier
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
            for (ab in document.artboards) {
                outline(ab.bounds, viewport, Color(0x66000000), 1.dp.toPx())
            }
            for (id in state.selection) {
                val bounds = document.findNode(id)?.bounds() ?: continue
                selectionBox(bounds, viewport)
            }
        }
    }
}

private fun DrawScope.outline(rect: Rect, viewport: Viewport, color: Color, width: Float) {
    val tl = viewport.toScreen(Vec2(rect.left, rect.top))
    val size = Size((rect.width * viewport.scale).toFloat(), (rect.height * viewport.scale).toFloat())
    drawRect(color, tl, size, style = Stroke(width))
}

private fun DrawScope.selectionBox(rect: Rect, viewport: Viewport) {
    outline(rect, viewport, AppColors.Selection, 1.5.dp.toPx())
    val half = 4.dp.toPx()
    for (corner in rect.corners) {
        val c = viewport.toScreen(corner)
        val tl = Offset(c.x - half, c.y - half)
        drawRect(Color.White, tl, Size(half * 2, half * 2))
        drawRect(AppColors.Selection, tl, Size(half * 2, half * 2), style = Stroke(1.5.dp.toPx()))
    }
}
