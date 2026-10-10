package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import io.github.mhmmtbg.mobileillustrator.editor.Viewport
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.render.DocumentRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kalabalık belgeler için ekran görüntüsü önbelleği.
 *
 * Binlerce nesneyi her karede yeniden çizmek yerine belge, ekran boyutunda bir görüntüye çizilir ve o
 * görüntü gösterilir. İki görüntü tutulur:
 *  - **tam**: belgenin tamamı (ekranda duran, kesin görüntü),
 *  - **kısmi**: seçili nesneler hariç her şey. Seçili nesneler taşınırken, renkleri değişirken ya da
 *    silinirken bu görüntünün üzerine yalnızca onlar çizilir; değişiklik anında görünür.
 *
 * Görüntüler arka planda hazırlanır; hazır olana kadar eldeki en uygun görüntü gösterilir. Hiçbiri uygun
 * değilse (belge yeni açıldıysa) bir kereliğine yerinde çizilir.
 */
class SceneCache(private val scope: CoroutineScope) {
    class Entry(val document: Document, val skip: Set<String>, val viewport: Viewport, val image: ImageBitmap) {
        fun fits(viewport: Viewport, width: Int, height: Int) = this.viewport == viewport && image.width == width && image.height == height
    }

    var exact: Entry? = null
        private set
    var partial: Entry? = null
        private set

    /** Arka planda bir görüntü hazır olduğunda artar; tuval bunu okuyarak yeniden çizilir. */
    var version by mutableIntStateOf(0)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.Default.limitedParallelism(1)
    private val backgroundRenderer = DocumentRenderer()
    private val foregroundRenderer = DocumentRenderer()
    private val clearPaint = Paint().apply { color = AppColors.Pasteboard }
    private val stalePaint = Paint().apply { filterQuality = FilterQuality.Low }

    private class Request(val document: Document, val skip: Set<String>, val viewport: Viewport, val width: Int, val height: Int) {
        fun same(d: Document, s: Set<String>, v: Viewport, w: Int, h: Int) = document === d && skip == s && viewport == v && width == w && height == h
    }

    private var pending: Request? = null
    private var job: Job? = null

    fun clear() {
        job?.cancel()
        job = null
        pending = null
        exact = null
        partial = null
    }

    fun exactFor(document: Document, viewport: Viewport, width: Int, height: Int): Entry? =
        exact?.takeIf { it.document === document && it.fits(viewport, width, height) }

    /**
     * Seçili nesneler hariç çizilmiş ve [document] için hâlâ geçerli olan görüntü: belge, görüntünün çizildiği
     * belgeden yalnızca hariç tutulan nesnelerde farklıysa geri kalanı aynıdır.
     */
    fun partialFor(document: Document, viewport: Viewport, width: Int, height: Int): Entry? {
        val p = partial ?: return null
        if (!p.fits(viewport, width, height)) return null
        if (p.document === document) return p
        if (compatibleWith === document && compatibleEntry === p) return p.takeIf { compatible }
        compatibleWith = document
        compatibleEntry = p
        compatible = sameExcept(document, p.document, p.skip)
        return p.takeIf { compatible }
    }

    // Son uyumluluk denetiminin sonucu (her karede yeniden hesaplanmasın).
    private var compatibleWith: Document? = null
    private var compatibleEntry: Entry? = null
    private var compatible = false

    private fun render(renderer: DocumentRenderer, r: Request, stop: (() -> Boolean)?): ImageBitmap? = try {
        val image = ImageBitmap(r.width, r.height)
        val canvas = Canvas(image)
        canvas.drawRect(0f, 0f, r.width.toFloat(), r.height.toFloat(), clearPaint)
        canvas.translate(r.viewport.offset.x, r.viewport.offset.y)
        canvas.scale(r.viewport.scale, r.viewport.scale)
        val visible = Rect.of(r.viewport.toDocument(Offset.Zero), r.viewport.toDocument(Offset(r.width.toFloat(), r.height.toFloat())))
        renderer.draw(canvas.nativeCanvas, r.document, visible = visible, skip = r.skip.takeIf { it.isNotEmpty() }, stop = stop)
        if (stop?.invoke() == true) null else image
    } catch (e: Throwable) {
        null // bellek yetmezse görüntü hazırlanmaz; tuval doğrudan çizime döner
    }

    private fun store(r: Request, image: ImageBitmap): Entry {
        val entry = Entry(r.document, r.skip, r.viewport, image)
        if (r.skip.isEmpty()) exact = entry else partial = entry
        return entry
    }

    /** Görüntüyü hemen, çağıran iş parçacığında çizer. Yalnızca eldeki hiçbir görüntü uygun değilken kullanılır. */
    fun renderNow(document: Document, skip: Set<String>, viewport: Viewport, width: Int, height: Int): Entry? {
        if (width <= 0 || height <= 0) return null
        job?.cancel()
        pending = null
        val request = Request(document, skip, viewport, width, height)
        val image = render(foregroundRenderer, request, null) ?: return null
        return store(request, image)
    }

    /** Görüntüyü arka planda hazırlar; bitince [version] artar. Aynı istek yinelenirse bir şey yapmaz. */
    fun request(document: Document, skip: Set<String>, viewport: Viewport, width: Int, height: Int, delayMillis: Long = 0) {
        if (width <= 0 || height <= 0) return
        if (pending?.same(document, skip, viewport, width, height) == true) return
        val have = if (skip.isEmpty()) exact else partial
        if (have != null && have.document === document && have.skip == skip && have.fits(viewport, width, height)) return
        val request = Request(document, skip, viewport, width, height)
        job?.cancel()
        pending = request
        job = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val image = withContext(worker) { render(backgroundRenderer, request, stop = { !isActive }) }
            if (pending === request) pending = null
            if (image != null && isActive) {
                store(request, image)
                version++
            }
        }
    }

    /** Tam görüntüyü, çizildiği görünümden [now] görünümüne ölçekleyip kaydırarak çizer (görünüm hareket halindeyken). */
    fun drawStale(canvas: Canvas, entry: Entry, now: Viewport) {
        val old = entry.viewport
        val k = now.scale / old.scale
        canvas.save()
        canvas.translate(now.offset.x - old.offset.x * k, now.offset.y - old.offset.y * k)
        canvas.scale(k, k)
        canvas.drawImage(entry.image, Offset.Zero, stalePaint)
        canvas.restore()
    }

    companion object {
        /** Bu sayıdan çok üst düzey nesnesi olan belge "kalabalık" sayılır. */
        const val HEAVY_NODE_COUNT = 300

        /** İki belge, [except] içindeki üst düzey nesneler dışında aynı mı (aynı nesneler, aynı sırada). */
        fun sameExcept(a: Document, b: Document, except: Set<String>): Boolean {
            if (a.artboards != b.artboards || a.layers.size != b.layers.size) return false
            for (i in a.layers.indices) {
                val la = a.layers[i]
                val lb = b.layers[i]
                if (la.id != lb.id || la.visible != lb.visible || la.opacity != lb.opacity) return false
                if (la.children === lb.children) continue
                val ca = la.children
                val cb = lb.children
                var ia = 0
                var ib = 0
                while (true) {
                    while (ia < ca.size && ca[ia].id in except) ia++
                    while (ib < cb.size && cb[ib].id in except) ib++
                    if (ia == ca.size || ib == cb.size) break
                    if (ca[ia] !== cb[ib]) return false
                    ia++
                    ib++
                }
                if (ia != ca.size || ib != cb.size) return false
            }
            return true
        }

        /** Seçimdeki her nesnenin (iç içe olsa da) ait olduğu üst düzey nesnelerin kimlikleri. */
        fun topLevelOf(document: Document, selection: Set<String>): Set<String> {
            if (selection.isEmpty()) return emptySet()
            fun holds(node: Node): Boolean = node.id in selection || (node is GroupNode && node.children.any(::holds))
            val out = HashSet<String>()
            for (layer in document.layers) for (node in layer.children) if (holds(node)) out += node.id
            return out
        }
    }
}
