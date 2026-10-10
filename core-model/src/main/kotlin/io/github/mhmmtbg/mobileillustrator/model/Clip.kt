package io.github.mhmmtbg.mobileillustrator.model

/**
 * Panoya alınan içerik: bütün olarak kopyalanan katmanlar ve tek tek kopyalanan nesneler (belge uzayında).
 * Belgeden bağımsızdır; başka bir belgeye de yapıştırılabilir.
 */
class Clip(val layers: List<Layer>, val nodes: List<Node>) {
    val isEmpty: Boolean get() = layers.isEmpty() && nodes.isEmpty()

    /** Kopyalanan nesne sayısı (katmanların içindekiler dahil, üst düzey). */
    val count: Int get() = nodes.size + layers.sumOf { it.children.size }

    fun bounds(): Rect? =
        (nodes + layers.flatMap { it.children }).mapNotNull { it.bounds() }.reduceOrNull(Rect::union)
}

/**
 * Seçili katmanları ve nesneleri panoya alır. Seçili bir katmanın ya da grubun içindeki nesneler ayrıca alınmaz.
 * İç içe nesneler bulundukları grubun dönüşümüyle birlikte alınır; yapıştırıldıklarında aynı yerde dururlar.
 */
fun Document.clip(nodeIds: Set<String>, layerIds: Set<String> = emptySet()): Clip {
    val outLayers = ArrayList<Layer>()
    val outNodes = ArrayList<Node>()
    fun walk(nodes: List<Node>, m: Matrix) {
        for (n in nodes) {
            if (n.id in nodeIds) outNodes += if (m.isIdentity) n else n.applied(m)
            else if (n is GroupNode) walk(n.children, m * n.transform)
        }
    }
    for (l in layers) {
        if (l.id in layerIds) outLayers += l else walk(l.children, Matrix.Identity)
    }
    return Clip(outLayers, outNodes)
}

/** Yapıştırmanın sonucu: yeni belge, eklenen üst düzey nesnelerin ve katmanların kimlikleri. */
class Pasted(val document: Document, val nodeIds: List<String>, val layerIds: List<String>)

/**
 * Panodakileri [offset] kadar kaydırarak belgeye ekler: katmanlar [activeLayerId] katmanının üstüne yeni katman
 * olarak, nesneler o katmanın en üstüne. Her şey yeni kimlik alır; aynı içerik birden çok kez yapıştırılabilir.
 */
fun Document.paste(clip: Clip, activeLayerId: String, offset: Vec2 = Vec2.Zero): Pasted {
    val m = Matrix.translate(offset.x, offset.y)
    fun fresh(n: Node): Node = n.duplicated().let { if (m.isIdentity) it else it.applied(m) }
    var doc = this
    val nodeIds = ArrayList<String>()
    val layerIds = ArrayList<String>()
    if (clip.nodes.isNotEmpty()) {
        val target = findLayer(activeLayerId) ?: layers.last()
        val added = clip.nodes.map(::fresh)
        nodeIds += added.map { it.id }
        doc = doc.updateLayer(target.id) { it.copy(children = it.children + added) }
    }
    var above = (findLayer(activeLayerId) ?: layers.last()).id
    for (l in clip.layers) {
        val copy = l.copy(id = newId(), children = l.children.map(::fresh))
        doc = doc.addLayer(copy, above)
        above = copy.id
        layerIds += copy.id
        nodeIds += copy.children.map { it.id }
    }
    return Pasted(doc, nodeIds, layerIds)
}

/**
 * Başka bir belgenin katmanlarını bu belgenin en üstüne ekler ("Dışarıdan ekle"). Eklenen içerik, kendi çalışma
 * yüzeyinin ortası bu belgenin ilk çalışma yüzeyinin ortasına gelecek biçimde yerleştirilir.
 */
fun Document.merged(other: Document): Pasted {
    val from = other.artboardBounds()?.center
    val to = artboards.firstOrNull()?.bounds?.center
    val offset = if (from != null && to != null) to - from else Vec2.Zero
    val content = other.layers.filter { it.children.isNotEmpty() }
    return paste(Clip(content, emptyList()), layers.last().id, offset)
}
