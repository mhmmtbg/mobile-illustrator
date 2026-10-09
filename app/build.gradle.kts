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
        versionName = "0.2.$ciVersionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Reklam kimlikleri depoya yazılmaz; CI'da gizli değişkenlerden gelir. Tanımlı değilse Google'ın
        // herkese açık deneme kimlikleri kullanılır (deneme reklamı gösterir, gelir üretmez).
        val admobAppId = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544~3347511713"
        val admobBannerId = System.getenv("ADMOB_BANNER_ID")?.takeIf { it.isNotBlank() } ?: "ca-app-pub-3940256099942544/9214589741"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        // Play Console'da tanımlanacak tek seferlik ürünün kimliği.
        buildConfigField("String", "COFFEE_PRODUCT_ID", "\"coffee\"")
    }

    // Mağaza imzası: anahtar depoya konmaz. CI'da gizli değişkenler tanımlıysa onlar kullanılır,
    // yoksa sürüm derlemesi de depodaki ortak anahtarla imzalanır (mağazaya yüklenemez, ama kurulabilir).
    val releaseStore = System.getenv("SIGNING_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
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
            // Otomatik testler reklamsız çalışır: düzen ve zamanlama reklam ağına bağlı kalmasın.
            buildConfigField("Boolean", "ADS_ENABLED", "false")
        }
        release {
            // Kullanılmayan kod atılır ve küçültülür: APK belirgin biçimde küçülür, açılış hızlanır.
            buildConfigField("Boolean", "ADS_ENABLED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Ekranlar ve düzenleyici mantığı Windows sürümüyle ortaktır; kaynakları shared-ui klasöründedir.
    sourceSets["main"].kotlin.srcDir("../shared-ui/src/main/kotlin")
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.compose.ui.tooling.preview)

    // Kahve ısmarlama (tek seferlik satın alma) ve reklamlar
    implementation(libs.play.billing)
    implementation(libs.play.services.ads)
    implementation(libs.play.ump)
    // Reklam kitaplığı eski bir Fragment sürümü getirir; dosya seçicilerin (ActivityResult) doğru çalışması için güncel sürüm.
    implementation(libs.androidx.fragment)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
