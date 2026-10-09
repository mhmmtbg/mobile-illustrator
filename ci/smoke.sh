#!/usr/bin/env bash
# Emülatörde duman testi: uygulamayı kurar, gerçek bir .ai dosyası açar, düzenler, dışa aktarır,
# yeniden açar ve ekran görüntüsü alır. Çökme olursa ya da beklenen arayüz durumu oluşmazsa hata ile çıkar.
# Kullanım: ci/smoke.sh <apk> <çıktı klasörü>
set -euo pipefail

APK=$1
OUT=$2
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
dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null; adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml"; }

# Etiketi (content-desc ya da text) verilen öğenin bir özniteliğini yazar; "center" merkezini verir.
node() {
  python3 - "$OUT/ui.xml" "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, label, attr = sys.argv[1:4]
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
tap "#000000"
tap "Kontur"
expect "Kontur kalınlığı" enabled true "kontur seçiliyken kalınlık kaydırıcısı görünmeli"
tap "Renk yok"
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
tap "Grubu çöz"
shot 10-grup

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

if adb logcat -d -b crash | grep -q "$PKG"; then fail "Çökme kaydı bulundu"; fi
echo "Duman testi geçti"
