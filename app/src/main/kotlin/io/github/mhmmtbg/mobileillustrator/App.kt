package io.github.mhmmtbg.mobileillustrator

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Çökme kaydı. Bir hata uygulamayı düşürürse ayrıntısı cihazda bir dosyaya yazılır; bir sonraki açılışta
 * kullanıcıya gösterilir ve isterse paylaşabilir. Hiçbir veri kendiliğinden bir yere gönderilmez.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
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
    }
}
