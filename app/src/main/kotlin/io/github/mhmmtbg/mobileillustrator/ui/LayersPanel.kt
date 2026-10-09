package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.model.GroupNode
import io.github.mhmmtbg.mobileillustrator.model.ImageNode
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.Node
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.TextNode

private sealed interface PanelRow {
    val key: String

    class OfLayer(val layer: Layer, val index: Int, val count: Int) : PanelRow {
        override val key get() = "L" + layer.id
    }

    class OfNode(val node: Node, val depth: Int) : PanelRow {
        override val key get() = "N" + node.id
    }
}

/** Katmanlar ve içlerindeki nesneler; Illustrator'daki gibi en üstteki en başta. */
@Composable
fun LayersPanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val state = vm.state
    val doc = state.document
    var renaming by remember { mutableStateOf<String?>(null) }

    val rows = ArrayList<PanelRow>()
    fun addNodes(nodes: List<Node>, depth: Int) {
        for (n in nodes.asReversed()) {
            rows += PanelRow.OfNode(n, depth)
            if (n is GroupNode && n.id in state.expanded) addNodes(n.children, depth + 1)
        }
    }
    for ((i, layer) in doc.layers.withIndex().reversed()) {
        rows += PanelRow.OfLayer(layer, i, doc.layers.size)
        if (layer.id in state.expanded) addNodes(layer.children, 1)
    }

    Column(modifier.background(AppColors.Panel).semantics { contentDescription = "Katman paneli" }) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Katmanlar", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = AppColors.OnPanel)
            BarButton(AppIcons.Add, "Katman ekle", onClick = vm::addLayer)
            BarButton(AppIcons.Close, "Paneli kapat", onClick = vm::toggleLayersPanel)
        }
        HorizontalDivider(color = AppColors.Divider)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is PanelRow.OfLayer -> LayerRow(vm, row, onRename = { renaming = row.layer.id })
                    is PanelRow.OfNode -> NodeRow(vm, row)
                }
            }
        }
    }

    renaming?.let { id ->
        val layer = doc.layers.firstOrNull { it.id == id }
        if (layer == null) renaming = null else NameDialog("Katmanı adlandır", layer.name, onDismiss = { renaming = null }) { renaming = null; vm.renameLayer(id, it) }
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(role = Role.Switch, onClick = onClick).semantics { contentDescription = label; selected = on },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (on) AppColors.OnPanel else AppColors.OnPanelMuted.copy(alpha = 0.55f))
    }
}

@Composable
private fun LayerRow(vm: EditorViewModel, row: PanelRow.OfLayer, onRename: () -> Unit) {
    val state = vm.state
    val layer = row.layer
    val active = state.activeLayerId == layer.id
    val open = layer.id in state.expanded
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (active) AppColors.PanelRaised else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (active) AppColors.Accent else Color.Transparent))
        SmallToggle(if (layer.visible) AppIcons.Visible else AppIcons.Hidden, "${layer.name} görünürlüğü", layer.visible) { vm.toggleLayerVisible(layer.id) }
        SmallToggle(if (layer.locked) AppIcons.Lock else AppIcons.Unlock, "${layer.name} kilidi", layer.locked) { vm.toggleLayerLocked(layer.id) }
        Box(
            Modifier.size(width = 28.dp, height = 40.dp).clickable(role = Role.Button) { vm.toggleExpanded(layer.id) }
                .semantics { contentDescription = "${layer.name} içeriği"; selected = open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (open) AppIcons.ExpandMore else AppIcons.ChevronRight, null, Modifier.size(20.dp), tint = AppColors.OnPanelMuted)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(role = Role.Tab) { vm.setActiveLayer(layer.id) }
                .semantics { selected = active }
                .padding(horizontal = 4.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(layer.name, color = AppColors.OnPanel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${layer.children.size} nesne", color = AppColors.OnPanelMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Box {
            Box(
                Modifier.size(40.dp).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "${layer.name} seçenekleri" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.More, null, Modifier.size(20.dp), tint = AppColors.OnPanel)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                @Composable
                fun item(label: String, enabled: Boolean = true, action: () -> Unit) =
                    DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { menu = false; action() })
                item("Yeniden adlandır", action = onRename)
                item("Öne taşı", enabled = row.index < row.count - 1) { vm.moveLayer(layer.id, up = true) }
                item("Arkaya taşı", enabled = row.index > 0) { vm.moveLayer(layer.id, up = false) }
                item("Seçimi bu katmana taşı", enabled = state.selection.isNotEmpty()) { vm.moveSelectionToLayer(layer.id) }
                item("Katmanı sil", enabled = row.count > 1) { vm.deleteLayer(layer.id) }
            }
        }
    }
}

@Composable
private fun NodeRow(vm: EditorViewModel, row: PanelRow.OfNode) {
    val state = vm.state
    val node = row.node
    val isSelected = node.id in state.selection
    val kind = when (node) {
        is PathNode -> "Yol"
        is GroupNode -> if (node.clip != null) "Kırpma" else "Grup"
        is ImageNode -> "Görsel"
        is TextNode -> "Metin"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (isSelected) AppColors.Selection.copy(alpha = 0.22f) else Color.Transparent)
            .padding(start = (4 + minOf(row.depth, 6) * 14).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallToggle(if (node.visible) AppIcons.Visible else AppIcons.Hidden, "${node.name} görünürlüğü", node.visible) { vm.toggleNodeVisible(node.id) }
        SmallToggle(if (node.locked) AppIcons.Lock else AppIcons.Unlock, "${node.name} kilidi", node.locked) { vm.toggleNodeLocked(node.id) }
        if (node is GroupNode) {
            val open = node.id in state.expanded
            Box(Modifier.size(width = 28.dp, height = 40.dp).clickable(role = Role.Button) { vm.toggleExpanded(node.id) }, contentAlignment = Alignment.Center) {
                Icon(if (open) AppIcons.ExpandMore else AppIcons.ChevronRight, null, Modifier.size(18.dp), tint = AppColors.OnPanelMuted)
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Row(
            Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button) { vm.selectNode(node.id) }.semantics { selected = isSelected },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (node.name == kind || node.name.isBlank()) kind else node.name,
                Modifier.weight(1f),
                color = AppColors.OnPanel,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (node is GroupNode) "${node.children.size}" else kind,
                Modifier.padding(horizontal = 10.dp),
                color = AppColors.OnPanelMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
