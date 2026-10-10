package io.github.mhmmtbg.mobileillustrator.editor

/**
 * Uygulamayla birlikte gelen açık lisanslı fontlar (çoğu SIL Open Font License; lisans metinleri fontların yanında,
 * `fonts/licenses` klasöründedir). Hepsi Türkçe harfleri içerir. Metin düğümlerinde yüklenen fontlar gibi
 * "font:<ad>" biçiminde anılırlar; böylece belge her cihazda aynı görünür.
 */
object BundledFonts {
    val names: List<String> = listOf(
        "Abril Fatface", "Alfa Slab One", "Amatic SC", "Anton", "Architects Daughter", "Archivo Black", "Audiowide", "Bangers",
        "Barlow Condensed", "Bebas Neue", "Bungee", "Cinzel", "Comfortaa", "Courgette", "Courier Prime", "Crimson Text",
        "DM Serif Display", "Dancing Script", "Indie Flower", "Josefin Sans", "Libre Baskerville", "Lora", "Luckiest Guy",
        "Marck Script", "Monoton", "Oswald", "Outfit", "Pacifico", "Parisienne", "Paytone One", "Pirata One", "Poppins",
        "Press Start 2P", "Quicksand", "Righteous", "Russo One", "Sacramento", "Sora", "Space Mono", "Staatliches",
    )

    private val known = names.toSet()

    /** Fontun uygulama varlıkları içindeki yolu; böyle bir font yoksa `null`. */
    fun assetPath(name: String): String? = if (name in known) "fonts/" + name.replace(" ", "") + ".ttf" else null
}
