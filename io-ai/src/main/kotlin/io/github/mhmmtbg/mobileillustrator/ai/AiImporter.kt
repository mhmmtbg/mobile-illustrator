package io.github.mhmmtbg.mobileillustrator.ai

import io.github.mhmmtbg.mobileillustrator.model.Artboard
import io.github.mhmmtbg.mobileillustrator.model.BlendMode
import io.github.mhmmtbg.mobileillustrator.model.withBlendMode
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImportException
import io.github.mhmmtbg.mobileillustrator.model.ImportResult
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.Matrix
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.TextNode
import io.github.mhmmtbg.mobileillustrator.model.transformedBy
import io.github.mhmmtbg.mobileillustrator.model.withLocked
import io.github.mhmmtbg.mobileillustrator.model.withVisible
import io.github.mhmmtbg.mobileillustrator.model.withName
import io.github.mhmmtbg.mobileillustrator.model.withOpacity
import io.github.mhmmtbg.mobileillustrator.pdf.ClipLink
import io.github.mhmmtbg.mobileillustrator.pdf.Container
import io.github.mhmmtbg.mobileillustrator.pdf.ContentRunner
import io.github.mhmmtbg.mobileillustrator.pdf.ContentSink
import io.github.mhmmtbg.mobileillustrator.pdf.Item
import io.github.mhmmtbg.mobileillustrator.pdf.LayerInfo
import io.github.mhmmtbg.mobileillustrator.pdf.Leaf
import io.github.mhmmtbg.mobileillustrator.pdf.PdfArr
import io.github.mhmmtbg.mobileillustrator.pdf.PdfDict
import io.github.mhmmtbg.mobileillustrator.pdf.PdfException
import io.github.mhmmtbg.mobileillustrator.pdf.PdfFile
import io.github.mhmmtbg.mobileillustrator.pdf.PdfObj
import io.github.mhmmtbg.mobileillustrator.pdf.PdfRef
import io.github.mhmmtbg.mobileillustrator.pdf.PdfStream
import java.io.ByteArrayOutputStream

/**
 * Illustrator (.ai) ve PDF dosyalarını belgeye çevirir.
 *
 * Illustrator 9 ve sonrası .ai dosyaları, "PDF uyumlu dosya oluştur" seçeneğiyle kaydedildiyse
 * tam bir PDF kopyası taşır; burada o kopya okunur. Katmanlar PDF'in isteğe bağlı içerik
 * gruplarından (OCG), çalışma yüzeyleri sayfalardan gelir.
 */
object AiImporter {
    private const val ARTBOARD_GAP = 60.0
    private const val MAX_PAGES = 200

    fun import(bytes: ByteArray, name: String): ImportResult {
        when (AiFormat.sniff(bytes)) {
            AiFormat.Container.PostScript ->
                throw ImportException("Bu dosya eski PostScript tabanlı .ai biçiminde (Illustrator 8 ve öncesi). Illustrator'da açıp yeniden kaydedin.")
            AiFormat.Container.Unknown ->
                throw ImportException("Dosya bir Illustrator ya da PDF dosyası gibi görünmüyor.")
            AiFormat.Container.Pdf -> {}
        }
        // Dosya güvenilmez girdidir: hangi hata çıkarsa çıksın uygulama çökmemeli, anlaşılır bir ileti dönmeli.
        try {
            return Importer(PdfFile(bytes), name).run()
        } catch (e: ImportException) {
            throw e
        } catch (e: PdfException) {
            throw ImportException(e.message ?: "Dosya okunamadı")
        } catch (e: OutOfMemoryError) {
            throw ImportException("Dosya belleğe sığmayacak kadar büyük ya da karmaşık")
        } catch (e: StackOverflowError) {
            throw ImportException("Dosya çok derin iç içe yapı içeriyor")
        } catch (e: RuntimeException) {
            throw ImportException("Dosya bozuk görünüyor ve okunamadı")
        }
    }

    private class Bucket(val key: PdfRef?, val info: LayerInfo) {
        val items = ArrayList<Item>()
        var lastPage = -1
    }

    private class Page(val dict: PdfDict, val resources: PdfDict?, val box: DoubleArray)

    private class Importer(val file: PdfFile, val name: String) : ContentSink {
        val warnings = LinkedHashSet<String>()
        val buckets = ArrayList<Bucket>()
        var active: Bucket? = null
        var pageIndex = 0
        val off = HashSet<PdfRef>()
        val locked = HashSet<PdfRef>()
        var pageRect = Rect(0.0, 0.0, 0.0, 0.0)

        override fun warn(message: String) {
            warnings += message
        }

        override fun emit(item: Item) {
            val b = active ?: implicitBucket()
            b.lastPage = pageIndex
            b.items += item
        }

        private fun implicitBucket(): Bucket {
            val last = buckets.lastOrNull()
            if (last != null && last.key == null && last.lastPage == pageIndex) return last
            // Katmansız içerik: önceki sayfada açılmış adsız kova varsa onu sürdür.
            if (last != null && last.key == null && buckets.none { it.key != null }) return last
            return Bucket(null, LayerInfo("", visible = true, locked = false)).also { buckets += it }
        }

        override fun layerInfo(ocg: PdfObj?): Pair<PdfRef?, LayerInfo>? {
            var ref = ocg as? PdfRef
            var d = file.dict(ocg) ?: return null
            if (file.name(d["Type"]) == "OCMD") {
                val inner = d["OCGs"]
                val first = (file.resolve(inner) as? PdfArr)?.items?.firstOrNull() ?: inner
                ref = first as? PdfRef
                d = file.dict(first) ?: return null
            }
            val nm = file.string(d["Name"])?.text()?.trim().orEmpty().ifEmpty { "Katman" }
            return ref to LayerInfo(nm, visible = ref == null || ref !in off, locked = ref != null && ref in locked)
        }

        override fun beginLayer(ref: PdfRef?, info: LayerInfo): Boolean {
            if (active != null) return false
            val existing = if (ref != null) buckets.lastOrNull { it.key == ref } else null
            active = if (existing != null && (existing.lastPage != pageIndex || existing === buckets.last())) {
                existing
            } else {
                Bucket(ref, info).also { buckets += it }
            }
            active!!.lastPage = pageIndex
            return true
        }

        override fun endLayer() {
            active = null
        }

        fun run(): ImportResult {
            val root = file.root
            file.dict(file.dict(root["OCProperties"])?.get("D"))?.let { cfg ->
                file.array(cfg["OFF"])?.forEach { (it as? PdfRef)?.let(off::add) }
                file.array(cfg["Locked"])?.forEach { (it as? PdfRef)?.let(locked::add) }
            }
            val pages = ArrayList<Page>()
            collectPages(file.dict(root["Pages"]), null, null, pages, HashSet(), 0)
            if (pages.isEmpty()) throw ImportException("Dosyada sayfa bulunamadı")
            if (pages.size > MAX_PAGES) warn("Yalnızca ilk $MAX_PAGES sayfa açıldı")

            val artboards = ArrayList<Artboard>()
            var x = 0.0
            for ((i, page) in pages.take(MAX_PAGES).withIndex()) {
                pageIndex = i
                val b = page.box
                val w = b[2] - b[0]
                val h = b[3] - b[1]
                pageRect = Rect(x, 0.0, x + w, h)
                artboards += Artboard(name = "Çalışma Yüzeyi ${i + 1}", bounds = pageRect)
                // PDF'te y yukarı doğrudur; belgede aşağı.
                val base = Matrix(1.0, 0.0, 0.0, -1.0, x - b[0], b[3])
                val runner = ContentRunner(file, this, pageRect)
                runner.start(base)
                try {
                    runner.run(pageContent(page.dict), page.resources, base)
                } catch (e: PdfException) {
                    warn("Sayfa ${i + 1} tam okunamadı: ${e.message}")
                } catch (e: StackOverflowError) {
                    warn("Sayfa ${i + 1} çok derin iç içe yapı içeriyor")
                }
                active = null
                x += w + ARTBOARD_GAP
            }

            val pageRects = artboards.map { it.bounds }
            var unnamed = 0
            val layers = buckets.mapNotNull { b ->
                val nodes = Builder(pageRects).build(b.items, null)
                if (nodes.isEmpty()) return@mapNotNull null
                val nm = b.info.name.ifEmpty { unnamed++; if (unnamed == 1) "Katman 1" else "Katman $unnamed" }
                // Katmanın tamamı tek bir saydamlık grubuysa bu, katman opaklığıdır.
                val whole = nodes.singleOrNull() as? GroupNode
                if (whole != null && whole.name == "Grup" && whole.opacity < 1.0 && whole.clip == null && whole.transform.isIdentity &&
                    whole.blendMode == BlendMode.Normal
                ) {
                    Layer(name = nm, visible = b.info.visible, locked = b.info.locked, opacity = whole.opacity, children = whole.children)
                } else {
                    Layer(name = nm, visible = b.info.visible, locked = b.info.locked, children = nodes)
                }
            }.ifEmpty { listOf(Layer(name = "Katman 1")) }

            val producer = file.string(file.dict(file.trailer["Info"])?.get("Producer"))?.text()
            return ImportResult(
                Document(name = name, artboards = artboards, layers = layers),
                warnings.toList(),
                writtenByThisApp = producer == AiExporter.PRODUCER,
            )
        }

        private fun collectPages(node: PdfDict?, res: PdfDict?, box: DoubleArray?, out: ArrayList<Page>, seen: HashSet<PdfDict>, depth: Int) {
            node ?: return
            if (depth > 64 || !seen.add(node) || out.size > MAX_PAGES) return
            val r = file.dict(node["Resources"]) ?: res
            val media = file.numbers(node["MediaBox"])?.takeIf { it.size >= 4 } ?: box
            val kids = file.array(node["Kids"])
            if (kids != null && file.name(node["Type"]) != "Page") {
                for (k in kids) collectPages(file.dict(k), r, media, out, seen, depth + 1)
                return
            }
            var b = normalize(media ?: doubleArrayOf(0.0, 0.0, 612.0, 792.0))
            file.numbers(node["CropBox"])?.takeIf { it.size >= 4 }?.let { c ->
                val cb = normalize(c)
                val l = maxOf(b[0], cb[0]); val bt = maxOf(b[1], cb[1]); val rr = minOf(b[2], cb[2]); val t = minOf(b[3], cb[3])
                if (rr > l && t > bt) b = doubleArrayOf(l, bt, rr, t)
            }
            if (b[2] - b[0] <= 0 || b[3] - b[1] <= 0) b = doubleArrayOf(0.0, 0.0, 612.0, 792.0)
            out += Page(node, r, b)
        }

        private fun normalize(b: DoubleArray) =
            doubleArrayOf(minOf(b[0], b[2]), minOf(b[1], b[3]), maxOf(b[0], b[2]), maxOf(b[1], b[3]))

        private fun pageContent(page: PdfDict): ByteArray {
            val c = file.resolve(page["Contents"])
            val streams: List<PdfStream> = when (c) {
                is PdfStream -> listOf(c)
                is PdfArr -> c.items.mapNotNull { file.stream(it) }
                else -> emptyList()
            }
            if (streams.size == 1) return file.decodedBytes(streams[0])
            val out = ByteArrayOutputStream()
            for (s in streams) {
                out.write(file.decodedBytes(s))
                out.write('\n'.code)
            }
            return out.toByteArray()
        }
    }

    /** Düz öğe listesini, kırpma yığınlarına göre iç içe kırpma gruplarına dönüştürür. */
    private class Builder(val pageRects: List<Rect>) {
        private class Entry(val item: Item, val chain: List<ClipLink>)

        fun build(items: List<Item>, base: ClipLink?): List<Node> =
            level(items.map { Entry(it, chainOf(it.clip, base)) }, 0)

        private fun chainOf(link: ClipLink?, base: ClipLink?): List<ClipLink> {
            val out = ArrayList<ClipLink>()
            var l = link
            while (l != null && l !== base) {
                if (!isNoOp(l)) out += l
                l = l.parent
            }
            out.reverse()
            return out
        }

        /** Sayfanın tamamını kapsayan dikdörtgen kırpmalar (Illustrator her katmana ekler) işe yaramaz. */
        private fun isNoOp(link: ClipLink): Boolean {
            val sp = link.clip.subpaths.singleOrNull() ?: return false
            if (sp.anchors.size != 4 || sp.anchors.any { it.handleIn != null || it.handleOut != null }) return false
            val b = sp.bounds() ?: return false
            val xs = sp.anchors.map { it.point.x }.distinct().size
            val ys = sp.anchors.map { it.point.y }.distinct().size
            if (xs > 2 || ys > 2) return false
            val grown = b.inflate(0.5)
            return pageRects.any { grown.left <= it.left && grown.top <= it.top && grown.right >= it.right && grown.bottom >= it.bottom }
        }

        private fun level(entries: List<Entry>, depth: Int): List<Node> {
            val out = ArrayList<Node>()
            var i = 0
            while (i < entries.size) {
                val e = entries[i]
                if (e.chain.size <= depth) {
                    toNode(e.item)?.let(out::add)
                    i++
                    continue
                }
                val link = e.chain[depth]
                var j = i
                while (j < entries.size && entries[j].chain.size > depth && entries[j].chain[depth] === link) j++
                val kids = level(entries.subList(i, j), depth + 1)
                if (kids.isNotEmpty()) {
                    val only = kids.singleOrNull()
                    // Kendi şekliyle kırpılmış tek yol: kırpma gereksiz.
                    if (only is PathNode && only.stroke == null && only.subpaths == link.clip.subpaths) {
                        out += only
                    } else {
                        out += GroupNode(name = "Kırpma grubu", children = kids, clip = link.clip)
                    }
                }
                i = j
            }
            return out
        }

        private fun toNode(item: Item): Node? = when (item) {
            is Leaf -> item.node
            is Container -> {
                val kids = build(item.items, item.clip)
                val text = item.text
                val only = kids.singleOrNull()
                when {
                    text != null -> textNode(item, text, kids.firstOrNull())
                    kids.isEmpty() -> null
                    item.unwrapSingle && only != null -> {
                        var n = only.withName(item.name)
                        if (!item.visible) n = n.withVisible(false)
                        if (item.locked) n = n.withLocked(true)
                        n
                    }
                    // Tek nesneyi saran saydamlık grubu: opaklık ve karışım modu doğrudan nesneye verilir.
                    only != null && (item.opacity < 1.0 || item.blend != BlendMode.Normal) && item.name == "Grup" &&
                        only.opacity == 1.0 && only.blendMode == BlendMode.Normal ->
                        only.withOpacity(item.opacity).withBlendMode(item.blend)
                    // Adlı grubun içinde yalnızca adsız bir grup varsa (kırpma ya da saydamlık sarmalı) ikisi tek gruptur.
                    only is GroupNode && item.opacity == 1.0 && item.blend == BlendMode.Normal &&
                        (only.name == "Grup" || only.name == "Kırpma grubu") ->
                        only.copy(name = item.name, visible = item.visible, locked = item.locked)
                    else -> GroupNode(
                        name = item.name,
                        children = kids,
                        opacity = item.opacity,
                        blendMode = item.blend,
                        visible = item.visible,
                        locked = item.locked,
                    )
                }
            }
        }

        private fun textNode(item: Container, t: io.github.mhmmtbg.mobileillustrator.pdf.TextProps, sample: Node?): Node {
            var fill: io.github.mhmmtbg.mobileillustrator.model.Paint? = null
            var stroke: io.github.mhmmtbg.mobileillustrator.model.Stroke? = null
            var opacity = 1.0
            var outline: List<io.github.mhmmtbg.mobileillustrator.model.SubPath>? = null
            val blend = sample?.blendMode ?: BlendMode.Normal
            val inv = t.matrix.inverse()
            when (sample) {
                is TextNode -> { fill = sample.fill; stroke = sample.stroke; opacity = sample.opacity }
                is PathNode -> {
                    // Yola çevrilmiş metin: biçimler ve boyalar belge uzayındadır, metnin yerel uzayına geri taşınır.
                    opacity = sample.opacity
                    if (inv != null) outline = sample.subpaths.map { it.transformed(inv) }
                    fill = if (inv != null) sample.fill?.transformedBy(inv) else sample.fill
                    stroke = sample.stroke?.let { st ->
                        val k = t.matrix.meanScale.takeIf { it > 0 } ?: 1.0
                        st.copy(width = st.width / k, paint = if (inv != null) st.paint.transformedBy(inv) else st.paint)
                    }
                }
                else -> {}
            }
            return TextNode(
                name = item.name,
                text = t.text,
                fontSize = t.fontSize,
                fontFamily = t.fontFamily,
                bold = t.bold,
                italic = t.italic,
                fill = fill,
                stroke = stroke,
                opacity = opacity,
                blendMode = blend,
                transform = t.matrix,
                outline = outline,
                align = io.github.mhmmtbg.mobileillustrator.model.TextAlign.entries.firstOrNull { it.name == t.align }
                    ?: io.github.mhmmtbg.mobileillustrator.model.TextAlign.Start,
                lineHeight = t.lineHeight,
            )
        }
    }
}
