#!/usr/bin/env bash
# Emülatörde çalışan tüm testler, sırayla:
#  1) Araç testleri (çoklu dokunuş hareketleri, görsel regresyon) - hata ayıklama derlemesi
#  2) Uçtan uca duman testi - hata ayıklama derlemesi
#  3) Yayımlanan (küçültülmüş) sürüm derlemesinin kısa denetimi
# Kullanım: ci/device-tests.sh <dist klasörü> <çıktı klasörü> <sürüm numarası>
set -euo pipefail

DIST=$1
OUT=$2
VERSION=$3
PKG=io.github.mhmmtbg.mobileillustrator
mkdir -p "$OUT"

echo "== 1) Araç testleri =="
adb install -r "$DIST/test/app-debug.apk" > /dev/null
adb install -r "$DIST/test/app-debug-androidTest.apk" > /dev/null
adb shell am instrument -w "$PKG.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tr -d '\r' | tee "$OUT/arac-testleri.txt"
# Çizilen görüntüyü al (onaylı görüntüyle karşılaştırma için sürüm sayfasına eklenir).
adb shell run-as "$PKG" cat files/render-actual.png > "$OUT/cizim-android.png" 2> /dev/null || true
[ -s "$OUT/cizim-android.png" ] || rm -f "$OUT/cizim-android.png"
if ! grep -q "^OK (" "$OUT/arac-testleri.txt"; then
  summary=$(grep -E "^[0-9]+\) |AssertionError|Error:|FAILURES|Tests run" "$OUT/arac-testleri.txt" | head -n 12 | sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')
  echo "::error title=Araç testleri::${summary}"
  exit 1
fi
adb shell pm clear "$PKG" > /dev/null
adb uninstall "$PKG.test" > /dev/null || true

echo "== 2) Duman testi =="
bash ci/smoke.sh "$DIST/test/app-debug.apk" "$OUT"

echo "== 3) Sürüm derlemesi =="
adb uninstall "$PKG" > /dev/null || true
bash ci/smoke.sh "$DIST/mobile-illustrator-$VERSION.apk" "$OUT/surum" quick
