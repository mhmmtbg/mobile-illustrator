package io.github.mhmmtbg.mobileillustrator.model

/** Kökten düğüme kadar olan zincir (düğümün kendisi sonda); bulunamazsa `null`. */
fun Document.pathTo(nodeId: String): List<Node>? {
    fun search(nodes: List<Node>, trail: List<Node>): List<Node>? {
        for (n in nodes) {
            if (n.id == nodeId) return trail + n
            if (n is GroupNode) search(n.children, trail + n)?.let { return it }
        }
        return null
    }
    for (l in layers) search(l.children, emptyList())?.let { return it }
    return null
}

/** Düğümün ebeveyn uzayından belge uzayına dönüşüm (kendi dönüşümü hariç). */
fun Document.parentMatrix(nodeId: String): Matrix {
    val chain = pathTo(nodeId) ?: return Matrix.Identity
    var m = Matrix.Identity
    for (i in 0 until chain.size - 1) m = m * chain[i].transform
    return m
}

/** Düğümün belge uzayındaki sınır kutusu (iç içe gruplardaki dönüşümler dahil). */
fun Document.boundsOf(nodeId: String): Rect? {
    val chain = pathTo(nodeId) ?: return null
    var m = Matrix.Identity
    for (i in 0 until chain.size - 1) m = m * chain[i].transform
    return chain.last().boundsIn(m)
}

/** Birden çok düğümün ortak sınır kutusu; ağaç tek geçişte taranır. */
fun Document.boundsOf(ids: Collection<String>): Rect? {
    if (ids.isEmpty()) return null
    if (ids.size == 1) return boundsOf(ids.first())
    val set = ids as? Set<String> ?: ids.toHashSet()
    var out: Rect? = null
    val index = DocIndex.of(this)
    fun walk(nodes: List<Node>, m: Matrix) {
        for (n in nodes) {
            if (n.id in set) {
                // Üst düzey düğümlerin kutusu dizinde hazırdır.
                val b = if (m.isIdentity) index.entry(n)?.let { it.bounds } ?: n.boundsIn(m) else n.boundsIn(m)
                b?.let { box -> out = out?.union(box) ?: box }
            } else if (n is GroupNode) {
                walk(n.children, m * n.transform)
            }
        }
    }
    for (l in layers) walk(l.children, Matrix.Identity)
    return out
}

/**
 * Ebeveyn uzayında [m] dönüşümünü uygular. Dönüşümü birim olan yollarda geometri doğrudan
 * değiştirilir; böylece kontur kalınlığı ölçeklemeyle bozulmaz (Illustrator'ın varsayılanı).
 */
fun Node.applied(m: Matrix): Node = when (this) {
    is PathNode -> if (transform.isIdentity) {
        copy(
            subpaths = subpaths.map { it.transformed(m) },
            fill = fill?.transformedBy(m),
            stroke = stroke?.let { it.copy(paint = it.paint.transformedBy(m)) },
        )
    } else {
        copy(transform = m * transform)
    }
    else -> transformedBy(m)
}

/** Belge uzayındaki [world] dönüşümünü, düğüm hangi derinlikte olursa olsun uygular. */
fun Document.transformNode(nodeId: String, world: Matrix): Document {
    val parent = parentMatrix(nodeId)
    val local = if (parent.isIdentity) world else (parent.inverse() ?: return this) * world * parent
    return updateNode(nodeId) { it.applied(local) }
}

/** Birden çok düğüme aynı belge uzayı dönüşümünü tek geçişte uygular. */
fun Document.transformNodes(ids: Set<String>, world: Matrix): Document {
    if (ids.isEmpty()) return this
    if (ids.size == 1) return transformNode(ids.first(), world)
    fun walk(nodes: List<Node>, parent: Matrix): List<Node> {
        var changed = false
        val out = ArrayList<Node>(nodes.size)
        for (n in nodes) {
            if (n.id in ids) {
                val local = if (parent.isIdentity) world else parent.inverse()?.let { it * world * parent }
                out += if (local != null) n.applied(local) else n
                changed = true
            } else if (n is GroupNode) {
                val kids = walk(n.children, parent * n.transform)
                if (kids !== n.children) { out += n.copy(children = kids); changed = true } else out += n
            } else {
                out += n
            }
        }
        return if (changed) out else nodes
    }
    var changed = false
    val newLayers = layers.map { l ->
        val kids = walk(l.children, Matrix.Identity)
        if (kids !== l.children) { changed = true; l.copy(children = kids) } else l
    }
    return if (changed) copy(layers = newLayers) else this
}

/** [point] altındaki en derin, seçilebilir yaprak düğüm (grupların içine iner). */
fun Document.hitTestDeep(point: Vec2, tolerance: Double): Node? {
    val top = hitTest(point, tolerance) ?: return null
    fun dive(node: Node, parent: Matrix): Node? {
        if (node !is GroupNode) return node
        val m = parent * node.transform
        for (child in node.children.asReversed()) {
            if (!child.visible || child.locked) continue
            val probe = Document(artboards = emptyList(), layers = listOf(Layer(name = "", children = listOf(child.transformedBy(m)))))
            if (probe.hitTest(point, tolerance) != null) return dive(child, m)
        }
        return null
    }
    return dive(top, Matrix.Identity) ?: top
}

/** Sınır kutusu [rect] ile kesişen, seçilebilir üst düzey düğümler (seçim çerçevesi için). */
fun Document.nodesIn(rect: Rect): List<Node> {
    val out = ArrayList<Node>()
    val index = DocIndex.of(this)
    for (layer in layers) {
        if (!layer.visible || layer.locked) continue
        for (n in layer.children) {
            if (!n.visible || n.locked) continue
            val b = index.bounds(n) ?: continue
            if (b.intersects(rect)) out += n
        }
    }
    return out
}

// ---- Sıra ----------------------------------------------------------------

enum class ZMove { Forward, Backward, Front, Back }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices) return this
    val out = ArrayList(this)
    val item = out.removeAt(from)
    out.add(to.coerceIn(0, out.size), item)
    return out
}

/** Düğümü kardeşleri arasında öne ya da arkaya taşır. */
fun Document.reorderNode(nodeId: String, move: ZMove): Document {
    fun reorder(nodes: List<Node>): List<Node>? {
        val i = nodes.indexOfFirst { it.id == nodeId }
        if (i >= 0) {
            val to = when (move) {
                ZMove.Forward -> i + 1
                ZMove.Backward -> i - 1
                ZMove.Front -> nodes.size - 1
                ZMove.Back -> 0
            }.coerceIn(0, nodes.size - 1)
            return nodes.moved(i, to)
        }
        for ((k, n) in nodes.withIndex()) {
            if (n is GroupNode) {
                val kids = reorder(n.children) ?: continue
                return ArrayList(nodes).also { it[k] = n.copy(children = kids) }
            }
        }
        return null
    }
    for (layer in layers) {
        val kids = reorder(layer.children) ?: continue
        return updateLayer(layer.id) { it.copy(children = kids) }
    }
    return this
}

// ---- Gruplama --------------------------------------------------------------

/**
 * Üst düzey düğümleri tek grupta toplar. Grup, seçimdeki en üstteki düğümün katmanına ve yerine konur.
 * @return yeni belge ve grubun kimliği; gruplanacak en az iki düğüm yoksa `null`.
 */
fun Document.group(ids: Set<String>): Pair<Document, String>? {
    val picked = ArrayList<Node>()
    var targetLayer: String? = null
    var anchorId: String? = null
    for (layer in layers) {
        for (n in layer.children) {
            if (n.id in ids) {
                picked += n
                targetLayer = layer.id
                anchorId = n.id
            }
        }
    }
    if (picked.size < 2 || targetLayer == null) return null
    val group = GroupNode(children = picked)
    val doc = copy(
        layers = layers.map { layer ->
            val kids = ArrayList<Node>()
            for (n in layer.children) {
                if (n.id == anchorId) kids += group else if (n.id !in ids) kids += n
            }
            if (kids.size == layer.children.size && kids.indices.all { kids[it] === layer.children[it] }) layer else layer.copy(children = kids)
        },
    )
    return doc to group.id
}

/** Grubu çözer; çocuklar grubun dönüşümünü ve opaklığını devralır. @return yeni belge ve çocuk kimlikleri. */
fun Document.ungroup(groupId: String): Pair<Document, List<String>>? {
    val g = findNode(groupId) as? GroupNode ?: return null
    val kids = g.children.map { c ->
        val moved = if (g.transform.isIdentity) c else c.applied(g.transform)
        if (g.opacity < 1.0) moved.withOpacity(moved.opacity * g.opacity) else moved
    }
    fun replace(nodes: List<Node>): List<Node>? {
        val i = nodes.indexOfFirst { it.id == groupId }
        if (i >= 0) return nodes.subList(0, i) + kids + nodes.subList(i + 1, nodes.size)
        for ((k, n) in nodes.withIndex()) {
            if (n is GroupNode) {
                val r = replace(n.children) ?: continue
                return ArrayList(nodes).also { it[k] = n.copy(children = r) }
            }
        }
        return null
    }
    for (layer in layers) {
        val r = replace(layer.children) ?: continue
        return updateLayer(layer.id) { it.copy(children = r) } to kids.map { it.id }
    }
    return null
}

/** Düğümleri biraz kaydırarak çoğaltır. @return yeni belge ve kopyaların kimlikleri. */
fun Document.duplicate(ids: Set<String>, offset: Vec2): Pair<Document, List<String>> {
    val created = ArrayList<String>()
    val m = Matrix.translate(offset.x, offset.y)
    fun dup(nodes: List<Node>): List<Node> {
        if (nodes.none { it.id in ids || it is GroupNode }) return nodes
        val out = ArrayList<Node>(nodes.size + 2)
        var changed = false
        for (n in nodes) {
            if (n.id in ids) {
                out += n
                val copy = n.duplicated().applied(m)
                created += copy.id
                out += copy
                changed = true
            } else if (n is GroupNode) {
                val kids = dup(n.children)
                if (kids !== n.children) { out += n.copy(children = kids); changed = true } else out += n
            } else {
                out += n
            }
        }
        return if (changed) out else nodes
    }
    val doc = copy(layers = layers.map { l -> dup(l.children).let { if (it === l.children) l else l.copy(children = it) } })
    return doc to created
}

// ---- Katmanlar -------------------------------------------------------------

/** [aboveLayerId] katmanının hemen üstüne (yoksa en üste) yeni katman ekler. */
fun Document.addLayer(layer: Layer, aboveLayerId: String? = null): Document {
    val i = layers.indexOfFirst { it.id == aboveLayerId }
    val out = ArrayList(layers)
    out.add(if (i < 0) out.size else i + 1, layer)
    return copy(layers = out)
}

/** Son katman silinemez. */
fun Document.removeLayer(layerId: String): Document =
    if (layers.size <= 1) this else copy(layers = layers.filter { it.id != layerId })

/** [up] doğruysa katmanı bir üste (öne) taşır. */
fun Document.moveLayer(layerId: String, up: Boolean): Document {
    val i = layers.indexOfFirst { it.id == layerId }
    if (i < 0) return this
    val to = if (up) i + 1 else i - 1
    if (to !in layers.indices) return this
    return copy(layers = layers.moved(i, to))
}

/** Üst düzey düğümleri başka bir katmanın en üstüne taşır. */
fun Document.moveNodesToLayer(ids: Set<String>, layerId: String): Document {
    if (findLayer(layerId) == null) return this
    val moving = ArrayList<Node>()
    for (l in layers) for (n in l.children) if (n.id in ids) moving += n
    if (moving.isEmpty()) return this
    return copy(
        layers = layers.map { l ->
            val rest = l.children.filter { it.id !in ids }
            when {
                l.id == layerId -> l.copy(children = rest + moving)
                rest.size != l.children.size -> l.copy(children = rest)
                else -> l
            }
        },
    )
}

/** Kullanılmayan bir "Katman N" adı. */
fun Document.nextLayerName(): String {
    var n = layers.size + 1
    while (layers.any { it.name == "Katman $n" }) n++
    return "Katman $n"
}
