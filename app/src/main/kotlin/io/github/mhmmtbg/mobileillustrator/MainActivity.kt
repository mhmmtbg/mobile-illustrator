package io.github.mhmmtbg.mobileillustrator

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mhmmtbg.mobileillustrator.editor.EditorViewModel
import io.github.mhmmtbg.mobileillustrator.platform.AndroidDocumentIo
import io.github.mhmmtbg.mobileillustrator.platform.AndroidHost
import io.github.mhmmtbg.mobileillustrator.ui.AppTheme
import io.github.mhmmtbg.mobileillustrator.ui.EditorScreen

/** Düzenleyiciyi ekran dönüşlerinde ve dil değişiminde canlı tutar. */
class EditorHolder(app: Application) : AndroidViewModel(app) {
    val editor = EditorViewModel(AndroidDocumentIo(app), viewModelScope)
}

class MainActivity : ComponentActivity() {
    private val holder: EditorHolder by viewModels()
    private val vm: EditorViewModel get() = holder.editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Arayüz her zaman koyu; sistem çubuğu simgeleri açık renk kalsın.
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val host = AndroidHost(this)
        val app = application as App
        setContent {
            AppTheme {
                EditorScreen(vm, host)
            }
        }
        // Satın alma durumu sorgulanır; kahve ısmarlanmamışsa reklamlar (gerekirse onay sorularak) başlatılır.
        app.monetization.start()
        if (!app.monetization.purchased) app.ads.start(this)
        // Dosya yöneticisinden "birlikte aç" ile gelindiyse o dosyayı aç (ekran dönüşünde yeniden açma).
        if (savedInstanceState == null) openFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { vm.open(it.toString()) }
    }

    override fun onStop() {
        super.onStop()
        vm.flushAutosave()
    }
}
