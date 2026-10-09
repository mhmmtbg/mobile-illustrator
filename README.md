# Mobile Illustrator

Android (telefon ve tablet) ve Windows için katmanlı vektör çizim uygulaması. Illustrator (.ai), PDF
ve SVG dosyalarını katmanlarıyla açar, düzenler ve yeniden .ai, PDF, SVG ya da PNG olarak kaydeder.

> Bu proje Adobe ile bağlantılı değildir. "Illustrator" Adobe'nin tescilli markasıdır.

## İndirme

Her `main` push'unda GitHub Actions derler ve [Releases](../../releases) sayfasına koyar:

| Dosya | Ne için |
| --- | --- |
| `mobile-illustrator-N.apk` | Android'e doğrudan kurulum (Android 8.0 ve üzeri) |
| `mobile-illustrator-N.aab` | Google Play'e yüklenecek paket |
| `mobile-illustrator-setup-N.exe` | Windows kurulum dosyası |
| `mobile-illustrator-windows-N.zip` | Windows, kurulumsuz: klasörü aç, `Mobile Illustrator.exe` dosyasını çalıştır |

Windows dosyaları imzasızdır; ilk açılışta SmartScreen "Windows kişisel bilgisayarınızı korudu" uyarısı
gösterir ("Ek bilgi" → "Yine de çalıştır"). Java kurmak gerekmez; çalışma ortamı paketin içindedir.

Her derleme otomatik denenir: telefon ve tablet emülatöründe gerçek bir .ai dosyası açılır, katmanlar
gezilir, nesneler taşınır, çizim araçları kullanılır, dosya kaydedilip yeniden açılır; yayımlanan APK
ayrıca denenir. Windows paketi bir Windows makinesinde çalıştırılır: pencere açılır, örnek dosya
yüklenir ve PNG olarak dışa aktarılır. Ekran görüntüleri aynı Release sayfasına eklenir.

## Özellikler

**Belgeler**
- Belgelerim: tüm çalışmaların küçük resimleriyle listelenir; yeni belge açmak eskisini silmez.
- Aç: `.ai` (Illustrator 9 ve sonrası, PDF uyumlu), `.pdf`, `.svg`. Dosya yöneticisinden "birlikte aç" da çalışır.
- Kaydet açtığın dosyanın üzerine yazar; Farklı kaydet yeni bir `.ai` oluşturur. PDF, SVG ve PNG (saydam zemin) dışa aktarılır.
- Otomatik kayıt: uygulama kapansa da çalışma yerinde kalır. Çökme olursa bir sonraki açılışta rapor paylaşma seçeneği çıkar; rapor kendiliğinden gönderilmez.

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

**Tablet:** geniş ekranda araçlar yan şeritte durur, katman paneli tuvali örtmeden yana yerleşir.

**Windows:** aynı ekranlar ve aynı dosya desteği. Fare tekerleği yakınlaştırır, sağ ya da orta tuşla
sürüklemek kaydırır. Kısayollar: Ctrl+Z / Ctrl+Shift+Z, Ctrl+S, Ctrl+O, Ctrl+A, Ctrl+D, Ctrl+G,
Ctrl+0 (sığdır), Delete; araçlar V, A, P, N, M, L, T, I. Komut satırında dosya yolu verilirse o dosya açılır.

**Reklam ve satın alma (yalnızca Android):** uygulama şerit reklam gösterir. Dosya menüsündeki
"Geliştiriciye kahve ısmarla" tek seferlik bir Google Play satın almasıdır; alınınca reklamlar kalıcı
olarak kalkar. Ayrıntılar ve mağaza kurulumu: [store/PLAY-STORE.md](store/PLAY-STORE.md).

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
| `app` | Android uygulaması | Android'e özgü işler: dosya seçiciler, satın alma, reklam |
| `desktop` | Masaüstü uygulaması (Compose Desktop) | Windows sürümü: pencere, kısayollar, Skia ile çizim |
| `shared-ui` | Ortak kaynak klasörü | Ekranlar (Jetpack Compose) ve düzenleyici mantığı; `app` ve `desktop` aynı kaynakları derler |
| `core-model` | Saf Kotlin | Belge, katman, yol, geometri, isabet testi, yol düzenleme, geçmiş, çeviriler |
| `core-render` | Android kitaplığı | Belgeyi `android.graphics.Canvas` üzerine çizer |
| `io-ai` | Saf Kotlin | PDF okuyucu ve yazıcı, .ai içe/dışa aktarma |
| `io-svg` | Saf Kotlin | SVG içe/dışa aktarma |

`shared-ui` bir Gradle modülü değildir: klasörü hem `app` hem `desktop` kaynak olarak ekler. Platforma
bağlı her şey `PlatformHost` arayüzünün ve `DocumentIo` sınıfının arkasındadır. Çizici iki platformda
ayrı yazılmıştır (Android'de `core-render`, masaüstünde `desktop/…/render`), adları ve işlevleri aynıdır.

## Yerelde derleme

JDK 17 ve Android SDK (platform 36) gerekir.

```sh
./gradlew :core-model:test :io-svg:test :io-ai:test
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :desktop:run            # masaüstü sürümünü çalıştırır
./gradlew :desktop:packageExe     # Windows kurulum dosyası (Windows'ta çalıştırılmalı)
```

## Google Play

Mağaza metinleri ve görselleri `fastlane/metadata/android/` altındadır; yükleme adımları, satın alma
ürününün ve reklamların kurulumu [store/PLAY-STORE.md](store/PLAY-STORE.md) dosyasında anlatılır.
Gizlilik politikası: [docs/privacy-policy.md](docs/privacy-policy.md).

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
