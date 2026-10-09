"""Google Play görsellerini hazırlar: başlıklı ekran görüntüleri, öne çıkan görsel ve simge.

Önce ham ekran görüntüleri üretilir:   ./gradlew :desktop:storeScreenshots     (store/raw/)
Sonra bu betik çalıştırılır:           python3 store/make_listing.py
Çıktı fastlane düzenindedir:           fastlane/metadata/android/<dil>/images/

Uygulamanın adı değişirse yalnızca APP_NAME'i ve metin dosyalarındaki başlığı değiştirmek yeter.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from icon import draw_icon  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "store" / "raw"
OUT = ROOT / "fastlane" / "metadata" / "android"

APP_NAME = "Mobile Illustrator"
LOCALES = {"tr": "tr-TR", "en": "en-US"}
TAGLINE = {
    "tr": "Katmanlı vektör çizim.\nAI, PDF ve SVG dosyalarını aç, düzenle, kaydet.",
    "en": "Layered vector drawing.\nOpen, edit and save AI, PDF and SVG files.",
}
CAPTIONS = {
    "tr": {
        "1-edit": "AI, PDF ve SVG dosyalarını aç ve düzenle",
        "2-layers": "Dosyadaki katmanlar olduğu gibi gelir",
        "5-poster": "Şekil, metin ve gradyanlarla tasarla",
        "3-color": "Baskı için CMYK renkler",
        "4-gallery": "Tüm çalışmaların tek yerde",
    },
    "en": {
        "1-edit": "Open and edit AI, PDF and SVG files",
        "2-layers": "Layers arrive exactly as in the file",
        "5-poster": "Design with shapes, text and gradients",
        "3-color": "CMYK colours for print",
        "4-gallery": "All your work in one place",
    },
}
DEVICES = {"phone": "phoneScreenshots", "tablet7": "sevenInchScreenshots", "tablet10": "tenInchScreenshots"}
BG_TOP = (0x2B, 0x1A, 0x0E)
BG_BOTTOM = (0x1E, 0x1E, 0x20)
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "C:/Windows/Fonts/segoeuib.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
]
FONT_REGULAR = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "C:/Windows/Fonts/segoeui.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
]


def font(size, bold=True):
    for path in (FONT_CANDIDATES if bold else FONT_REGULAR):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def background(width, height):
    img = Image.new("RGB", (width, height))
    px = img.load()
    for y in range(height):
        t = y / max(1, height - 1)
        row = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3))
        for x in range(width):
            px[x, y] = row
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def fit_text(draw, text, max_width, start_size):
    size = start_size
    while size > 20:
        f = font(size)
        if draw.textlength(text, font=f) <= max_width:
            return f
        size -= 2
    return font(size)


def captioned(raw_path, caption):
    """Ekran görüntüsünü aynı boyutta bir zemine, üstünde başlıkla yerleştirir."""
    shot = Image.open(raw_path).convert("RGB")
    w, h = shot.size
    canvas = background(w, h)
    draw = ImageDraw.Draw(canvas)
    band = int(h * (0.13 if h > w else 0.15))
    f = fit_text(draw, caption, int(w * 0.9), int(band * 0.36))
    tw = draw.textlength(caption, font=f)
    draw.text(((w - tw) / 2, band * 0.36), caption, font=f, fill=(255, 255, 255))
    scale = (h - band - int(h * 0.03)) / h
    sw, sh = int(w * scale), int(h * scale)
    small = rounded(shot.resize((sw, sh), Image.LANCZOS), int(min(sw, sh) * 0.035))
    x, y = (w - sw) // 2, band
    ImageDraw.Draw(canvas).rounded_rectangle([x - 3, y - 3, x + sw + 2, y + sh + 2], radius=int(min(sw, sh) * 0.035) + 3, fill=(0x55, 0x55, 0x5A))
    canvas.paste(small, (x, y), small)
    return canvas


def feature_graphic(lang):
    """1024x500 öne çıkan görsel."""
    w, h = 1024, 500
    canvas = background(w, h)
    draw = ImageDraw.Draw(canvas)
    icon = draw_icon(112, rounded=True)
    # Simgenin zemini görselin zeminiyle aynı tonda; ayırt edilsin diye ince bir çerçeve.
    draw.rounded_rectangle([54, 92, 54 + 115, 92 + 115], radius=27, fill=(0x6B, 0x45, 0x25))
    canvas.paste(icon, (56, 94), icon)
    draw.text((56, 232), APP_NAME, font=fit_text(draw, APP_NAME, 440, 50), fill=(255, 255, 255))
    y = 304
    for line in TAGLINE[lang].split("\n"):
        f = fit_text(draw, line, 440, 22) if draw.textlength(line, font=font(22, bold=False)) <= 440 else None
        f = font(22, bold=False)
        while draw.textlength(line, font=f) > 440 and f.size > 12:
            f = font(f.size - 1, bold=False)
        draw.text((56, y), line, font=f, fill=(0xD8, 0xC8, 0xBC))
        y += 34
    shot = Image.open(RAW / lang / "tablet10" / "1-edit.png").convert("RGB")
    sw = 620
    sh = int(shot.height * sw / shot.width)
    small = rounded(shot.resize((sw, sh), Image.LANCZOS), 14)
    x, y = 530, (h - sh) // 2 + 30
    draw.rounded_rectangle([x - 3, y - 3, x + sw + 2, y + sh + 2], radius=17, fill=(0x55, 0x55, 0x5A))
    canvas.paste(small, (x, y), small)
    return canvas


def main():
    if not RAW.exists():
        sys.exit("store/raw yok: önce ./gradlew :desktop:storeScreenshots çalıştır")
    for lang, locale in LOCALES.items():
        images = OUT / locale / "images"
        images.mkdir(parents=True, exist_ok=True)
        draw_icon(512).convert("RGB").save(images / "icon.png")
        feature_graphic(lang).save(images / "featureGraphic.png")
        for device, folder in DEVICES.items():
            target = images / folder
            target.mkdir(exist_ok=True)
            for old in target.glob("*.png"):
                old.unlink()
            for index, (name, caption) in enumerate(CAPTIONS[lang].items(), start=1):
                captioned(RAW / lang / device / f"{name}.png", caption).save(target / f"{index}.png", optimize=True)
        print(locale, "tamam")


if __name__ == "__main__":
    main()
