package io.github.mhmmtbg.mobileillustrator.editor

import androidx.compose.ui.graphics.ImageBitmap
import io.github.mhmmtbg.mobileillustrator.trace.TraceOptions
import androidx.compose.ui.geometry.Offset
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.AnchorRef
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.History
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.LineJoin
import io.github.mhmmtbg.mobileillustrator.model.SnapResult
import io.github.mhmmtbg.mobileillustrator.model.TextAlign
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Vec2

/** [Hand]: yalnızca tuvali kaydırır; dokunuş hiçbir şeyi seçmez ya da çizmez. */
enum class Tool { Select, Direct, Pen, Pencil, Rectangle, Ellipse, Line, Text, Eyedropper, Hand }

/** Kontur çizgi türü; aralıklar kalınlığa oranlıdır. */
enum class DashStyle(val pattern: List<Double>) {
    Solid(emptyList()), Dashed(listOf(3.0, 2.0)), LongDashed(listOf(6.0, 3.0)), Dotted(listOf(0.0, 2.0));

    fun forWidth(width: Double): List<Double> = pattern.map { it * width }

    companion object {
        /** Var olan bir kesik çizgi desenine en yakın tür. */
        fun of(dash: List<Double>, width: Double): DashStyle {
            if (dash.size < 2 || width <= 0) return Solid
            val a = dash[0] / width
            return when {
                a < 0.5 -> Dotted
                a > 4.5 -> LongDashed
                else -> Dashed
            }
        }
    }
}

enum class PaintTarget { Fill, Stroke, Opacity }

enum class ExportFormat(val extension: String, val mimeType: String) {
    Ai("ai", "application/octet-stream"),
    Pdf("pdf", "application/pdf"),
    Svg("svg", "image/svg+xml"),
    Png("png", "image/png"),
}

/** Seçim kutusunun tutamaçları. */
enum class Handle(val fx: Double, val fy: Double) {
    NW(0.0, 0.0), N(0.5, 0.0), NE(1.0, 0.0), E(1.0, 0.5), SE(1.0, 1.0), S(0.5, 1.0), SW(0.0, 1.0), W(0.0, 0.5);

    val opposite: Handle get() = entries[(ordinal + 4) % 8]
    val isCorner: Boolean get() = ordinal % 2 == 0
    fun on(r: Rect) = Vec2(r.left + r.width * fx, r.top + r.height * fy)
}

/** Ekran = belge * [scale] + [offset]. */
data class Viewport(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun toDocument(p: Offset) = Vec2(((p.x - offset.x) / scale).toDouble(), ((p.y - offset.y) / scale).toDouble())
    fun toScreen(v: Vec2) = Offset(v.x.toFloat() * scale + offset.x, v.y.toFloat() * scale + offset.y)

    companion object {
        const val MIN_SCALE = 0.01f
        const val MAX_SCALE = 512f
    }
}

/** Doğrudan seçim aracının üzerinde çalıştığı yol ve (varsa) seçili düğüm. */
data class NodeEdit(val nodeId: String, val anchor: AnchorRef? = null)

/** Kalem aracıyla çizilmekte olan yol. */
data class PenState(val nodeId: String, val layerId: String, val anchors: List<Anchor>)

/** Metin ekleme/düzenleme penceresi. [nodeId] doluysa var olan metin düzenlenir. */
data class TextPrompt(
    val nodeId: String?,
    val position: Vec2,
    val text: String = "",
    val fontSize: Double = 48.0,
    val fontFamily: String = "sans-serif",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: TextAlign = TextAlign.Start,
    val lineHeight: Double = 1.2,
    /** Harf aralığı (font boyutunun binde biri). */
    val tracking: Double = 0.0,
)

/** Vektöre çevirmenin hazır ayarları. [detail]: görselin izlenmeden önce küçültüleceği uzun kenar (piksel). */
enum class TracePreset(val label: String, val options: TraceOptions, val detail: Int) {
    BlackWhite("Siyah-beyaz", TraceOptions(mode = TraceOptions.Mode.BlackWhite, speckle = 6, smoothness = 1.0), 1200),
    Logo("Logo / çizim", TraceOptions(colors = 8, speckle = 8, smoothness = 1.0), 1000),
    Photo("Fotoğraf", TraceOptions(colors = 20, speckle = 16, smoothness = 1.3, maxShapes = 800), 560),
}

/**
 * "Vektöre çevir" penceresinin durumu. Ayarlar değiştikçe iz arka planda yeniden hesaplanır ve önizlemesi gösterilir;
 * "Uygula" o sonucu belgeye ekler.
 */
data class TraceUi(
    val nodeId: String,
    val options: TraceOptions = TracePreset.Logo.options,
    val detail: Int = TracePreset.Logo.detail,
    val preset: TracePreset? = TracePreset.Logo,
    val busy: Boolean = true,
    val preview: ImageBitmap? = null,
    val shapes: Int = 0,
    val anchors: Int = 0,
    val colors: Int = 0,
    val error: String? = null,
)

/** Sekme şeridindeki bir belge. */
data class TabInfo(val id: String, val name: String, val active: Boolean, val unsaved: Boolean)

/** Açık belgenin depodaki kimliği ve (varsa) bağlı olduğu dış dosya. */
data class DocSession(
    val id: String,
    val linkUri: String? = null,
    val linkFormat: ExportFormat? = null,
    val linkWritable: Boolean = false,
    /** Bağlı dosyaya henüz yazılmamış değişiklik var. */
    val unsaved: Boolean = false,
)

data class EditorState(
    val history: History<Document>,
    /** Sürükleme sürerken gösterilen, henüz geçmişe yazılmamış belge. */
    val preview: Document? = null,
    val selection: Set<String> = emptySet(),
    val tool: Tool = Tool.Select,
    val paintTarget: PaintTarget = PaintTarget.Fill,
    val fill: Rgba? = Rgba.rgb(0xFF9A3C),
    val stroke: Rgba? = Rgba.Black,
    val strokeWidth: Double = 4.0,
    val strokeCap: LineCap = LineCap.Butt,
    val strokeJoin: LineJoin = LineJoin.Miter,
    val dashStyle: DashStyle = DashStyle.Solid,
    val opacity: Double = 1.0,
    /** Nesneleri taşırken ve çizerken kenarlara ve ortalara yapışma. */
    val snapping: Boolean = true,
    /** Sürükleme sırasında gösterilen akıllı kılavuzlar. */
    val guides: SnapResult? = null,
    val activeLayerId: String,
    val nodeEdit: NodeEdit? = null,
    val pen: PenState? = null,
    /** Seçim çerçevesi (belge koordinatı). */
    val marquee: Rect? = null,
    val layersOpen: Boolean = false,
    /** Katman panelinde açık duran katman ve grupların kimlikleri. */
    val expanded: Set<String> = emptySet(),
    /** Katman panelinin seçili nesneye kaydırılması istendikçe artar. */
    val revealTick: Int = 0,
    val busy: String? = null,
    val message: String? = null,
    /** İçe aktarmada birebir aktarılamayan özellikler; pencere olarak gösterilir. */
    val warnings: List<String> = emptyList(),
    val textPrompt: TextPrompt? = null,
    val session: DocSession,
    /** Önceki oturum çökmeyle bittiyse hata ayrıntısı; kullanıcıya bir kez gösterilir. */
    val crashReport: String? = null,
    /** Doluysa "Belgelerim" ekranı açıktır. */
    val gallery: List<StoredDocument>? = null,
    /** Doluysa "Vektöre çevir" penceresi açıktır. */
    val trace: TraceUi? = null,
    /** Katman panelinde çoklu seçim kipi: satıra dokunmak seçime ekler ya da seçimden çıkarır. */
    val multiSelect: Boolean = false,
    /** Katman panelinde bütün olarak seçilmiş katmanlar (kopyalanırken katman olarak alınırlar). */
    val layerSelection: Set<String> = emptySet(),
    /** Katman paneli katmanlar yerine belgedeki renkleri gösteriyor. */
    val colorView: Boolean = false,
) {
    val document: Document get() = preview ?: history.present
}
