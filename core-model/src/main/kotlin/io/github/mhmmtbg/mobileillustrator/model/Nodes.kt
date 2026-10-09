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

data class GroupNode(
    override val id: String = newId(),
    override val name: String = "Grup",
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val opacity: Double = 1.0,
    override val transform: Matrix = Matrix.Identity,
    val children: List<Node>,
) : Node

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
}

/** Ebeveyn uzayında [m] uygular (taşıma, ölçekleme, döndürme). */
fun Node.transformedBy(m: Matrix): Node = withTransform(m * transform)

/** Ebeveyn uzayındaki sınır kutusu. Boş nesnelerde `null`. Kontur kalınlığı dahil değildir. */
fun Node.bounds(): Rect? = boundsIn(Matrix.Identity)

private fun Node.boundsIn(parent: Matrix): Rect? {
    val m = parent * transform
    return when (this) {
        is PathNode -> subpaths
            .mapNotNull { it.transformed(m).bounds() }
            .reduceOrNull(Rect::union)
        is GroupNode -> children
            .mapNotNull { it.boundsIn(m) }
            .reduceOrNull(Rect::union)
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
