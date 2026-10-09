package io.github.mhmmtbg.mobileillustrator.ui

import io.github.mhmmtbg.mobileillustrator.model.trName
import io.github.mhmmtbg.mobileillustrator.model.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mhmmtbg.mobileillustrator.editor.StoredDocument

/** "Belgelerim": uygulamada duran tüm belgeler. Her şey sürekli otomatik kaydedildiği için burada kayıp olmaz. */
@Composable
fun GalleryScreen(
    host: PlatformHost,
    documents: List<StoredDocument>,
    currentId: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
) {
    var deleting by remember { mutableStateOf<StoredDocument?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Panel)
            // Arkadaki tuvale dokunuş geçmesin
            .clickable(interactionSource = null, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { contentDescription = tr("Belgelerim ekranı") },
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Belgelerim"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = AppColors.OnPanel)
            GalleryAction(tr("Yeni"), onNew)
            GalleryAction(tr("Dosya aç"), onImport)
            BarButton(AppIcons.Close, tr("Belgelerimi kapat"), onClick = onClose)
        }
        HorizontalDivider(color = AppColors.Divider)
        if (documents.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr("Henüz kayıtlı belge yok. Çizmeye başladığında ya da bir dosya açtığında burada görünür."),
                    color = AppColors.OnPanelMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth().weight(1f),
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentCard(host, doc, current = doc.id == currentId, onOpen = { onOpen(doc.id) }, onDelete = { deleting = doc })
                }
            }
        }
        host.AdBanner(Modifier.fillMaxWidth())
    }
    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("\"%s\" silinsin mi?", trName(doc.name))) },
            text = {
                Text(
                    if (doc.linkUri != null) {
                        tr("Belge uygulamadan silinir. Cihazına kaydettiğin dosyaya dokunulmaz.")
                    } else {
                        tr("Bu belge yalnızca uygulamada duruyor. Silersen geri getirilemez.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(doc.id) }) { Text(tr("Sil"), color = Color(0xFFFF8A8A)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Vazgeç")) } },
        )
    }
}

@Composable
private fun GalleryAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.PanelRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = AppColors.OnPanel,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun DocumentCard(host: PlatformHost, doc: StoredDocument, current: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    // Küçük resim küçük bir dosyadır; değişince (modified) yeniden okunur.
    val thumb = remember(doc.id, doc.modified) {
        try { host.loadThumbnail(doc.thumbnail) } catch (e: Throwable) { null }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.PanelRaised)
            .border(1.5.dp, if (current) AppColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = tr("Belge: %s", trName(doc.name)) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).background(AppColors.Pasteboard), contentAlignment = Alignment.Center) {
            if (thumb != null) {
                Image(thumb, contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            }
        }
        Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(trName(doc.name), color = AppColors.OnPanel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val whenText = host.relativeTime(doc.modified)
                val status = when {
                    doc.linkUri == null -> tr("yalnızca uygulamada")
                    doc.unsaved -> tr("dosyaya kaydedilmedi")
                    else -> tr("dosyaya kaydedildi")
                }
                Text("$whenText · $status", color = AppColors.OnPanelMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                Box(
                    Modifier.size(44.dp).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = tr("%s seçenekleri", trName(doc.name)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.More, null, Modifier.size(20.dp), tint = AppColors.OnPanel)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Aç")) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text(tr("Sil")) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
