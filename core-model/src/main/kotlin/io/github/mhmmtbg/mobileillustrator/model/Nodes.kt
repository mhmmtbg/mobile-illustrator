package io.github.mhmmtbg.mobileillustrator.model

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Katman içindeki her şey: yol ya da grup. Liste sırası çizim sırasıdır (son eleman en üstte). */
sealed interface Node {
    val id: String
    val name: String
    val visible: Boolean
    val locked: Boolean
    val opacity: Double

    /** Nesnenin yerel uzayından ebeveyninin uzayına dönüşüm. */
    val transform: Matrix
}

data class PathNode(
    override val id: String = newId(),
    override val name: String = "Yol",
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val opacity: Double = 1.0,
    override val transform: Matrix = Matrix.Identity,
    val subpaths: List<SubPath>,
    val fill: Paint? = null,
    val stroke: Stroke? = null,
    val fillRule: FillRule = FillRule.NonZero,
) : Node

/** Kırpma yolu; grubun yerel uzayındadır. */
data class ClipPath(val subpaths: List<SubPath>, val rule: FillRule = FillRule.NonZero)

data class GroupNode(
    override val id: String = newId(),
    override val name: String = "Grup",
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val opacity: Double = 1.0,
    override val transform: Matrix = Matrix.Identity,
    val children: List<Node>,
    /** Doluysa çocuklar bu yolun içine kırpılır (Illustrator'daki kırpma maskesi). */
    val clip: ClipPath? = null,
) : Node

/**
 * Kodlanmış (PNG ya da JPEG) görsel verisi. Büyük olabildiği için eşitlik kimlik üzerindendir;
 * belge kopyaları aynı nesneyi paylaşır.
 */
class ImageData(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int)

/** Gömülü görsel. Yerel uzayı piksel cinsindendir: (0,0)-([ImageData.width],[ImageData.height]). */
data class ImageNode(
    override val id: String = newId(),
    override val name: String = "Görsel",
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val opacity: Double = 1.0,
    override val transform: Matrix = Matrix.Identity,
    val image: ImageData,
) : Node

/** Tek satır metin. Yerel uzayda başlangıç noktası (0,0) taban çizgisinin soludur. */
data class TextNode(
    override val id: String = newId(),
    override val name: String = "Metin",
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val opacity: Double = 1.0,
    override val transform: Matrix = Matrix.Identity,
    val text: String,
    val fontSize: Double = 24.0,
    val fontFamily: String = "sans-serif",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fill: Paint? = Paint.Solid(Rgba.Black),
    val stroke: Stroke? = null,
    /** Biliniyorsa metnin gerçek genişliği (içe aktarmada fonttan hesaplanır); yoksa tahmin edilir. */
    val measuredWidth: Double? = null,
) : Node {
    /** Yerel uzaydaki yaklaşık kutu. */
    fun localBounds(): Rect {
        val w = measuredWidth ?: (text.length * fontSize * 0.55)
        return Rect(0.0, -fontSize * 0.8, w, fontSize * 0.25)
    }
}

data class Layer(
    val id: String = newId(),
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Double = 1.0,
    val children: List<Node> = emptyList(),
)

data class Artboard(
    val id: String = newId(),
    val name: String = "Çalışma Yüzeyi 1",
    val bounds: Rect,
)

/** Değişmez belge. Her düzenleme yeni bir kopya üretir; geri al/yinele bunun üzerine kuruludur. */
data class Document(
    val name: String = "Adsız",
    val artboards: List<Artboard>,
    /** Alttan üste doğru. */
    val layers: List<Layer>,
) {
    companion object {
        fun blank(width: Double = 1080.0, height: Double = 1080.0, name: String = "Adsız") = Document(
            name = name,
            artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, width, height))),
            layers = listOf(Layer(name = "Katman 1")),
        )
    }
}

fun Node.withTransform(m: Matrix): Node = when (this) {
    is PathNode -> copy(transform = m)
    is GroupNode -> copy(transform = m)
    is ImageNode -> copy(transform = m)
    is TextNode -> copy(transform = m)
}

fun Node.withName(name: String): Node = when (this) {
    is PathNode -> copy(name = name)
    is GroupNode -> copy(name = name)
    is ImageNode -> copy(name = name)
    is TextNode -> copy(name = name)
}

fun Node.withVisible(visible: Boolean): Node = when (this) {
    is PathNode -> copy(visible = visible)
    is GroupNode -> copy(visible = visible)
    is ImageNode -> copy(visible = visible)
    is TextNode -> copy(visible = visible)
}

fun Node.withLocked(locked: Boolean): Node = when (this) {
    is PathNode -> copy(locked = locked)
    is GroupNode -> copy(locked = locked)
    is ImageNode -> copy(locked = locked)
    is TextNode -> copy(locked = locked)
}

fun Node.withOpacity(opacity: Double): Node = when (this) {
    is PathNode -> copy(opacity = opacity)
    is GroupNode -> copy(opacity = opacity)
    is ImageNode -> copy(opacity = opacity)
    is TextNode -> copy(opacity = opacity)
}

/** Aynı içerikle, kendisi ve tüm alt düğümleri yeni kimlikli bir kopya. */
fun Node.duplicated(): Node = when (this) {
    is PathNode -> copy(id = newId())
    is GroupNode -> copy(id = newId(), children = children.map { it.duplicated() })
    is ImageNode -> copy(id = newId())
    is TextNode -> copy(id = newId())
}

/** Ebeveyn uzayında [m] uygular (taşıma, ölçekleme, döndürme). */
fun Node.transformedBy(m: Matrix): Node = withTransform(m * transform)

/** Ebeveyn uzayındaki sınır kutusu. Boş nesnelerde `null`. Kontur kalınlığı dahil değildir. */
fun Node.bounds(): Rect? = boundsIn(Matrix.Identity)

/** [parent] uzayından belge uzayına dönüşüm verildiğinde belge uzayındaki sınır kutusu. */
fun Node.boundsIn(parent: Matrix): Rect? {
    val m = parent * transform
    return when (this) {
        is PathNode -> subpaths
            .mapNotNull { it.transformed(m).bounds() }
            .reduceOrNull(Rect::union)
        is GroupNode -> {
            val kids = children.mapNotNull { it.boundsIn(m) }.reduceOrNull(Rect::union)
            val clipBox = clip?.subpaths?.mapNotNull { it.transformed(m).bounds() }?.reduceOrNull(Rect::union)
            if (kids != null && clipBox != null) kids.intersect(clipBox) ?: clipBox else kids
        }
        is ImageNode -> Rect.bounding(Rect(0.0, 0.0, image.width.toDouble(), image.height.toDouble()).corners.map(m::apply))
        is TextNode -> Rect.bounding(localBounds().corners.map(m::apply))
    }
}

// ---- Ağaç işlemleri -------------------------------------------------------

fun Document.findLayer(layerId: String): Layer? = layers.firstOrNull { it.id == layerId }

fun Document.findNode(nodeId: String): Node? {
    fun search(nodes: List<Node>): Node? {
        for (n in nodes) {
            if (n.id == nodeId) return n
            if (n is GroupNode) search(n.children)?.let { return it }
        }
        return null
    }
    for (l in layers) search(l.children)?.let { return it }
    return null
}

/** Düğümü içeren katman. */
fun Document.layerOf(nodeId: String): Layer? = layers.firstOrNull { layer ->
    fun has(nodes: List<Node>): Boolean = nodes.any { it.id == nodeId || (it is GroupNode && has(it.children)) }
    has(layer.children)
}

fun Document.updateLayer(layerId: String, f: (Layer) -> Layer): Document =
    copy(layers = layers.map { if (it.id == layerId) f(it) else it })

/** [f] `null` dönerse düğüm silinir. Değişmeyen dallar aynı nesne olarak kalır. */
fun Document.updateNode(nodeId: String, f: (Node) -> Node?): Document {
    fun walk(nodes: List<Node>): List<Node> {
        var changed = false
        val out = ArrayList<Node>(nodes.size)
        for (n in nodes) {
            if (n.id == nodeId) {
                changed = true
                f(n)?.let(out::add)
            } else if (n is GroupNode) {
                val kids = walk(n.children)
                if (kids !== n.children) {
                    changed = true
                    out += n.copy(children = kids)
                } else {
                    out += n
                }
            } else {
                out += n
            }
        }
        return if (changed) out else nodes
    }
    var changed = false
    val newLayers = layers.map { layer ->
        val kids = walk(layer.children)
        if (kids !== layer.children) {
            changed = true
            layer.copy(children = kids)
        } else {
            layer
        }
    }
    return if (changed) copy(layers = newLayers) else this
}

fun Document.removeNode(nodeId: String): Document = updateNode(nodeId) { null }

/** Düğümü katmanın en üstüne ekler. */
fun Document.addNode(layerId: String, node: Node): Document =
    updateLayer(layerId) { it.copy(children = it.children + node) }

/** Düğümün (grupsa içindeki tüm yolların) stilini değiştirir. */
fun Node.mapPaths(f: (PathNode) -> PathNode): Node = when (this) {
    is PathNode -> f(this)
    is GroupNode -> copy(children = children.map { it.mapPaths(f) })
    is ImageNode -> this
    is TextNode -> this
}

/** Ağaçtaki toplam düğüm sayısı (gruplar dahil). */
fun Document.nodeCount(): Int {
    fun count(nodes: List<Node>): Int = nodes.sumOf { 1 + if (it is GroupNode) count(it.children) else 0 }
    return layers.sumOf { count(it.children) }
}

/** Tüm çalışma yüzeylerini kapsayan kutu. */
fun Document.artboardBounds(): Rect? = artboards.map { it.bounds }.reduceOrNull(Rect::union)

/** Düğümün (grupsa içindeki her şeyin) dolgusunu ve konturunu değiştirir; yollar ve metinler etkilenir. */
fun Node.restyled(fill: (Paint?) -> Paint?, stroke: (Stroke?) -> Stroke?): Node = when (this) {
    is PathNode -> copy(fill = fill(this.fill), stroke = stroke(this.stroke))
    is TextNode -> copy(fill = fill(this.fill), stroke = stroke(this.stroke))
    is GroupNode -> copy(children = children.map { it.restyled(fill, stroke) })
    is ImageNode -> this
}

/** Stil göstergeleri için: düğümdeki ilk yol ya da metnin dolgusu ve konturu. */
fun Node.styleSample(): Pair<Paint?, Stroke?>? = when (this) {
    is PathNode -> fill to stroke
    is TextNode -> fill to stroke
    is GroupNode -> children.firstNotNullOfOrNull { it.styleSample() }
    is ImageNode -> null
}
