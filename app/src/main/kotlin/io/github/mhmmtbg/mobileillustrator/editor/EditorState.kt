package io.github.mhmmtbg.mobileillustrator.editor

import androidx.compose.ui.geometry.Offset
import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.AnchorRef
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.History
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Vec2

enum class Tool { Select, Direct, Pen, Pencil, Rectangle, Ellipse, Line, Text }

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
    val opacity: Double = 1.0,
    val activeLayerId: String,
    val nodeEdit: NodeEdit? = null,
    val pen: PenState? = null,
    /** Seçim çerçevesi (belge koordinatı). */
    val marquee: Rect? = null,
    val layersOpen: Boolean = false,
    /** Katman panelinde açık duran katman ve grupların kimlikleri. */
    val expanded: Set<String> = emptySet(),
    val busy: String? = null,
    val message: String? = null,
    /** İçe aktarmada birebir aktarılamayan özellikler; pencere olarak gösterilir. */
    val warnings: List<String> = emptyList(),
    val textPrompt: TextPrompt? = null,
) {
    val document: Document get() = preview ?: history.present
}
