"""Uygulama simgesini çizer (Android simgesindeki kalem eğrisinin aynısı).

Kullanım: python3 store/icon.py  ->  store/play/icon-512.png, desktop simgeleri
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
BACKGROUND = (0x2B, 0x1A, 0x0E, 255)
ORANGE = (0xFF, 0x9A, 0x3C, 255)
BROWN = (0xB3, 0x6A, 0x2A, 255)
WHITE = (255, 255, 255, 255)


def cubic(p0, p1, p2, p3, steps=200):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        pts.append((
            u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
            u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1],
        ))
    return pts


def draw_icon(size, rounded=False, transparent_corners=False):
    """Simgeyi [size] pikselde çizer. Çizim 108 birimlik alanın ortadaki 72 birimini doldurur."""
    ss = 4
    n = size * ss
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if rounded:
        d.rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * 0.22), fill=BACKGROUND)
    else:
        d.rectangle([0, 0, n, n], fill=BACKGROUND)
    k = n / 72.0

    def p(x, y):
        return ((x - 18) * k, (y - 18) * k)

    def line(points, color, width):
        # Kalın çizgi, yol boyunca sık aralıklarla basılan dairelerle çizilir (ek yerlerinde boşluk kalmaz).
        r = width * k / 2
        dense = []
        for (a, b) in zip(points, points[1:]):
            steps = max(1, int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))))
            dense += [(a[0] + (b[0] - a[0]) * i / steps, a[1] + (b[1] - a[1]) * i / steps) for i in range(steps + 1)]
        for (x, y) in dense:
            d.ellipse([x - r, y - r, x + r, y + r], fill=color)

    # tutamaç çizgileri
    line([p(34, 72), p(34, 52)], BROWN, 1.5)
    line([p(74, 36), p(74, 56)], BROWN, 1.5)
    # eğri
    line([p(*q) for q in cubic((34, 72), (34, 44), (74, 64), (74, 36))], ORANGE, 4)
    # düğümler
    for (x, y) in ((34, 72), (74, 36)):
        a = p(x - 4, y - 4)
        b = p(x + 4, y + 4)
        d.rectangle([a[0], a[1], b[0], b[1]], fill=WHITE)
    # tutamaç uçları
    for (x, y) in ((34, 52), (74, 56)):
        a = p(x - 3, y - 3)
        b = p(x + 3, y + 3)
        d.ellipse([a[0], a[1], b[0], b[1]], fill=ORANGE)
    return img.resize((size, size), Image.LANCZOS)


def main():
    play = ROOT / "store" / "play"
    play.mkdir(parents=True, exist_ok=True)
    # Google Play simgesi: 512x512, köşeleri mağaza yuvarlar; saydamlık olmamalı.
    draw_icon(512).convert("RGB").save(play / "icon-512.png")
    # Masaüstü: pencere simgesi ve Windows kurulum simgesi.
    res = ROOT / "desktop" / "src" / "main" / "resources"
    res.mkdir(parents=True, exist_ok=True)
    draw_icon(256, rounded=True).save(res / "icon.png")
    pack = ROOT / "desktop" / "packaging"
    pack.mkdir(parents=True, exist_ok=True)
    draw_icon(256, rounded=True).save(pack / "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    draw_icon(512, rounded=True).save(pack / "icon.png")


if __name__ == "__main__":
    main()
