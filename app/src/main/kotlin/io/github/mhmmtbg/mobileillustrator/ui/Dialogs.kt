package io.github.mhmmtbg.mobileillustrator.ui

import io.github.mhmmtbg.mobileillustrator.model.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import io.github.mhmmtbg.mobileillustrator.model.Ink
import io.github.mhmmtbg.mobileillustrator.model.Rgba

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text(tr("Ad")) }) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(tr("Tamam")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
fun WarningsDialog(warnings: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Dosya açıldı, bazı özellikler farklı")) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in warnings) Text("• $w", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Tamam")) } },
    )
}

@Composable
fun NewDocumentDialog(onDismiss: () -> Unit, onCreate: (Double, Double) -> Unit) {
    var w by remember { mutableStateOf("1080") }
    var h by remember { mutableStateOf("1080") }
    val presets = listOf(tr("Kare") to (1080 to 1080), tr("Yatay HD") to (1920 to 1080), tr("Dikey HD") to (1080 to 1920), "A4" to (595 to 842))
    val wv = w.replace(',', '.').toDoubleOrNull()
    val hv = h.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Yeni belge")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Geçerli belgen Belgelerim'de kalır; hiçbir şey silinmez."), style = MaterialTheme.typography.bodySmall)
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
                    OutlinedTextField(w, { w = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("Genişlik (pt)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(h, { h = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("Yükseklik (pt)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(wv ?: 1080.0, hv ?: 1080.0) }, enabled = wv != null && hv != null && wv > 0 && hv > 0) { Text(tr("Oluştur")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
fun TextDialog(prompt: TextPrompt, fonts: List<String>, onLoadFont: () -> Unit, onDismiss: () -> Unit, onConfirm: (TextPrompt) -> Unit) {
    var text by remember { mutableStateOf(prompt.text) }
    var size by remember { mutableStateOf(prompt.fontSize.let { if (it == it.toLong().toDouble()) it.toLong().toString() else ((it * 100).toLong() / 100.0).toString() }) }
    // Font yüklenince pencere açık kalır ve yeni font seçili gelir.
    var family by remember(prompt.fontFamily) { mutableStateOf(prompt.fontFamily) }
    var bold by remember { mutableStateOf(prompt.bold) }
    var italic by remember { mutableStateOf(prompt.italic) }
    var align by remember { mutableStateOf(prompt.align) }
    var leading by remember { mutableStateOf(((prompt.lineHeight * 100).toLong() / 100.0).toString()) }
    val sizeValue = size.replace(',', '.').toDoubleOrNull()
    val leadingValue = leading.replace(',', '.').toDoubleOrNull()
    val keys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prompt.nodeId == null) tr("Metin ekle") else tr("Metni düzenle")) },
        text = {
            // Yükseklik sınırlı: klavye açıkken Tamam/Vazgeç düğmeleri klavyenin arkasında kalmamalı.
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(tr("Metin")) }, minLines = 2, maxLines = 6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(size, { size = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("Boyut")) }, keyboardOptions = keys)
                    OutlinedTextField(leading, { leading = it }, Modifier.weight(1f), singleLine = true, label = { Text(tr("Satır aralığı")) }, keyboardOptions = keys)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { bold = !bold }) {
                        Checkbox(bold, null)
                        Text(tr("Kalın"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { italic = !italic }) {
                        Checkbox(italic, null)
                        Text(tr("Eğik"))
                    }
                }
                val aligns = io.github.mhmmtbg.mobileillustrator.model.TextAlign.entries
                ChoiceRow(tr("Hizalama"), listOf(tr("Sola") to (align == aligns[0]), tr("Ortala") to (align == aligns[1]), tr("Sağa") to (align == aligns[2]))) { align = aligns[it] }
                Text("Font", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val builtIn = listOf("Sans" to "sans-serif", "Serif" to "serif", "Mono" to "monospace")
                    val custom = fonts.map { it to "font:$it" }
                    // Belgedeki font bu cihazda yüklü değilse de listede görünür (seçim kaybolmasın).
                    val missing = if (family.startsWith("font:") && custom.none { it.second == family }) listOf(tr("%s (yüklü değil)", family.removePrefix("font:")) to family) else emptyList()
                    for ((label, value) in builtIn + custom + missing) {
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
                            maxLines = 1,
                        )
                    }
                }
                TextButton(onClick = onLoadFont) { Text(tr("Font yükle (.ttf, .otf)…")) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        prompt.copy(
                            text = text, fontSize = sizeValue ?: prompt.fontSize, fontFamily = family, bold = bold, italic = italic,
                            align = align, lineHeight = leadingValue ?: prompt.lineHeight,
                        ),
                    )
                },
                enabled = text.isNotBlank() && sizeValue != null && sizeValue > 0 && leadingValue != null && leadingValue > 0,
            ) { Text(tr("Tamam")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
fun ColorPickerDialog(initial: Rgba, onDismiss: () -> Unit, onPick: (Rgba) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.copy(a = 1.0).toArgb(), it) } }
    var hue by remember { mutableStateOf(hsv0[0]) }
    var sat by remember { mutableStateOf(hsv0[1]) }
    var value by remember { mutableStateOf(hsv0[2]) }
    var hex by remember { mutableStateOf("%06X".format(initial.toArgb() and 0xFFFFFF)) }
    // Baskı işi için CMYK: değerler dosyaya RGB'ye çevrilmeden yazılır.
    val ink0 = initial.ink
    var cmykMode by remember { mutableStateOf(ink0 is Ink.Cmyk) }
    val start = ink0 as? Ink.Cmyk
    var c by remember { mutableStateOf((start?.c ?: 0.0).toFloat()) }
    var m by remember { mutableStateOf((start?.m ?: 0.0).toFloat()) }
    var y by remember { mutableStateOf((start?.y ?: 0.0).toFloat()) }
    var k by remember { mutableStateOf((start?.k ?: 0.0).toFloat()) }
    fun argb() = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    fun syncHex() { hex = "%06X".format(argb() and 0xFFFFFF) }
    fun cmyk() = Ink.Cmyk(c.toDouble(), m.toDouble(), y.toDouble(), k.toDouble())
    fun step(v: Float) = (v * 100).toInt() / 100f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Renk seç")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val preview = if (cmykMode) Color(cmyk().toRgb().toArgb()) else Color(argb())
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)).background(preview))
                (ink0 as? Ink.Spot)?.let { spot ->
                    Text(
                        tr("Şu anki renk bir spot renk: %s (%%%s). Buradan renk seçersen spot tanımı kalkar.", spot.name, (spot.tint * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ChoiceRow(tr("Renk modeli"), listOf("RGB" to !cmykMode, "CMYK" to cmykMode)) { cmykMode = it == 1 }
                if (cmykMode) {
                    @Composable
                    fun ink(label: String, v: Float, set: (Float) -> Unit) {
                        Text("$label %${(v * 100).toInt()}", style = MaterialTheme.typography.labelMedium)
                        Slider(v, { set(step(it)) }, modifier = Modifier.semantics { contentDescription = label })
                    }
                    ink(tr("Camgöbeği (C)"), c) { c = it }
                    ink(tr("Macenta (M)"), m) { m = it }
                    ink(tr("Sarı (Y)"), y) { y = it }
                    ink(tr("Siyah (K)"), k) { k = it }
                    Text(tr("Ekrandaki renk yaklaşık bir önizlemedir; dosyaya bu CMYK değerleri yazılır."), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(tr("Ton"), style = MaterialTheme.typography.labelMedium)
                    Slider(hue, { hue = it; syncHex() }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = tr("Ton") })
                    Text(tr("Doygunluk"), style = MaterialTheme.typography.labelMedium)
                    Slider(sat, { sat = it; syncHex() }, modifier = Modifier.semantics { contentDescription = tr("Doygunluk") })
                    Text(tr("Parlaklık"), style = MaterialTheme.typography.labelMedium)
                    Slider(value, { value = it; syncHex() }, modifier = Modifier.semantics { contentDescription = tr("Parlaklık") })
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
                        label = { Text(tr("Onaltılık (RRGGBB)")) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(if (cmykMode) cmyk().toRgb() else Rgba.fromArgb(argb())) }) { Text(tr("Uygula")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, (label, on) ->
                Text(
                    label,
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) AppColors.Accent else AppColors.PanelRaised)
                        .clickable(role = Role.RadioButton) { onPick(i) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    color = if (on) Color(0xFF2B1A0E) else AppColors.OnPanel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Kontur ucu, köşesi ve çizgi türü. Değişiklikler anında seçime uygulanır. */
@Composable
fun StrokeOptionsDialog(vm: io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel, onDismiss: () -> Unit) {
    val s = vm.state
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kontur seçenekleri")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val caps = io.github.mhmmtbg.mobileillustrator.model.LineCap.entries
                ChoiceRow(tr("Uç"), listOf(tr("Düz") to (s.strokeCap == caps[0]), tr("Yuvarlak") to (s.strokeCap == caps[1]), tr("Kare") to (s.strokeCap == caps[2]))) {
                    vm.setStrokeStyle(cap = caps[it])
                }
                val joins = io.github.mhmmtbg.mobileillustrator.model.LineJoin.entries
                ChoiceRow(tr("Köşe"), listOf(tr("Sivri") to (s.strokeJoin == joins[0]), tr("Yuvarlak") to (s.strokeJoin == joins[1]), tr("Pah") to (s.strokeJoin == joins[2]))) {
                    vm.setStrokeStyle(join = joins[it])
                }
                val dashes = io.github.mhmmtbg.mobileillustrator.editor.DashStyle.entries
                val labels = listOf(tr("Kesiksiz"), tr("Kesik"), tr("Uzun"), tr("Noktalı"))
                ChoiceRow(tr("Çizgi türü"), dashes.mapIndexed { i, d -> labels[i] to (s.dashStyle == d) }) { vm.setStrokeStyle(dash = dashes[it]) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Tamam")) } },
    )
}

/** Sayısal konum, boyut ve döndürme. */
@Composable
fun TransformDialog(
    bounds: io.github.mhmmtbg.mobileillustrator.model.Rect,
    onDismiss: () -> Unit,
    onFlip: (horizontal: Boolean) -> Unit,
    onApply: (x: Double, y: Double, w: Double, h: Double, angle: Double) -> Unit,
) {
    fun fmt(v: Double): String = ((v * 100).toLong() / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
    var x by remember { mutableStateOf(fmt(bounds.left)) }
    var y by remember { mutableStateOf(fmt(bounds.top)) }
    var w by remember { mutableStateOf(fmt(bounds.width)) }
    var h by remember { mutableStateOf(fmt(bounds.height)) }
    var angle by remember { mutableStateOf("0") }
    var lock by remember { mutableStateOf(true) }
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val ratio = if (bounds.height > 0) bounds.width / bounds.height else 1.0
    val valid = listOf(x, y, w, h, angle).all { num(it) != null } && (num(w) ?: 0.0) > 0 && (num(h) ?: 0.0) > 0
    val keys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Dönüştür")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(x, { x = it }, Modifier.weight(1f), singleLine = true, label = { Text("X") }, keyboardOptions = keys)
                    OutlinedTextField(y, { y = it }, Modifier.weight(1f), singleLine = true, label = { Text("Y") }, keyboardOptions = keys)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        w,
                        { w = it; if (lock) num(it)?.let { v -> if (ratio > 0) h = fmt(v / ratio) } },
                        Modifier.weight(1f), singleLine = true, label = { Text(tr("Genişlik")) }, keyboardOptions = keys,
                    )
                    OutlinedTextField(
                        h,
                        { h = it; if (lock) num(it)?.let { v -> w = fmt(v * ratio) } },
                        Modifier.weight(1f), singleLine = true, label = { Text(tr("Yükseklik")) }, keyboardOptions = keys,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { lock = !lock }) {
                    Checkbox(lock, null)
                    Text(tr("Oranı koru"))
                }
                OutlinedTextField(angle, { angle = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("Döndür (derece)")) }, keyboardOptions = keys)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onFlip(true); onDismiss() }) { Text(tr("Yatay çevir")) }
                    TextButton(onClick = { onFlip(false); onDismiss() }) { Text(tr("Dikey çevir")) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(num(x)!!, num(y)!!, num(w)!!, num(h)!!, num(angle)!!) }, enabled = valid) { Text(tr("Uygula")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

/** Gradyan düzenleyici: tür, açı ve renk durakları. */
@Composable
fun GradientDialog(
    initial: io.github.mhmmtbg.mobileillustrator.model.GradientSpec,
    onDismiss: () -> Unit,
    onApply: (io.github.mhmmtbg.mobileillustrator.model.GradientSpec) -> Unit,
) {
    var radial by remember { mutableStateOf(initial.radial) }
    var angle by remember { mutableStateOf(initial.angleDegrees.toFloat()) }
    // Çok duraklı (içe aktarılmış) gradyanlar en çok altı durağa indirilir.
    var stops by remember {
        mutableStateOf(
            initial.stops.sortedBy { it.offset }.let { all ->
                if (all.size <= 6) all else (0 until 6).map { all[it * (all.size - 1) / 5] }
            },
        )
    }
    var picking by remember { mutableStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Gradyan")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val brushStops = stops.sortedBy { it.offset }.map { it.offset.toFloat() to Color(it.color.toArgb()) }.toTypedArray()
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (brushStops.size >= 2) androidx.compose.ui.graphics.Brush.horizontalGradient(*brushStops) else androidx.compose.ui.graphics.SolidColor(Color.Gray)),
                )
                ChoiceRow(tr("Tür"), listOf(tr("Doğrusal") to !radial, tr("Dairesel") to radial)) { radial = it == 1 }
                if (!radial) {
                    Text(tr("Açı: %s°", angle.toInt()), style = MaterialTheme.typography.labelMedium)
                    Slider(angle, { angle = (it / 5).toInt() * 5f }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = tr("Gradyan açısı") })
                }
                stops.forEachIndexed { i, stop ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(44.dp).height(44.dp).clickable(role = Role.Button) { picking = i }.semantics { contentDescription = tr("Durak %s rengi", i + 1) },
                            contentAlignment = Alignment.Center,
                        ) { ColorDot(stop.color, Modifier.width(28.dp).height(28.dp)) }
                        Slider(
                            stop.offset.toFloat(),
                            { v -> stops = stops.toMutableList().also { l -> l[i] = stop.copy(offset = (v * 100).toInt() / 100.0) } },
                            modifier = Modifier.weight(1f).semantics { contentDescription = tr("Durak %s konumu", i + 1) },
                        )
                        TextButton(onClick = { stops = stops.filterIndexed { k, _ -> k != i } }, enabled = stops.size > 2) { Text(tr("Sil")) }
                    }
                }
                TextButton(
                    onClick = {
                        val sorted = stops.sortedBy { it.offset }
                        val a = sorted[sorted.size - 2]
                        val b = sorted[sorted.size - 1]
                        // Yeni durak, son iki durağın ortasına ve ortalama renkle eklenir.
                        val mid = io.github.mhmmtbg.mobileillustrator.model.GradientStop(
                            (a.offset + b.offset) / 2,
                            Rgba((a.color.r + b.color.r) / 2, (a.color.g + b.color.g) / 2, (a.color.b + b.color.b) / 2, (a.color.a + b.color.a) / 2),
                        )
                        stops = stops + mid
                    },
                    enabled = stops.size < 6,
                ) { Text(tr("Durak ekle")) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(io.github.mhmmtbg.mobileillustrator.model.GradientSpec(radial, angle.toDouble(), stops.sortedBy { it.offset })) }) { Text(tr("Uygula")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
    if (picking in stops.indices) {
        val i = picking
        ColorPickerDialog(stops[i].color, onDismiss = { picking = -1 }) { c ->
            stops = stops.toMutableList().also { l -> l[i] = l[i].copy(color = c) }
            picking = -1
        }
    }
}

@Composable
fun CrashDialog(onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Uygulama geçen sefer beklenmedik biçimde kapandı")) },
        text = {
            Text(
                tr(
                    "Çalışman otomatik kaydedildiği için yerinde olmalı. Hatanın ayrıntısı cihazında kaydedildi; düzeltilebilmesi için raporu geliştiriciyle paylaşabilirsin. Rapor yalnızca teknik hata bilgisini ve cihaz modelini içerir.",
                ),
            )
        },
        confirmButton = { TextButton(onClick = onShare) { Text(tr("Raporu paylaş")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Kapat")) } },
    )
}
