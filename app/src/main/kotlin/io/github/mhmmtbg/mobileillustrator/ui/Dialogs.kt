package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mhmmtbg.mobileillustrator.editor.TextPrompt
import io.github.mhmmtbg.mobileillustrator.model.Rgba

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Ad") }) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun WarningsDialog(warnings: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dosya açıldı, bazı özellikler farklı") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in warnings) Text("• $w", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
    )
}

@Composable
fun NewDocumentDialog(onDismiss: () -> Unit, onCreate: (Double, Double) -> Unit) {
    var w by remember { mutableStateOf("1080") }
    var h by remember { mutableStateOf("1080") }
    val presets = listOf("Kare" to (1080 to 1080), "Yatay HD" to (1920 to 1080), "Dikey HD" to (1080 to 1920), "A4" to (595 to 842))
    val wv = w.replace(',', '.').toDoubleOrNull()
    val hv = h.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni belge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Açık belgenin yerini alır. Kaydetmediysen önce dışa aktar.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, size) in presets) {
                        Text(
                            label,
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.PanelRaised)
                                .clickable(role = Role.Button) { w = size.first.toString(); h = size.second.toString() }
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(w, { w = it }, Modifier.weight(1f), singleLine = true, label = { Text("Genişlik (pt)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(h, { h = it }, Modifier.weight(1f), singleLine = true, label = { Text("Yükseklik (pt)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(wv ?: 1080.0, hv ?: 1080.0) }, enabled = wv != null && hv != null && wv > 0 && hv > 0) { Text("Oluştur") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun TextDialog(prompt: TextPrompt, onDismiss: () -> Unit, onConfirm: (TextPrompt) -> Unit) {
    var text by remember { mutableStateOf(prompt.text) }
    var size by remember { mutableStateOf(prompt.fontSize.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }) }
    var family by remember { mutableStateOf(prompt.fontFamily) }
    var bold by remember { mutableStateOf(prompt.bold) }
    var italic by remember { mutableStateOf(prompt.italic) }
    val sizeValue = size.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prompt.nodeId == null) "Metin ekle" else "Metni düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Metin") }, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        size, { size = it }, Modifier.width(110.dp), singleLine = true, label = { Text("Boyut") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { bold = !bold }) {
                        Checkbox(bold, null)
                        Text("Kalın")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { italic = !italic }) {
                        Checkbox(italic, null)
                        Text("Eğik")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, value) in listOf("Sans" to "sans-serif", "Serif" to "serif", "Mono" to "monospace")) {
                        val on = family == value
                        Text(
                            label,
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) AppColors.Accent else AppColors.PanelRaised)
                                .clickable(role = Role.RadioButton) { family = value }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            color = if (on) Color(0xFF2B1A0E) else AppColors.OnPanel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(prompt.copy(text = text, fontSize = sizeValue ?: prompt.fontSize, fontFamily = family, bold = bold, italic = italic)) },
                enabled = text.isNotBlank() && sizeValue != null && sizeValue > 0,
            ) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun ColorPickerDialog(initial: Rgba, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.copy(a = 1.0).toArgb(), it) } }
    var hue by remember { mutableStateOf(hsv0[0]) }
    var sat by remember { mutableStateOf(hsv0[1]) }
    var value by remember { mutableStateOf(hsv0[2]) }
    var hex by remember { mutableStateOf("%06X".format(initial.toArgb() and 0xFFFFFF)) }
    fun argb() = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    fun syncHex() { hex = "%06X".format(argb() and 0xFFFFFF) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renk seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(argb())))
                Spacer(Modifier.height(6.dp))
                Text("Ton", style = MaterialTheme.typography.labelMedium)
                Slider(hue, { hue = it; syncHex() }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Ton" })
                Text("Doygunluk", style = MaterialTheme.typography.labelMedium)
                Slider(sat, { sat = it; syncHex() }, modifier = Modifier.semantics { contentDescription = "Doygunluk" })
                Text("Parlaklık", style = MaterialTheme.typography.labelMedium)
                Slider(value, { value = it; syncHex() }, modifier = Modifier.semantics { contentDescription = "Parlaklık" })
                OutlinedTextField(
                    hex,
                    { raw ->
                        val clean = raw.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hex = clean
                        if (clean.length == 6) clean.toIntOrNull(16)?.let { rgb ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(rgb or (0xFF shl 24), hsv)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        }
                    },
                    singleLine = true,
                    label = { Text("Onaltılık (RRGGBB)") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Rgba.fromArgb(argb())) }) { Text("Uygula") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
