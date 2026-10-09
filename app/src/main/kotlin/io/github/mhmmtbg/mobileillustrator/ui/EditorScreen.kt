package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mhmmtbg.mobileillustrator.R
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.editor.PaintTarget
import io.github.mhmmtbg.mobileillustrator.editor.Tool
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import kotlin.math.roundToInt

private val Swatches = listOf(
    0x000000, 0xFFFFFF, 0x8E8E93, 0xE5484D, 0xFF9A3C, 0xFFD43B,
    0x40C057, 0x3FA796, 0x3B9BFF, 0x3B6FE0, 0x7950F2, 0xE64980, 0x14213D, 0x8B5E34,
).map(Rgba::rgb)

@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().background(AppColors.Panel)) {
        TopBar(vm)
        CanvasView(vm, Modifier.weight(1f).fillMaxWidth())
        BottomPanel(vm)
    }
}

@Composable
private fun TopBar(vm: EditorViewModel) {
    val state = vm.state
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.document.name,
            Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.OnPanel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.zoom_percent, (vm.viewport.scale * 100).roundToInt()),
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.OnPanelMuted,
        )
        BarButton(AppIcons.Fit, stringResource(R.string.action_fit), onClick = vm::fitToScreen)
        BarButton(AppIcons.Undo, stringResource(R.string.action_undo), enabled = state.history.canUndo, onClick = vm::undo)
        BarButton(AppIcons.Redo, stringResource(R.string.action_redo), enabled = state.history.canRedo, onClick = vm::redo)
        BarButton(
            AppIcons.Delete,
            stringResource(R.string.action_delete),
            enabled = state.selection.isNotEmpty(),
            onClick = vm::deleteSelection,
        )
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Etiket düğmenin kendisinde durur ki erişilebilirlik servisleri etkin/pasif durumunu doğru okusun.
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) AppColors.OnPanel else AppColors.OnPanelMuted.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun BottomPanel(vm: EditorViewModel) {
    val state = vm.state
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(vertical = 6.dp),
    ) {
        if (state.paintTarget == PaintTarget.Stroke && state.stroke != null) {
            val label = stringResource(R.string.stroke_width)
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(state.strokeWidth * 10).roundToInt() / 10.0} pt",
                    Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.OnPanelMuted,
                )
                Slider(
                    value = state.strokeWidth.toFloat(),
                    onValueChange = { vm.setStrokeWidth(snapWidth(it), commit = false) },
                    onValueChangeFinished = { vm.setStrokeWidth(vm.state.strokeWidth, commit = true) },
                    valueRange = 0.5f..60f,
                    modifier = Modifier.weight(1f).semantics { contentDescription = label },
                )
            }
        }

        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PaintTargetChip(stringResource(R.string.paint_fill), state.fill, state.paintTarget == PaintTarget.Fill) {
                vm.selectPaintTarget(PaintTarget.Fill)
            }
            Spacer(Modifier.width(6.dp))
            PaintTargetChip(stringResource(R.string.paint_stroke), state.stroke, state.paintTarget == PaintTarget.Stroke) {
                vm.selectPaintTarget(PaintTarget.Stroke)
            }
            Spacer(Modifier.width(10.dp))
            val current = if (state.paintTarget == PaintTarget.Fill) state.fill else state.stroke
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwatchButton(null, current == null, stringResource(R.string.paint_none)) { vm.setColor(null) }
                for (c in Swatches) {
                    SwatchButton(c, current == c, "#%06X".format(c.toArgb() and 0xFFFFFF)) { vm.setColor(c) }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(AppIcons.Select, stringResource(R.string.tool_select), state.tool == Tool.Select) { vm.selectTool(Tool.Select) }
            ToolButton(AppIcons.Rectangle, stringResource(R.string.tool_rectangle), state.tool == Tool.Rectangle) { vm.selectTool(Tool.Rectangle) }
            ToolButton(AppIcons.Ellipse, stringResource(R.string.tool_ellipse), state.tool == Tool.Ellipse) { vm.selectTool(Tool.Ellipse) }
            ToolButton(AppIcons.Line, stringResource(R.string.tool_line), state.tool == Tool.Line) { vm.selectTool(Tool.Line) }
        }
    }
}

/** Kaydırıcı değerini yarım puanlık adımlara yuvarlar. */
private fun snapWidth(v: Float): Double = (v * 2).roundToInt() / 2.0

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) AppColors.Accent else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label; selected = active },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color(0xFF2B1A0E) else AppColors.OnPanel)
    }
}

@Composable
private fun PaintTargetChip(label: String, color: Rgba?, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) AppColors.PanelRaised else Color.Transparent)
            .border(1.dp, if (active) AppColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(color, Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.OnPanel)
    }
}

@Composable
private fun SwatchButton(color: Rgba?, active: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label; selected = active },
        contentAlignment = Alignment.Center,
    ) {
        if (active) Box(Modifier.size(36.dp).border(2.dp, AppColors.Accent, CircleShape))
        ColorDot(color, Modifier.size(26.dp))
    }
}

/** Renk yoksa Illustrator'daki gibi üzeri kırmızı çizgili beyaz daire. */
@Composable
private fun ColorDot(color: Rgba?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color?.let { Color(it.toArgb()) } ?: Color.White)
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
            .drawBehind {
                if (color == null) {
                    drawLine(Color(0xFFE5484D), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                }
            },
    )
}
