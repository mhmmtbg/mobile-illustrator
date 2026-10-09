package io.github.mhmmtbg.mobileillustrator.ui

import io.github.mhmmtbg.mobileillustrator.model.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.artboardBounds
import io.github.mhmmtbg.mobileillustrator.model.nodeCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    // Karmaşık belgelerde kaydırma ve yakınlaştırma sırasında binlerce yolu her karede yeniden çizmek
    // yerine, arka planda hazırlanmış bir bit eşlem gösterilir; hareket bitince vektör çizime dönülür.
    val backdrop = remember { DragBackdrop() }
    var raster by remember { mutableStateOf<RasterCache?>(null) }
    var gesturing by remember { mutableStateOf(false) }
    val committed = vm.state.history.present
    LaunchedEffect(committed) {
        raster = null
        delay(350)
        raster = withContext(Dispatchers.Default) { RasterCache.build(committed) }
    }

    Canvas(
        modifier
            .semantics { contentDescription = tr("Tuval") }
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
                                    gesturing = true
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
                    gesturing = false
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
            val cached = raster?.takeIf { gesturing && it.document === document }
            val moving = vm.movingIds
            val base = state.history.present
            // Kalabalık belgede nesne sürüklerken: geri kalan her şey bir kez görüntüye çizilir, her karede
            // yalnızca sürüklenen nesneler yeniden çizilir. Sürükleme boyunca bu nesneler en üstte görünür.
            val still = if (moving.isNotEmpty() && state.preview != null && cached == null) {
                backdrop.get(base, moving, viewport, size.width.toInt(), size.height.toInt())
            } else {
                backdrop.clear()
                null
            }
            val visible = Rect.of(viewport.toDocument(Offset.Zero), viewport.toDocument(Offset(size.width, size.height)))
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                if (still != null) native.drawBitmap(still, 0f, 0f, null)
                val count = native.save()
                native.translate(viewport.offset.x, viewport.offset.y)
                native.scale(viewport.scale, viewport.scale)
                when {
                    cached != null -> cached.draw(native)
                    still != null -> for (layer in document.layers) {
                        if (!layer.visible) continue
                        for (node in layer.children) if (node.id in moving) renderer.drawNode(native, node)
                    }
                    else -> renderer.draw(native, document, visible = visible)
                }
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

            // Akıllı kılavuzlar: yapışılan kenar ya da orta çizgisi tuval boyunca gösterilir.
            state.guides?.let { g ->
                val guide = Color(0xFFFF2D9B)
                for (x in g.guidesX) {
                    val sx = viewport.toScreen(Vec2(x, 0.0)).x
                    drawLine(guide, Offset(sx, 0f), Offset(sx, size.height), 1.dp.toPx())
                }
                for (y in g.guidesY) {
                    val sy = viewport.toScreen(Vec2(0.0, y)).y
                    drawLine(guide, Offset(0f, sy), Offset(size.width, sy), 1.dp.toPx())
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

/** Belgenin çalışma yüzeylerini kapsayan, önceden çizilmiş görüntüsü. */
private class RasterCache(val document: Document, private val bitmap: android.graphics.Bitmap, private val bounds: Rect) {
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val dst = android.graphics.RectF(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())

    fun draw(canvas: android.graphics.Canvas) {
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    companion object {
        private const val HEAVY_NODE_COUNT = 400
        private const val MAX_SIDE = 2048.0

        /** Hafif belgeler için `null` döner: onları doğrudan çizmek zaten hızlıdır. */
        fun build(document: Document): RasterCache? {
            if (document.nodeCount() < HEAVY_NODE_COUNT) return null
            val box = document.artboardBounds()?.let { it.inflate(maxOf(it.width, it.height) * 0.08) } ?: return null
            if (box.width <= 0 || box.height <= 0) return null
            val scale = MAX_SIDE / maxOf(box.width, box.height)
            val w = (box.width * scale).toInt().coerceAtLeast(1)
            val h = (box.height * scale).toInt().coerceAtLeast(1)
            return try {
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.scale(scale.toFloat(), scale.toFloat())
                canvas.translate(-box.left.toFloat(), -box.top.toFloat())
                // Arka plan iş parçacığında çalışır; ekran çizicisiyle durum paylaşmamak için ayrı bir çizici kullanılır.
                DocumentRenderer().draw(canvas, document)
                RasterCache(document, bmp, box)
            } catch (e: Throwable) {
                null // bellek yetmezse önbelleksiz devam
            }
        }
    }
}

/** Sürükleme sırasında yerinde duran nesnelerin, ekran boyutunda bir kez çizilmiş görüntüsü. */
private class DragBackdrop {
    private var bitmap: android.graphics.Bitmap? = null
    private var base: Document? = null
    private var ids: Set<String>? = null
    private var viewport: Viewport? = null
    private val renderer = DocumentRenderer()

    fun clear() {
        if (bitmap == null) return
        bitmap = null
        base = null
        ids = null
    }

    /** Hafif belgelerde `null` döner; onlarda her şeyi her karede çizmek zaten akıcıdır. */
    fun get(base: Document, ids: Set<String>, viewport: Viewport, width: Int, height: Int): android.graphics.Bitmap? {
        val have = bitmap
        if (have != null && this.base === base && this.ids === ids && this.viewport == viewport && have.width == width && have.height == height) return have
        if (width <= 0 || height <= 0) return null
        if (this.base !== base && base.layers.sumOf { it.children.size } < HEAVY) return null
        return try {
            val bmp = if (have != null && have.width == width && have.height == height) have else android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(0xFF3A3A3D.toInt())
            canvas.translate(viewport.offset.x, viewport.offset.y)
            canvas.scale(viewport.scale, viewport.scale)
            val visible = Rect.of(viewport.toDocument(Offset.Zero), viewport.toDocument(Offset(width.toFloat(), height.toFloat())))
            renderer.draw(canvas, base, visible = visible, skip = ids)
            bitmap = bmp
            this.base = base
            this.ids = ids
            this.viewport = viewport
            bmp
        } catch (e: Throwable) {
            null
        }
    }

    private companion object {
        const val HEAVY = 300
    }
}
