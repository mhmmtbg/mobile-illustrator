package io.github.mhmmtbg.mobileillustrator.platform

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import io.github.mhmmtbg.mobileillustrator.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reklamlar (AdMob şerit reklamı). Gerekli bölgelerde önce kullanıcı onayı sorulur; onay alınmadan
 * reklam kitaplığı başlatılmaz. Kahve ısmarlanmışsa hiç başlatılmaz.
 */
class Ads(private val app: Application) {
    /** Reklam kitaplığı hazır mı (onay tamamlandı ve başlatıldı). */
    var ready by mutableStateOf(false)
        private set

    private val requested = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    fun start(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || !requested.compareAndSet(false, true)) return
        try {
            val consent = UserMessagingPlatform.getConsentInformation(activity)
            consent.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        if (consent.canRequestAds()) initialize()
                    }
                },
                {
                    // Onay bilgisi alınamadı (ör. çevrimdışı): önceki oturumun onayı geçerliyse devam edilir.
                    if (consent.canRequestAds()) initialize()
                },
            )
            if (consent.canRequestAds()) initialize()
        } catch (e: RuntimeException) {
            // Google Play hizmetleri olmayan cihaz: reklam gösterilmez, uygulama etkilenmez.
        }
    }

    private fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        Thread {
            try {
                MobileAds.initialize(app) { }
                android.os.Handler(android.os.Looper.getMainLooper()).post { ready = true }
            } catch (e: RuntimeException) {
                initialized.set(false)
            }
        }.start()
    }

    /** Ekran genişliğine uyan şerit reklam. */
    @Composable
    fun Banner(modifier: Modifier) {
        if (!BuildConfig.ADS_ENABLED || !ready) return
        val widthDp = LocalConfiguration.current.screenWidthDp
        // Genişlik değişince (ekran dönünce) reklam yeni boyutla yeniden kurulur.
        key(widthDp) {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    AdView(context).apply {
                        adUnitId = BuildConfig.ADMOB_BANNER_ID
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
