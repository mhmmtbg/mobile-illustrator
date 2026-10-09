# Mobile Illustrator

Android için katmanlı vektör çizim uygulaması. Hedef: Illustrator (.ai) dosyalarını
katmanlarıyla açıp telefonda ve tablette düzenleyebilmek.

> Bu proje Adobe ile bağlantılı değildir. "Illustrator" Adobe'nin tescilli markasıdır.

## APK'yı indirme

Her `main` push'unda GitHub Actions bir APK derler ve
[Releases](../../releases) sayfasına koyar. En üstteki derlemeyi indirip kurman yeterli.
Tüm derlemeler aynı anahtarla imzalanır, yeni sürüm eskisinin üzerine kurulur.

Android 8.0 (API 26) ve üzeri gerekir.

## Durum

| Aşama | İçerik | Durum |
| --- | --- | --- |
| 1 | Repo, CI, proje iskeleti | Tamam |
| 2 | Belge modeli, tuval, kaydırma/yakınlaştırma, geri al/yinele | Sürüyor |
| 3 | Katman paneli | Bekliyor |
| 4 | Araçlar: düğüm düzenleme, kalem, metin, gradyan, Pathfinder | Bekliyor |
| 5 | SVG ve .ai içe aktarma; SVG, PDF, PNG dışa aktarma | Bekliyor |
| 6 | Kalem basıncı, otomatik kayıt, performans, imzalı sürüm | Bekliyor |

## Modüller

| Modül | Tür | Görev |
| --- | --- | --- |
| `app` | Android uygulaması | Arayüz (Jetpack Compose), araçlar, durum yönetimi |
| `core-model` | Saf Kotlin | Belge, katman, grup, yol, geometri, isabet testi, geçmiş |
| `core-render` | Android kitaplığı | Belgeyi `android.graphics.Canvas` üzerine çizer |
| `io-svg` | Saf Kotlin | SVG okuma ve yazma |
| `io-ai` | Saf Kotlin | .ai içe aktarma |

`core-model` Android'e bağımlı değildir; testleri saniyeler içinde çalışır.

## Yerelde derleme

JDK 17 ve Android SDK (platform 36) gerekir.

```sh
./gradlew :core-model:test        # birim testleri
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

## .ai desteğinin sınırları

- Yalnızca "PDF uyumlu dosya oluştur" seçeneğiyle kaydedilmiş .ai dosyaları tam açılır.
- Canlı efektler, mesh gradyan, semboller ve fırçalar düzleştirilmiş şekil olarak gelir.
- Gerçek .ai yazılmaz. Dışa aktarma SVG ve PDF'tir; ikisini de Illustrator açar.

## Lisans

[MIT](LICENSE)
