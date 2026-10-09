package io.github.mhmmtbg.mobileillustrator.platform

import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.mhmmtbg.mobileillustrator.App
import io.github.mhmmtbg.mobileillustrator.model.L10n
import io.github.mhmmtbg.mobileillustrator.model.tr
import io.github.mhmmtbg.mobileillustrator.ui.CoffeeState
import io.github.mhmmtbg.mobileillustrator.ui.PlatformHost
import java.io.File

/**
 * Arayüzün Android'e özgü işleri. Dosya pencereleri sistemin belge seçicisidir; sonuçlar içerik adresi
 * olarak döner. Etkinlik oluşturulurken (onCreate) kurulmalıdır: seçiciler o sırada kaydedilir.
 */
class AndroidHost(private val activity: ComponentActivity) : PlatformHost {
    private val app = activity.application as App
    private var pending: ((String) -> Unit)? = null

    private fun deliver(uri: android.net.Uri?) {
        val callback = pending
        pending = null
        if (uri != null) callback?.invoke(uri.toString())
    }

    private val openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument(), ::deliver)
    private val contentLauncher = activity.registerForActivityResult(ActivityResultContracts.GetContent(), ::deliver)
    private val createLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("*/*"), ::deliver)

    override fun pickDocument(onPicked: (String) -> Unit) {
        pending = onPicked
        openLauncher.launch(arrayOf("*/*"))
    }

    override fun pickFont(onPicked: (String) -> Unit) {
        pending = onPicked
        openLauncher.launch(arrayOf("*/*"))
    }

    override fun pickImage(onPicked: (String) -> Unit) {
        pending = onPicked
        contentLauncher.launch("image/*")
    }

    override fun pickSaveLocation(suggestedName: String, onPicked: (String) -> Unit) {
        pending = onPicked
        createLauncher.launch(suggestedName)
    }

    override fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
        try {
            activity.startActivity(Intent.createChooser(send, tr("Raporu paylaş")))
        } catch (e: Exception) {
            // paylaşacak uygulama yok
        }
    }

    override fun toggleLanguage() {
        App.setLanguage(app, if (L10n.english) "tr" else "en")
        activity.recreate()
    }

    override fun loadThumbnail(file: File): ImageBitmap? = BitmapFactory.decodeFile(file.path)?.asImageBitmap()

    override fun relativeTime(millis: Long): String =
        DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    override val coffee: CoffeeState get() = CoffeeState(app.monetization.price, app.monetization.purchased)

    override fun buyCoffee() = app.monetization.buy(activity)

    @Composable
    override fun AdBanner(modifier: Modifier) {
        if (!app.monetization.purchased) app.ads.Banner(modifier)
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)
}
