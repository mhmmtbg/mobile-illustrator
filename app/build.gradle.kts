plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI her derlemede artan bir numara verir; böylece yeni APK eskisinin üzerine kurulur.
val ciVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "io.github.mhmmtbg.mobileillustrator"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mhmmtbg.mobileillustrator"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "0.1.$ciVersionCode"
    }

    signingConfigs {
        // Depodaki sabit debug anahtarı: her CI derlemesi aynı imzayı taşır,
        // güncelleme için uygulamayı kaldırmak gerekmez. Gizli bir anahtar değildir.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-render"))
    implementation(project(":io-svg"))
    implementation(project(":io-ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
