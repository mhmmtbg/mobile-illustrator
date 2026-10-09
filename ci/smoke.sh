#!/usr/bin/env bash
# Emülatörde duman testi: uygulamayı kurar, gerçek bir .ai dosyası açar, düzenler, dışa aktarır,
# yeniden açar ve ekran görüntüsü alır. Çökme olursa ya da beklenen arayüz durumu oluşmazsa hata ile çıkar.
# Kullanım: ci/smoke.sh <apk> <çıktı klasörü>
set -euo pipefail

APK=$1
OUT=$2
# "quick": yalnızca açılış, dosya açma ve katmanlar (küçültülmüş sürüm derlemesinin bozulmadığını görmek için).
MODE=${3:-full}
PKG=io.github.mhmmtbg.mobileillustrator
mkdir -p "$OUT"

fail() {
  echo "::error title=Duman testi::$1"
  adb logcat -d -b crash | tail -n 80 || true
  adb exec-out screencap -p > "$OUT/99-hata.png" || true
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml" || true
  exit 1
}

alive() { adb shell pidof "$PKG" > /dev/null || fail "Uygulama çöktü: $1"; }
shot() { sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; }
# Arayüz meşgulken (ör. büyük dosya çizilirken) döküm alınamayabilir; birkaç kez denenir.
dump() {
  for _ in 1 2 3 4 5; do
    if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; then adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml"; return 0; fi
    sleep 2
  done
  fail "Arayüz dökümü alınamadı (uygulama yanıt vermiyor olabilir)"
}

# Etiketi (content-desc ya da text) verilen öğenin bir özniteliğini yazar; "center" merkezini verir.
node() {
  python3 - "$OUT/ui.xml" "$1" "$2" "$EN" "$L10N" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, label, attr, en, l10n = sys.argv[1:6]
if en == "1":
    # Arayüz İngilizce ise etiketler uygulamanın kendi çeviri tablosundan çevrilir.
    table = dict(re.findall(r'^\s*"((?:[^"\\]|\\.)*)" to "((?:[^"\\]|\\.)*)",', open(l10n, encoding="utf-8").read(), re.M))
    if label in table:
        label = table[label]
    else:
        for key, value in table.items():
            if key.startswith("%s ") and label.endswith(key[2:]):
                label = value.replace("%s", label[: -len(key[2:])])
                break
for n in ET.parse(path).iter("node"):
    if n.get("content-desc") == label or n.get("text") == label:
        if attr == "center":
            l, t, r, b = map(int, re.findall(r"\d+", n.get("bounds")))
            print((l + r) // 2, (t + b) // 2)
        else:
            print(n.get(attr))
        sys.exit(0)
sys.exit(1)
PY
}

has() { dump; node "$1" enabled > /dev/null; }
tap() { dump; local xy; xy=$(node "$1" center) || fail "Öğe bulunamadı: $1"; adb shell input tap $xy; sleep 1; }
expect() { dump; local v; v=$(node "$1" "$2") || fail "Öğe bulunamadı: $1"; [ "$v" = "$3" ] || fail "$1.$2 = $v, beklenen $3 ($4)"; }
# Uzun işlemler (dosya açma) bitene kadar bekler.
wait_for() { for _ in $(seq 1 40); do if has "$1"; then return 0; fi; sleep 1; done; fail "Beklenen öğe gelmedi: $1"; }
wait_gone() { for _ in $(seq 1 60); do if ! has "$1"; then return 0; fi; sleep 1; done; fail "Hâlâ ekranda: $1"; }
# Açılan uyarı penceresini (varsa) kapatır.
dismiss() { if has "Tamam"; then tap "Tamam"; fi; }

adb install -r "$APK"
adb logcat -c
# Test etiketleri Türkçe. Hata ayıklama derlemesinde arayüz dili Türkçeye sabitlenir; sürüm derlemesinde
# buna izin yoktur, orada cihaz dili (İngilizce) geçerlidir ve etiketler çeviri tablosundan çevrilir.
L10N="$(dirname "$0")/../core-model/src/main/kotlin/io/github/mhmmtbg/mobileillustrator/model/L10n.kt"
if adb shell "run-as $PKG sh -c 'mkdir -p files && echo tr > files/language'" > /dev/null 2>&1 \
  && [ "$(adb shell "run-as $PKG cat files/language" 2> /dev/null | tr -d '\r')" = tr ]; then EN=0; else EN=1; fi
echo "Arayüz dili: $([ "$EN" = 1 ] && echo İngilizce || echo Türkçe)"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 8
alive "açılış"
wait_for "Dosya menüsü"
shot 01-acilis
read -r W H < <(adb shell wm size | tail -n 1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
expect "Geri al" enabled false "açılışta geçmiş boş olmalı"

# 1) Gerçek bir Illustrator dosyası aç
tap "Dosya menüsü"
tap "Örnek dosyayı aç"
sleep 3
dismiss
wait_for "Gopher"
alive "ai açma"
shot 02-ai-acildi

# 2) Katman paneli: dosyadaki üç katman görünmeli
tap "Katmanlar"
wait_for "Katman paneli"
has "Gopher" || fail "Gopher katmanı listede yok"
has "Pallette" || fail "Pallette katmanı listede yok"
has "License" || fail "License katmanı listede yok"
shot 03-katmanlar
tap "Pallette görünürlüğü"
expect "Pallette görünürlüğü" selected false "katman gizlenmeli"
expect "Geri al" enabled true "gizleme geri alınabilmeli"
tap "Gopher içeriği"
shot 04-katman-icerigi
tap "Paneli kapat"

if [ "$MODE" = quick ]; then
  tap "Dikdörtgen"
  adb shell input swipe $((W * 10 / 100)) $((H * 22 / 100)) $((W * 35 / 100)) $((H * 30 / 100)) 500
  expect "Geri al" enabled true "çizim geri alınabilmeli"
  alive "sürüm derlemesi"
  shot 01-surum
  if adb logcat -d -b crash | grep -q "$PKG"; then fail "Çökme kaydı bulundu (sürüm derlemesi)"; fi
  echo "Sürüm derlemesi denetimi geçti"
  exit 0
fi

# 3) Seçim: gopher'ın gövdesine dokun, taşı, ölçekle
tap "Seçim"
adb shell input tap $((W * 46 / 100)) $((H * 44 / 100))
sleep 1
has "Çoğalt" || fail "dokununca nesne seçilmedi"
shot 05-secim
adb shell input swipe $((W * 46 / 100)) $((H * 44 / 100)) $((W * 52 / 100)) $((H * 52 / 100)) 600
tap "Çoğalt"
tap "Öne"
alive "taşıma ve çoğaltma"
shot 06-tasima

# 4) Çizim araçları
tap "Dikdörtgen"
adb shell input swipe $((W * 10 / 100)) $((H * 22 / 100)) $((W * 35 / 100)) $((H * 30 / 100)) 500
tap "Kontur"
tap "Özel renk"
tap "Uygula"
expect "Kontur kalınlığı" enabled true "kontur rengi varken kalınlık kaydırıcısı görünmeli"
tap "Dolgu"

tap "Kalem"
adb shell input tap $((W * 60 / 100)) $((H * 24 / 100))
adb shell input swipe $((W * 75 / 100)) $((H * 30 / 100)) $((W * 82 / 100)) $((H * 26 / 100)) 400
adb shell input tap $((W * 62 / 100)) $((H * 32 / 100))
shot 07-kalem
tap "Kapat ve bitir"
has "Çoğalt" || fail "kalemle çizilen yol seçilmedi"

tap "Kurşun kalem"
adb shell input swipe $((W * 15 / 100)) $((H * 66 / 100)) $((W * 80 / 100)) $((H * 70 / 100)) 700
alive "kurşun kalem"

tap "Metin"
adb shell input tap $((W * 20 / 100)) $((H * 62 / 100))
wait_for "Metin ekle"
adb shell input text "Merhaba"
sleep 1
tap "Tamam"
has "Metni düzenle" || fail "metin eklenmedi"
shot 08-cizimler

# 5) Doğrudan seçim: kalemle çizilen yolun düğümünü taşı
tap "Doğrudan seçim"
adb shell input tap $((W * 66 / 100)) $((H * 28 / 100))
sleep 1
adb shell input swipe $((W * 60 / 100)) $((H * 24 / 100)) $((W * 55 / 100)) $((H * 20 / 100)) 500
shot 09-dugum
alive "düğüm düzenleme"

# 6) Tümünü seç, grupla, geri al/yinele
tap "Dosya menüsü"
tap "Tümünü seç"
tap "Grupla"
has "Grubu çöz" || fail "gruplama çalışmadı"
tap "Geri al"
expect "Yinele" enabled true "geri aldıktan sonra yinelenebilmeli"
tap "Yinele"
# Geri al/yinele seçimi boşaltır: gruba (içindeki dikdörtgene) dokunarak yeniden seç.
adb shell input tap $((W * 22 / 100)) $((H * 26 / 100))
sleep 1
tap "Grubu çöz"
shot 10-grup

# 6b) Dışa aktarma: sistemin dosya kaydetme ekranı üzerinden. Bu ekran Android sürümüne göre değiştiği için
# bulunamazsa test düşmez, yalnızca uyarı verir. Kaydedilen dosyalar incelenmek üzere çıktı klasörüne alınır.
try_export() {
  tap "Dosya menüsü"
  tap "$1"
  sleep 4
  dump
  local xy=""
  for label in SAVE Save KAYDET Kaydet; do xy=$(node "$label" center) && break || true; done
  if [ -z "$xy" ]; then
    echo "::warning title=Duman testi::Kaydetme ekranı tanınmadı ($1)"
    adb exec-out screencap -p > "$OUT/98-kaydet-$2.png" || true
    adb shell input keyevent KEYCODE_BACK; sleep 2
    has "Dosya menüsü" || { adb shell input keyevent KEYCODE_BACK; sleep 2; }
    return 0
  fi
  adb shell input tap $xy
  sleep 5
  wait_for "Dosya menüsü"
  local f
  f=$(adb shell "find /sdcard/Download /sdcard/Documents -name 'Gopher*.$2' 2>/dev/null" | tr -d '\r' | head -n 1)
  if [ -n "$f" ]; then adb pull "$f" "$OUT/disa-aktarim.$2" > /dev/null || true; else echo "::warning title=Duman testi::Kaydedilen dosya bulunamadı ($2)"; fi
  alive "dışa aktarma $2"
}
try_export "Farklı kaydet (.ai)" ai
# Farklı kaydet'ten sonra "Kaydet" sormadan aynı dosyanın üzerine yazmalı.
if [ -f "$OUT/disa-aktarim.ai" ]; then
  before=$(stat -c %s "$OUT/disa-aktarim.ai")
  tap "Elips"
  adb shell input swipe $((W * 40 / 100)) $((H * 14 / 100)) $((W * 55 / 100)) $((H * 19 / 100)) 400
  tap "Dosya menüsü"
  tap "Kaydet"
  sleep 4
  wait_for "Dosya menüsü"
  f=$(adb shell "find /sdcard/Download /sdcard/Documents -name 'Gopher*.ai' 2>/dev/null" | tr -d '\r' | head -n 1)
  adb pull "$f" "$OUT/disa-aktarim.ai" > /dev/null
  after=$(stat -c %s "$OUT/disa-aktarim.ai")
  [ "$before" != "$after" ] || fail "Kaydet bağlı dosyayı güncellemedi (boyut aynı: $before)"
  tap "Seçim"
fi
try_export "PDF dışa aktar" pdf
try_export "SVG dışa aktar" svg
try_export "PNG dışa aktar" png

# 7) Yatay ekran
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
alive "ekran döndürme"
tap "Ekrana sığdır"
shot 11-yatay
adb shell settings put system user_rotation 0
sleep 3

# 8) Otomatik kayıt: uygulamayı kapatıp aç, çalışma geri gelmeli
adb shell input keyevent KEYCODE_HOME
sleep 4
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 6
wait_for "Dosya menüsü"
wait_for "Gopher"
tap "Katmanlar"
has "Pallette" || fail "otomatik kayıttan katmanlar geri gelmedi"
expect "Pallette görünürlüğü" selected false "gizli katman durumu korunmalı"
tap "Paneli kapat"
tap "Ekrana sığdır"
shot 12-otomatik-kayit
alive "otomatik kayıt"

# 9) Belgelerim: yeni belge açmak eskisini silmemeli
tap "Dosya menüsü"
tap "Yeni belge…"
tap "Oluştur"
sleep 3
wait_for "Adsız"
tap "Dikdörtgen"
adb shell input swipe $((W * 30 / 100)) $((H * 40 / 100)) $((W * 60 / 100)) $((H * 55 / 100)) 400
# 9b) Pathfinder, gradyan, sayısal dönüşüm, hizalama: iki şekli birleştir
tap "Elips"
adb shell input swipe $((W * 50 / 100)) $((H * 48 / 100)) $((W * 80 / 100)) $((H * 62 / 100)) 400
tap "Dosya menüsü"
tap "Tümünü seç"
tap "Hizala"
tap "Üste"
tap "‹ Geri"
tap "Şekil"
tap "Birleştir"
sleep 1
alive "pathfinder"
tap "Gradyan"
tap "Uygula"
tap "Dönüştür"
tap "Uygula"
tap "Katmanlar"
has "1 nesne" || fail "Birleştir iki şekli tek yola indirmedi"
tap "Paneli kapat"
shot 13a-birlestir
alive "araçlar"

tap "Dosya menüsü"
tap "Belgelerim"
wait_for "Belgelerim ekranı"
has "Belge: Gopher" || fail "yeni belge açınca önceki belge kayboldu"
has "Belge: Adsız" || fail "yeni belge galeride yok"
shot 13-belgelerim
tap "Belge: Gopher"
sleep 3
wait_gone "Belgelerim ekranı"
wait_for "Katmanlar"
tap "Katmanlar"
has "Pallette" || fail "galeriden açılan belge eksik"
tap "Paneli kapat"
alive "belgelerim"

# 10) Performans: 10.000 nesneli dosyayı "birlikte aç" yoluyla aç, kare sürelerini ölç
STRESS="$(dirname "$APK")/../stress.ai"
if [ -f "$STRESS" ]; then
  adb push "$STRESS" /data/local/tmp/stress.ai > /dev/null
  adb shell run-as "$PKG" cp /data/local/tmp/stress.ai files/stress.ai
  t0=$(date +%s)
  adb shell am start -W -a android.intent.action.VIEW -d "file:///data/user/0/$PKG/files/stress.ai" -n "$PKG/.MainActivity" > /dev/null
  sleep 2
  wait_for "stress"
  t1=$(date +%s)
  dismiss
  sleep 4
  alive "büyük dosya açma"
  shot 14-stres
  tap "Seçim"
  adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1 || true
  # Nesne seç ve sürükle, sonra çerçeveyle çoklu seçip taşı
  for i in 1 2 3; do
    adb shell input swipe $((W * 50 / 100)) $((H * 45 / 100)) $((W * (30 + i * 10) / 100)) $((H * (35 + i * 5) / 100)) 900
  done
  adb shell input swipe $((W * 5 / 100)) $((H * 13 / 100)) $((W * 95 / 100)) $((H * 20 / 100)) 600
  sleep 1
  adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt" 2>&1 || true
  stats=$(grep -aE "Total frames rendered|Janky frames|percentile" "$OUT/gfxinfo.txt" | tr -d '\r' | sed 's/^ *//' | head -n 8 | paste -sd ';' - || true)
  echo "::notice title=Performans (10.000 nesne)::açılış $((t1 - t0)) sn; $stats"
  alive "büyük dosyada düzenleme"
  shot 15-stres-surukleme
else
  echo "::warning title=Duman testi::stress.ai bulunamadı; performans adımı atlandı"
fi

if adb logcat -d -b crash | grep -q "$PKG"; then fail "Çökme kaydı bulundu"; fi
echo "Duman testi geçti"
