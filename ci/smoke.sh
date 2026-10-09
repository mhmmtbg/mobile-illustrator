#!/usr/bin/env bash
# Emülatörde duman testi: uygulamayı kurar, açar, birkaç işlem yapar, ekran görüntüsü alır.
# Çökme olursa ya da beklenen arayüz durumu oluşmazsa hata ile çıkar.
# Kullanım: ci/smoke.sh <apk> <çıktı klasörü>
set -euo pipefail

APK=$1
OUT=$2
PKG=io.github.mhmmtbg.mobileillustrator
mkdir -p "$OUT"

fail() {
  echo "::error title=Duman testi::$1"
  adb logcat -d -b crash | tail -n 60 || true
  adb exec-out screencap -p > "$OUT/99-hata.png" || true
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

tap() { dump; local xy; xy=$(node "$1" center) || fail "Öğe bulunamadı: $1"; adb shell input tap $xy; sleep 1; }
expect() { dump; local v; v=$(node "$1" "$2") || fail "Öğe bulunamadı: $1"; [ "$v" = "$3" ] || fail "$1.$2 = $v, beklenen $3 ($4)"; }

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 8
alive "açılış"
shot 01-acilis

read -r W H < <(adb shell wm size | tail -n 1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')

expect "Geri al" enabled false "açılışta geçmiş boş olmalı"

# Dikdörtgen çiz
tap "Dikdörtgen"
adb shell input swipe $((W * 25 / 100)) $((H * 30 / 100)) $((W * 70 / 100)) $((H * 45 / 100)) 500
alive "dikdörtgen çizimi"
shot 02-dikdortgen
expect "Geri al" enabled true "çizimden sonra geri alınabilmeli"
expect "Sil" enabled true "yeni şekil seçili olmalı"

# Elips çiz, rengini değiştir
tap "Elips"
adb shell input swipe $((W * 40 / 100)) $((H * 50 / 100)) $((W * 80 / 100)) $((H * 62 / 100)) 500
tap "#3B9BFF"
tap "Kontur"
shot 03-elips-kontur
expect "Kontur kalınlığı" enabled true "kontur seçiliyken kalınlık kaydırıcısı görünmeli"
tap "Renk yok"
alive "renk değişimi"

# Seçim aracıyla taşı
tap "Seçim"
adb shell input swipe $((W * 60 / 100)) $((H * 56 / 100)) $((W * 60 / 100)) $((H * 36 / 100)) 500
shot 04-tasima

# Geri al / yinele
tap "Geri al"
tap "Geri al"
expect "Yinele" enabled true "geri aldıktan sonra yinelenebilmeli"
shot 05-geri-al
tap "Yinele"

# Sil ve döndür
tap "Sil"
expect "Sil" enabled false "silince seçim boşalmalı"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
alive "ekran döndürme"
shot 06-yatay
adb shell settings put system user_rotation 0
sleep 2
tap "Ekrana sığdır"
alive "son"
shot 07-son

if adb logcat -d -b crash | grep -q "$PKG"; then fail "Çökme kaydı bulundu"; fi
echo "Duman testi geçti"
