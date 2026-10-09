package io.github.mhmmtbg.mobileillustrator.editor

import androidx.compose.ui.geometry.Offset
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.History
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Vec2

enum class Tool { Select, Rectangle, Ellipse, Line }

enum class PaintTarget { Fill, Stroke }

/** Ekran = belge * [scale] + [offset]. */
data class Viewport(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    fun toDocument(p: Offset) = Vec2(((p.x - offset.x) / scale).toDouble(), ((p.y - offset.y) / scale).toDouble())
    fun toScreen(v: Vec2) = Offset(v.x.toFloat() * scale + offset.x, v.y.toFloat() * scale + offset.y)

    companion object {
        const val MIN_SCALE = 0.02f
        const val MAX_SCALE = 256f
    }
}

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
    val activeLayerId: String,
) {
    val document: Document get() = preview ?: history.present
}
