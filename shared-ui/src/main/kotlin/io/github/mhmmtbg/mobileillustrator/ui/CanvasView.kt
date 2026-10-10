package io.github.mhmmtbg.mobileillustrator.ui

import io.github.mhmmtbg.mobileillustrator.model.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
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
    val imagePaint = remember { Paint() }
    val density = LocalDensity.current
    val tolerancePx = with(density) { 14.dp.toPx() }
    SideEffect { vm.touchTolerancePx = tolerancePx }

    // Karmaşık belgelerde kaydırma ve yakınlaştırma sırasında binlerce yolu her karede yeniden çizmek
    // yerine, arka planda hazırlanmış bir bit eşlem gösterilir; hareket bitince vektör çizime dönülür.
    val cacheScope = rememberCoroutineScope()
    val scene = remember { SceneCache(cacheScope) }
    var raster by remember { mutableStateOf<RasterCache?>(null) }
    var gesturing by remember { mutableStateOf(false) }
    // Çizim iki katmandır: altta belge, üstte seçim kutusu, tutamaçlar ve kılavuzlar. Belge katmanı yalnızca
    // belge ya da görünüm değişince yeniden çizilir; seçim, araç ya da panel değişiklikleri (kalabalık belgede
    // pahalı olan) belge çizimini tetiklemez.
    val documentState = remember(vm) { derivedStateOf(referentialEqualityPolicy()) { vm.state.document } }
    val presentState = remember(vm) { derivedStateOf(referentialEqualityPolicy()) { vm.state.history.present } }
    val previewing = remember(vm) { derivedStateOf { vm.state.preview != null } }
    // Seçili nesnelerin üst düzey kimlikleri: kalabalık belgede bunlar ayrı çizilir.
    val selectedTop = remember(vm) { derivedStateOf { SceneCache.topLevelOf(vm.state.history.present, vm.state.selection) } }

    // Görünüm (kaydırma, yakınlaştırma) değişirken kalabalık belge keskin olarak yeniden çizilmez; hareket
    // kısa bir süre durunca çizilir. Parmak, fare tekerleği ve el aracı için aynı kural geçerlidir.
    var settledViewport by remember { mutableStateOf(vm.viewport) }
    val liveViewport = vm.viewport
    LaunchedEffect(liveViewport) {
        delay(140)
        settledViewport = liveViewport
    }
    val committed = presentState.value
    LaunchedEffect(committed) {
        raster = null
        delay(350)
        raster = withContext(Dispatchers.Default) { RasterCache.build(committed) }
    }

    Box(
        modifier
            .semantics { contentDescription = tr("Tuval") }
            .onSizeChanged { vm.onCanvasSize(it.toSize()) }
            // Fare tekerleği: imlecin bulunduğu noktaya doğru yakınlaştırır.
            .pointerInput(vm) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val dy = change.scrollDelta.y
                        if (dy != 0f) vm.transformViewport(change.position, Offset.Zero, if (dy < 0f) 1.12f else 1f / 1.12f)
                        change.consume()
                    }
                }
            }
            .pointerInput(vm) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Fare: sağ ya da orta tuşla sürüklemek tuvali kaydırır.
                    val mousePan = currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isTertiaryPressed
                    var multiTouch = mousePan
                    var dragging = false
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.isEmpty() -> finished = true
                            mousePan -> {
                                gesturing = true
                                val change = pressed.first()
                                vm.transformViewport(change.position, change.position - change.previousPosition, 1f)
                                change.consume()
                            }
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
      Canvas(Modifier.fillMaxSize()) {
        val viewport = vm.viewport
        val document = documentState.value

        clipRect {
            drawRect(AppColors.Pasteboard)
            val unsettled = gesturing || viewport != settledViewport
            val cached = raster?.takeIf { unsettled && it.document === document }
            val moving = vm.movingIds
            val base = presentState.value
            // Kalabalık belge her karede yeniden çizilmez (bkz. SceneCache): hazır görüntü gösterilir, üzerine
            // yalnızca seçili ya da sürüklenen nesneler çizilir. Bu sırada o nesneler en üstte görünür; kesin
            // görüntü arka planda hazırlanır ve hazır olunca yerine geçer.
            val heavy = cached == null && base.layers.sumOf { it.children.size } >= SceneCache.HEAVY_NODE_COUNT
            val dragging = heavy && moving.isNotEmpty() && previewing.value
            val w = size.width.toInt()
            val h = size.height.toInt()
            scene.version // arka planda görüntü hazır olunca yeniden çiz
            var image: androidx.compose.ui.graphics.ImageBitmap? = null
            var stale: SceneCache.Entry? = null
            var live: Set<String> = emptySet()
            if (!heavy) {
                scene.clear()
            } else if (unsettled) {
                // Görünüm hareket halinde: son kesin görüntü yeni görünüme göre ölçeklenip kaydırılır.
                stale = scene.exact?.takeIf { it.document === document }
            } else {
                val selected = selectedTop.value
                val exact = if (dragging) null else scene.exactFor(document, viewport, w, h)
                val partial = if (exact == null) scene.partialFor(document, viewport, w, h) else null
                val ghost = if (exact == null && partial == null && dragging) scene.exactFor(base, viewport, w, h) else null
                val moved = if (exact == null && partial == null && ghost == null && !dragging) scene.exact?.takeIf { it.document === document } else null
                when {
                    exact != null -> {
                        image = exact.image
                        // Seçim üzerinde yapılacak işlemler için kısmi görüntü önceden hazırlanır.
                        if (selected.isNotEmpty()) scene.request(document, selected, viewport, w, h)
                    }
                    partial != null -> {
                        image = partial.image
                        live = partial.skip
                        if (!dragging) scene.request(document, emptySet(), viewport, w, h, delayMillis = 120)
                    }
                    ghost != null -> {
                        // Kısmi görüntü henüz hazır değil: nesnenin eski yeri kısa bir süre görünür kalır.
                        image = ghost.image
                        live = moving
                        scene.request(base, moving, viewport, w, h)
                    }
                    moved != null -> {
                        // Görünüm yeni duruldu: keskin görüntü hazırlanana kadar eskisi ölçeklenerek gösterilir.
                        stale = moved
                        scene.request(document, emptySet(), viewport, w, h)
                    }
                    else -> {
                        val entry = if (dragging) scene.renderNow(base, moving, viewport, w, h) else scene.renderNow(document, emptySet(), viewport, w, h)
                        image = entry?.image
                        if (entry != null && dragging) live = moving
                    }
                }
            }
            val visible = Rect.of(viewport.toDocument(Offset.Zero), viewport.toDocument(Offset(size.width, size.height)))
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                stale?.let { scene.drawStale(canvas, it, viewport) }
                image?.let { canvas.drawImage(it, Offset.Zero, imagePaint) }
                canvas.save()
                canvas.translate(viewport.offset.x, viewport.offset.y)
                canvas.scale(viewport.scale, viewport.scale)
                when {
                    cached != null -> cached.draw(canvas)
                    image != null -> if (live.isNotEmpty()) {
                        for (layer in document.layers) {
                            if (!layer.visible) continue
                            for (node in layer.children) if (node.id in live) renderer.drawNode(native, node)
                        }
                    }
                    stale != null -> {}
                    else -> renderer.draw(native, document, visible = visible)
                }
                canvas.restore()
            }
            for (ab in document.artboards) outline(ab.bounds, viewport, Color(0x66000000), 1.dp.toPx())
        }
      }
      Canvas(Modifier.fillMaxSize()) {
        val state = vm.state
        val viewport = vm.viewport
        val document = state.document

        clipRect {
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
private class RasterCache(val document: Document, private val image: ImageBitmap, private val bounds: Rect, private val scale: Float) {
    private val paint = Paint().apply { filterQuality = FilterQuality.Low }

    /** [canvas] belge koordinatında olmalıdır. */
    fun draw(canvas: androidx.compose.ui.graphics.Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(1f / scale, 1f / scale)
        canvas.drawImage(image, Offset.Zero, paint)
        canvas.restore()
    }

    companion object {
        private const val HEAVY_NODE_COUNT = 400
        private const val MAX_SIDE = 2048.0

        /** Hafif belgeler için `null` döner: onları doğrudan çizmek zaten hızlıdır. */
        fun build(document: Document): RasterCache? {
            if (document.nodeCount() < HEAVY_NODE_COUNT) return null
            val box = document.artboardBounds()?.let { it.inflate(maxOf(it.width, it.height) * 0.08) } ?: return null
            if (box.width <= 0 || box.height <= 0) return null
            val scale = (MAX_SIDE / maxOf(box.width, box.height)).toFloat()
            val w = (box.width * scale).toInt().coerceAtLeast(1)
            val h = (box.height * scale).toInt().coerceAtLeast(1)
            return try {
                val image = ImageBitmap(w, h)
                val canvas = androidx.compose.ui.graphics.Canvas(image)
                canvas.scale(scale, scale)
                canvas.translate(-box.left.toFloat(), -box.top.toFloat())
                // Arka plan iş parçacığında çalışır; ekran çizicisiyle durum paylaşmamak için ayrı bir çizici kullanılır.
                DocumentRenderer().draw(canvas.nativeCanvas, document)
                RasterCache(document, image, box, scale)
            } catch (e: Throwable) {
                null // bellek yetmezse önbelleksiz devam
            }
        }
    }
}
