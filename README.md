# Mobile Illustrator

Android için katmanlı vektör çizim uygulaması. Illustrator (.ai), PDF ve SVG dosyalarını
katmanlarıyla açar, düzenler ve yeniden .ai, PDF, SVG ya da PNG olarak kaydeder.

> Bu proje Adobe ile bağlantılı değildir. "Illustrator" Adobe'nin tescilli markasıdır.

## APK'yı indirme

Her `main` push'unda GitHub Actions bir APK derler ve [Releases](../../releases) sayfasına koyar.
En üstteki derlemeyi indirip kurman yeterli. Tüm derlemeler aynı anahtarla imzalanır, yeni sürüm
eskisinin üzerine kurulur. Android 8.0 (API 26) ve üzeri gerekir.

Her derleme bir emülatörde otomatik denenir: gerçek bir .ai dosyası açılır, katmanlar gezilir,
nesneler taşınır, çizim araçları kullanılır, dosya kaydedilip yeniden açılır, çok parmaklı hareketler ve
çizim çıktısı onaylı görüntüyle karşılaştırılır. Yayımlanan (küçültülmüş) APK ayrıca denenir.
Ekran görüntüleri aynı Release sayfasına eklenir.

## Özellikler

**Belgeler**
- Belgelerim: tüm çalışmaların küçük resimleriyle listelenir; yeni belge açmak eskisini silmez.
- Aç: `.ai` (Illustrator 9 ve sonrası, PDF uyumlu), `.pdf`, `.svg`. Dosya yöneticisinden "birlikte aç" da çalışır.
- Kaydet açtığın dosyanın üzerine yazar; Farklı kaydet yeni bir `.ai` oluşturur. PDF, SVG ve PNG (saydam zemin) dışa aktarılır.
- Otomatik kayıt: uygulama kapansa da çalışma yerinde kalır. Çökme olursa bir sonraki açılışta rapor paylaşma seçeneği çıkar; hiçbir şey kendiliğinden gönderilmez.

**Katmanlar**
- Dosyadaki katmanlar adları, görünürlükleri ve kilitleriyle gelir; çalışma yüzeyleri korunur.
- Katman ekle, sil, yeniden adlandır, sırala, gizle, kilitle; nesneleri katmanlar arasında taşı.
- Her katmanın içindeki nesne ağacı (gruplar, kırpma grupları) panelden gezilir ve seçilir.

**Çizim ve düzenleme**
- Seçim: taşı, ölçekle, döndür, çerçeveyle çoklu seç; sayısal genişlik/yükseklik/açı, yatay ve dikey çevirme.
- Doğrudan seçim: düğümleri ve tutamaçları sürükle, yola dokunarak düğüm ekle, sil, köşe/yumuşak çevir.
- Kalem (Bezier), kurşun kalem, dikdörtgen, elips, çizgi, metin, damlalık.
- Hizalama ve dağıtma, kenarlara ve merkezlere yakalama (kılavuz çizgileriyle).
- Şekil işlemleri: birleştir, öndekini çıkar, kesiştir, dışla. Kırpma maskesi yap/bırak.
- Dolgu ve kontur: düz renk, doğrusal/dairesel gradyan (duraklar ve açı), opaklık; kontur kalınlığı, uç, köşe, kesikli çizgi.
- Renk seçici RGB (ton/doygunluk/parlaklık, onaltılık) ve CMYK modunda çalışır.
- Metin: çok satır, hizalama, satır aralığı, kalın/eğik, kendi `.ttf`/`.otf` fontunu yükleme, yola çevirme.
- Grupla, grubu çöz, çoğalt, sıralama, geri al/yinele, görsel yerleştirme.

**Gezinme:** iki parmakla kaydır ve yakınlaştır. Karmaşık belgelerde hareket sırasında önbellekten
çizilir, parmaklar kalkınca yeniden keskinleşir; görünmeyen nesneler çizilmez.

**Dil:** Türkçe ve İngilizce. Cihaz dili Türkçe değilse İngilizce açılır; Dosya menüsünden değiştirilir.

## .ai desteği: ne gelir, ne gelmez

| Özellik | Durum |
| --- | --- |
| Yollar, bileşik yollar, dolgu, kontur, kesikli çizgi | Tam |
| Katmanlar (ad, görünürlük, kilit), alt katmanlar (grup olarak) | Tam |
| Çalışma yüzeyleri | Tam (yan yana dizilir) |
| Kırpma maskeleri | Tam |
| Doğrusal ve dairesel gradyanlar | Tam |
| Opaklık, karışım modları (Çoğalt, Ekran, ...) | Tam (Android 10+; 9 ve öncesinde 5 temel mod) |
| CMYK, gri ve spot (Pantone) renkler | Değerler ve spot adları korunur, kaydederken aynen geri yazılır. Ekranda yaklaşık sRGB önizleme gösterilir; ICC profilli renk yönetimi yok |
| Gömülü görseller | PNG/JPEG; CMYK JPEG'lerde renk kayabilir |
| Metin | Dosyaya gömülü fontun (TrueType, OpenType/CFF) harf biçimleriyle birebir gösterilir ve metin olarak kalır. Metni düzenlersen cihazın fontuna geçer. Eski Type 1 fontlar cihaz fontuyla gösterilir |
| Ağ (mesh) gradyan, desen dolgusu, opaklık maskesi | Düz renk / yok sayılır (açılışta uyarı gösterilir) |
| Canlı efektler, semboller, fırçalar | Illustrator'ın PDF'e yazdığı düzleştirilmiş haliyle gelir |

Gereksinimler ve sınırlar:
- Dosya "PDF uyumlu dosya oluştur" seçeneğiyle kaydedilmiş olmalı (Illustrator'ın varsayılanı).
  Illustrator 8 ve öncesinin PostScript tabanlı dosyaları açılmaz.
- Kaydedilen `.ai`, Illustrator'ın açtığı PDF yapısındadır; Illustrator'ın belgelenmemiş özel verisi
  (AIPrivateData) yazılmaz. Bu uygulama kendi yazdığı dosyayı kayıpsız geri okur (katmanlar, adlar,
  gizli/kilitli nesneler, düzenlenebilir metin). Illustrator'ın aynı dosyada katmanları katman olarak mı
  yoksa grup olarak mı göstereceği sürüme bağlıdır ve burada denenemedi.
- Uygulamada seçilen yeni renkler RGB ya da (renk seçicide CMYK modu) CMYK olarak yazılır. Dosyadaki ICC profilleri geri yazılmaz.

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

## Kendi anahtarınla imzalama

Depoda gizli anahtar tanımlı değilse APK, depodaki ortak hata ayıklama anahtarıyla imzalanır; bu
mağaza yayını için uygun değildir. Kendi anahtarınla imzalamak için depo ayarlarında şu gizli
değerleri tanımla: `SIGNING_KEYSTORE_BASE64` (anahtar deposunun base64 hali),
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Anahtar değişince eski
kurulumun üzerine güncelleme yapılamaz; uygulamayı kaldırıp yeniden kurmak gerekir.

## Çeviriler

Arayüz metinleri kaynakta Türkçedir; İngilizce karşılıkları `core-model` içindeki `L10n.kt`
tablosundadır. Yeni bir metin eklerken `tr("…")` ile sar ve tabloya karşılığını ekle.

## Teşekkür

Örnek dosya (`app/src/main/assets/samples/gopher.ai`): Go gopher'ı Renee French tasarladı; vektör çizim
Hugo Arganda'ya ait, [golang-samples/gopher-vector](https://github.com/golang-samples/gopher-vector)
deposundan, Creative Commons Attribution 3.0 lisansıyla.

## Lisans

[MIT](LICENSE)
