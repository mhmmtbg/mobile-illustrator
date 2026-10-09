# Google Play'e yükleme rehberi

Depoda hazır olanlar ve senin yapman gerekenler. Sıra önemlidir: önce imza anahtarı, sonra AdMob,
sonra Play Console.

## Hazır olanlar

| Ne | Nerede |
| --- | --- |
| Mağaza paketi (AAB) | Her derlemenin [Releases](../../../releases) sayfasında `mobile-illustrator-N.aab` |
| Başlık, kısa ve uzun açıklama (TR, EN) | `fastlane/metadata/android/<dil>/*.txt` |
| Simge 512×512 | `fastlane/metadata/android/<dil>/images/icon.png` |
| Öne çıkan görsel 1024×500 | `…/images/featureGraphic.png` |
| Telefon ekran görüntüleri 1080×1920 (5 adet) | `…/images/phoneScreenshots/` |
| 7" tablet 1200×1920 ve 10" tablet 2560×1600 (5'er adet) | `…/images/sevenInchScreenshots/`, `tenInchScreenshots/` |
| Gizlilik politikası (TR, EN) | `docs/privacy-policy.md` |

Görselleri yeniden üretmek için: `./gradlew :desktop:storeScreenshots` ve ardından
`python3 store/make_listing.py`. Ekran görüntüleri uygulamanın gerçek ekranlarıdır (Android ile ortak
arayüz kodu, masaüstü çizicisiyle görüntüye çizilir); yazı tipi telefondakinden biraz farklıdır.

## 1. Uygulamanın adı ve simgesi (yüklemeden önce karar ver)

"Illustrator" Adobe'nin tescilli markasıdır. Bu adla yüklenen bir uygulama Google Play'in "başkasının
markasını kullanma / taklit" kurallarına takılabilir; Adobe'nin şikâyetiyle de kaldırılabilir. Koyu
kahverengi zemin üzerine turuncu simge de Illustrator'ın simgesini andırıyor. Mağazaya çıkmadan önce
adı ve simge renklerini değiştirmeni öneririm. Ad değişirse:

- `app/src/main/res/values/strings.xml` içindeki `app_name`
- `fastlane/metadata/android/*/title.txt`
- `store/make_listing.py` içindeki `APP_NAME` (sonra betiği yeniden çalıştır)
- `desktop/build.gradle.kts` içindeki `packageName` ve pencere başlığı (`Main.kt`)

Açıklamada "Illustrator (.ai) dosyalarını açar" demek, ürünün neyle uyumlu olduğunu anlatan bir
kullanımdır ve açıklamanın sonunda Adobe ile bağlantı olmadığı yazılıdır.

## 2. İmza anahtarı

Play, hata ayıklama anahtarıyla imzalanmış paketi kabul etmez. Bir kez anahtar oluştur ve sakla:

```sh
keytool -genkeypair -v -keystore upload.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 upload.keystore   # çıktıyı SIGNING_KEYSTORE_BASE64 olarak kaydet
```

Depo ayarları → Secrets and variables → Actions altında şunları tanımla: `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`upload`), `SIGNING_KEY_PASSWORD`. Sonraki derlemede
APK ve AAB bu anahtarla imzalanır.

Dikkat: anahtar değişince telefonundaki mevcut kurulumun üzerine güncelleme yapılamaz. Önce
Belgelerim'deki çalışmalarını "Farklı kaydet" ile dışarı al, uygulamayı kaldır, yenisini kur.

## 3. AdMob

1. https://admob.google.com → uygulama ekle (Android) → bir **Banner** reklam birimi oluştur.
2. Depo gizli değişkenleri: `ADMOB_APP_ID` (`ca-app-pub-…~…`) ve `ADMOB_BANNER_ID` (`ca-app-pub-…/…`).
   Tanımlı değilken uygulama Google'ın deneme reklamlarını gösterir; bunlar gelir üretmez.
3. AdMob → Gizlilik ve mesajlaşma: Avrupa için onay mesajını (GDPR) oluştur ve yayımla. Uygulama onay
   penceresini bu ayara göre gösterir.
4. Kendi reklamlarına tıklama; hesap kapatılır. Denerken deneme kimlikleriyle derlenmiş sürümü kullan.

Reklamlar şurada görünür: düzenleyicide üst çubuğun altında ve Belgelerim ekranının altında. Hata
ayıklama derlemesinde (otomatik testlerin kullandığı) reklam kapalıdır.

## 4. Play Console

1. Uygulamayı oluştur (ücretsiz, uygulama). Paket adı: `io.github.mhmmtbg.mobileillustrator`.
2. AAB'yi önce **dahili test** kanalına yükle. Satın alma ürünü ancak bir paket yüklendikten sonra tanımlanabilir.
3. Para kazanma → Ürünler → Uygulama içi ürünler → ürün oluştur:
   - Ürün kimliği: **`coffee`** (uygulama bu kimliği arar; farklıysa `app/build.gradle.kts` içindeki `COFFEE_PRODUCT_ID`'yi değiştir)
   - Ad: "Geliştiriciye kahve ısmarla", fiyat: **₺99,99**, durumu: etkin
   - Tek seferlik ve tüketilmeyen bir üründür: bir hesap bir kez alır, yeni cihazda kendiliğinden geri gelir.
4. Mağaza girişi: metinleri ve görselleri `fastlane/metadata/android/` altından yükle (Türkçe ve İngilizce).
5. Uygulama içeriği:
   - Gizlilik politikası adresi: `docs/privacy-policy.md` dosyasının GitHub adresi (önce içindeki iletişim adresini doldur)
   - Reklamlar: **Evet, reklam içeriyor**
   - Reklam kimliği: **Evet**, amaç: reklamcılık
   - Veri güvenliği: uygulama kendisi veri toplamaz; AdMob nedeniyle "Cihaz ya da diğer kimlikler", "Yaklaşık konum",
     "Uygulama etkileşimleri" ve "Tanılama" verilerinin reklam amacıyla toplandığını ve paylaşıldığını bildir
     (Google'ın güncel AdMob veri güvenliği yönergesine göre doldur)
   - Hedef kitle: 13 yaş ve üzeri (çocuklara yönelik değil)
   - İçerik derecelendirmesi anketini doldur
6. Satın almayı dene: Play Console → Ayarlar → Lisans testi bölümüne kendi hesabını ekle, uygulamayı
   dahili test bağlantısından kur, "Geliştiriciye kahve ısmarla"yı seç. Ödeme deneme kartıyla yapılır;
   ardından reklamların kalktığını gör.

Yeni açılmış kişisel geliştirici hesaplarında Google, üretime çıkmadan önce kapalı test (en az 12
test kullanıcısı, 14 gün) şartı arar; hesabının durumunu Play Console'da görürsün.

## Henüz denenmemiş olanlar

Bunlar ancak senin hesaplarınla denenebilir; kod yazıldı ve derleniyor ama gerçek mağaza ortamında çalıştırılmadı:

- Satın alma akışı (ürün sorgusu, ödeme, onay, yeni cihazda geri yükleme)
- Gerçek reklam birimiyle reklam gösterimi ve Avrupa onay penceresi
