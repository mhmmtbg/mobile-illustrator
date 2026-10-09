package io.github.mhmmtbg.mobileillustrator.model

import java.util.IdentityHashMap

/**
 * Üst düzey düğümlerin sınır kutularını saklar. İsabet testi, seçim çerçevesi, yakalama ve çizimde
 * "bu nesne bu bölgeye değiyor mu" sorusu, her seferinde tüm eğrileri hesaplamak yerine buradan yanıtlanır.
 *
 * Belge değişmezdir ve düzenlemeler değişmeyen düğümleri aynı nesne olarak bırakır; bu yüzden yeni
 * belgenin dizini kurulurken önceki dizinden yalnızca değişen düğümler yeniden hesaplanır.
 */
class DocIndex private constructor(val document: Document, private val boxes: IdentityHashMap<Node, Entry>) {

    class Entry(
        /** Geometrinin kutusu; boş nesnede `null`. */
        val bounds: Rect?,
        /** Kontur kalınlığı payı eklenmiş kutu: nesnenin boyadığı alan bunun dışına çıkmaz. */
        val painted: Rect?,
    )

    fun entry(node: Node): Entry? = boxes[node]
    fun bounds(node: Node): Rect? = boxes[node]?.bounds
    fun painted(node: Node): Rect? = boxes[node]?.painted

    companion object {
        private var last: DocIndex? = null

        /** Belgenin dizini. Aynı belge için yeniden istenirse hazır olan döner. */
        @Synchronized
        fun of(document: Document): DocIndex {
            val prev = last
            if (prev != null && prev.document === document) return prev
            val map = IdentityHashMap<Node, Entry>(maxOf(16, document.layers.sumOf { it.children.size } * 2))
            for (layer in document.layers) {
                for (n in layer.children) {
                    map[n] = prev?.boxes?.get(n) ?: compute(n)
                }
            }
            return DocIndex(document, map).also { last = it }
        }

        private fun compute(node: Node): Entry {
            val b = node.bounds() ?: return Entry(null, null)
            val pad = strokePad(node, Matrix.Identity)
            return Entry(b, if (pad > 0) b.inflate(pad) else b)
        }

        /** Alt ağaçtaki en kalın konturun yarısı (sivri köşeler için paylı), belge biriminde. */
        private fun strokePad(node: Node, parent: Matrix): Double {
            val m = parent * node.transform
            return when (node) {
                is PathNode -> node.stroke?.let { it.width * m.meanScale / 2 * if (it.join == LineJoin.Miter) minOf(it.miterLimit, 4.0) else 1.0 } ?: 0.0
                is TextNode -> node.stroke?.let { it.width * m.meanScale / 2 } ?: 0.0
                is GroupNode -> if (node.clip != null) 0.0 else node.children.maxOfOrNull { strokePad(it, m) } ?: 0.0
                is ImageNode -> 0.0
            }
        }
    }
}
