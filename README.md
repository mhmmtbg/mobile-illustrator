# Mobile Illustrator

Android için katmanlı vektör çizim uygulaması. Illustrator (.ai), PDF ve SVG dosyalarını
katmanlarıyla açar, düzenler ve yeniden .ai, PDF, SVG ya da PNG olarak kaydeder.

> Bu proje Adobe ile bağlantılı değildir. "Illustrator" Adobe'nin tescilli markasıdır.

## APK'yı indirme

Her `main` push'unda GitHub Actions bir APK derler ve [Releases](../../releases) sayfasına koyar.
En üstteki derlemeyi indirip kurman yeterli. Tüm derlemeler aynı anahtarla imzalanır, yeni sürüm
eskisinin üzerine kurulur. Android 8.0 (API 26) ve üzeri gerekir.

Her derleme bir emülatörde otomatik denenir: gerçek bir .ai dosyası açılır, katmanlar gezilir,
nesneler taşınır, çizim araçları kullanılır, uygulama kapatılıp açılır. Ekran görüntüleri aynı
Release sayfasına eklenir.

## Özellikler

**Dosyalar**
- Aç: `.ai` (Illustrator 9 ve sonrası, PDF uyumlu), `.pdf`, `.svg`. Dosya yöneticisinden "birlikte aç" da çalışır.
- Kaydet: `.ai`, PDF, SVG, PNG (saydam zemin).
- Otomatik kayıt: uygulama kapansa da son çalışma geri gelir.

**Katmanlar**
- Dosyadaki katmanlar adları, görünürlükleri ve kilitleriyle gelir; çalışma yüzeyleri korunur.
- Katman ekle, sil, yeniden adlandır, sırala, gizle, kilitle; nesneleri katmanlar arasında taşı.
- Her katmanın içindeki nesne ağacı (gruplar, kırpma grupları) panelden gezilir ve seçilir.

**Çizim ve düzenleme**
- Seçim: taşı, köşelerden orantılı ya da kenarlardan tek yönde ölçekle, döndür, çerçeveyle çoklu seç.
- Doğrudan seçim: düğümleri ve tutamaçları sürükle, yola dokunarak düğüm ekle, sil, köşe/yumuşak çevir.
- Kalem (Bezier), kurşun kalem (serbest el, otomatik yumuşatma), dikdörtgen, elips, çizgi, metin.
- Dolgu ve kontur rengi, özel renk seçici, kontur kalınlığı, opaklık.
- Grupla, grubu çöz, çoğalt, öne/arkaya gönder, geri al/yinele.
- Görsel yerleştirme (galeriden).

**Gezinme:** iki parmakla kaydır ve yakınlaştır. Karmaşık belgelerde hareket sırasında önbellekten
çizilir, parmaklar kalkınca yeniden keskinleşir.

## .ai desteği: ne gelir, ne gelmez

| Özellik | Durum |
| --- | --- |
| Yollar, bileşik yollar, dolgu, kontur, kesikli çizgi | Tam |
| Katmanlar (ad, görünürlük, kilit), alt katmanlar (grup olarak) | Tam |
| Çalışma yüzeyleri | Tam (yan yana dizilir) |
| Kırpma maskeleri | Tam |
| Doğrusal ve dairesel gradyanlar | Tam |
| Opaklık, karışım modları (Çoğalt, Ekran, ...) | Tam (Android 10+; 9 ve öncesinde 5 temel mod) |
| CMYK, ICC, Lab ve spot (Pantone) renkler | sRGB'ye çevrilir; renk yönetimi yok |
| Gömülü görseller | PNG/JPEG; CMYK JPEG'lerde renk kayabilir |
| Metin | Düzenlenebilir metin olarak gelir ama cihazın fontuyla gösterilir; özgün font kullanılmaz |
| Ağ (mesh) gradyan, desen dolgusu, opaklık maskesi | Düz renk / yok sayılır (açılışta uyarı gösterilir) |
| Canlı efektler, semboller, fırçalar | Illustrator'ın PDF'e yazdığı düzleştirilmiş haliyle gelir |

Gereksinimler ve sınırlar:
- Dosya "PDF uyumlu dosya oluştur" seçeneğiyle kaydedilmiş olmalı (Illustrator'ın varsayılanı).
  Illustrator 8 ve öncesinin PostScript tabanlı dosyaları açılmaz.
- Kaydedilen `.ai`, Illustrator'ın açtığı PDF yapısındadır; Illustrator'ın belgelenmemiş özel verisi
  (AIPrivateData) yazılmaz. Bu uygulama kendi yazdığı dosyayı kayıpsız geri okur (katmanlar, adlar,
  gizli/kilitli nesneler, düzenlenebilir metin). Illustrator'ın aynı dosyada katmanları katman olarak mı
  yoksa grup olarak mı göstereceği sürüme bağlıdır ve burada denenemedi.
- Renkler RGB olarak yazılır.

## Modüller

| Modül | Tür | Görev |
| --- | --- | --- |
| `app` | Android uygulaması | Arayüz (Jetpack Compose), araçlar, dosya işlemleri |
| `core-model` | Saf Kotlin | Belge, katman, yol, geometri, isabet testi, yol düzenleme, geçmiş |
| `core-render` | Android kitaplığı | Belgeyi `android.graphics.Canvas` üzerine çizer |
| `io-ai` | Saf Kotlin | PDF okuyucu ve yazıcı, .ai içe/dışa aktarma |
| `io-svg` | Saf Kotlin | SVG içe/dışa aktarma |

`core-model`, `io-ai` ve `io-svg` Android'e bağımlı değildir; birim testleri saniyeler içinde çalışır.

## Yerelde derleme

JDK 17 ve Android SDK (platform 36) gerekir.

```sh
./gradlew :core-model:test :io-svg:test :io-ai:test
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

## Teşekkür

Örnek dosya (`app/src/main/assets/samples/gopher.ai`): Go gopher'ı Renee French tasarladı; vektör çizim
Hugo Arganda'ya ait, [golang-samples/gopher-vector](https://github.com/golang-samples/gopher-vector)
deposundan, Creative Commons Attribution 3.0 lisansıyla.

## Lisans

[MIT](LICENSE)
