package io.github.mhmmtbg.mobileillustrator

import android.app.Application
import android.os.Build
import io.github.mhmmtbg.mobileillustrator.model.L10n
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Çökme kaydı. Bir hata uygulamayı düşürürse ayrıntısı cihazda bir dosyaya yazılır; bir sonraki açılışta
 * kullanıcıya gösterilir ve isterse paylaşabilir. Hiçbir veri kendiliğinden bir yere gönderilmez.
 */
class App : Application() {
    /** Kahve ısmarlama (satın alma) ve reklamlar; etkinlik yeniden kurulsa da aynı kalır. */
    val monetization by lazy { io.github.mhmmtbg.mobileillustrator.platform.Monetization(this) }
    val ads by lazy { io.github.mhmmtbg.mobileillustrator.platform.Ads(this) }

    override fun onCreate() {
        super.onCreate()
        applyLanguage(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
                crashFile(this).writeText(
                    "Mobile Illustrator $version\n${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "İş parçacığı: ${thread.name}\n\n$trace",
                )
            } catch (e: Throwable) {
                // Kayıt yazılamasa da çökme olağan yoluna devam etmeli.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(app: Application) = File(app.filesDir, "last-crash.txt")

        private fun languageFile(app: Application) = File(app.filesDir, "language")

        /**
         * Arayüz dili: kullanıcı menüden seçtiyse o ("tr" ya da "en"), seçmediyse cihazın dili.
         * Türkçe dışındaki cihazlarda İngilizce gösterilir.
         */
        fun applyLanguage(app: Application) {
            val chosen = try { languageFile(app).readText().trim() } catch (e: Exception) { "" }
            L10n.english = when (chosen) {
                "tr" -> false
                "en" -> true
                else -> java.util.Locale.getDefault().language != "tr"
            }
        }

        fun setLanguage(app: Application, code: String) {
            try { languageFile(app).writeText(code) } catch (e: Exception) { /* yazılamazsa bu oturum için geçerli olur */ }
            L10n.english = code == "en"
        }
    }
}
