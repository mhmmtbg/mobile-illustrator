import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// CI her derlemede artan bir numara verir (Windows kurulumunda yeni sürüm eskisinin üzerine kurulur).
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":io-svg"))
    implementation(project(":io-ai"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.swing)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "io.github.mhmmtbg.mobileillustrator.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mobile Illustrator"
            packageVersion = "1.0.$buildNumber"
            description = "Katmanlı vektör çizim; .ai, PDF ve SVG dosyalarını açar ve kaydeder"
            vendor = "mhmmtbg"
            // Paketlenen Java çalışma ortamına alınacak modüller (dosya pencereleri, görsel okuma, XML).
            modules("java.desktop", "java.xml", "java.logging")
            windows {
                menuGroup = "Mobile Illustrator"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Sabit kimlik: yeni kurulum eskisini günceller.
                upgradeUuid = "6f1d1c0e-3c53-4f6f-9d0a-7a1f5d2b8c41"
            }
        }
    }
}
