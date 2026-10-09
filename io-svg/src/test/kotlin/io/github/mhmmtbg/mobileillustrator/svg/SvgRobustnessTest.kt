package io.github.mhmmtbg.mobileillustrator.svg

import io.github.mhmmtbg.mobileillustrator.model.ImportException
import java.util.Random
import kotlin.test.Test
import kotlin.test.fail

class SvgRobustnessTest {
    private fun mustNotCrash(bytes: ByteArray, label: String) {
        try {
            SvgImporter.import(bytes, "fuzz")
        } catch (e: ImportException) {
        } catch (e: Throwable) {
            fail("$label: beklenmeyen hata kaçtı: $e")
        }
    }

    @Test
    fun mutatedAndHostileSvgNeverCrashes() {
        val base = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 100 100">
<defs><style>.a{fill:url(#g);stroke:#123}</style><linearGradient id="g" xlink:href="#h"/><linearGradient id="h" xlink:href="#g"><stop offset="0"/><stop offset="1" stop-color="red"/></linearGradient>
<clipPath id="c"><use xlink:href="#u"/></clipPath><g id="u"><use xlink:href="#u"/><rect width="5" height="5"/></g></defs>
<g id="L1" clip-path="url(#c)" transform="rotate(30 5 5) scale(2,3) skewX(10) matrix(1 0 0 1 3 4)"><path class="a" d="M0 0C1 2 3 4 5 6S7 8 9 9Q1 1 2 2T3 3A5 5 0 0 1 9 9Z"/>
<text x="1" y="2" font-size="3"><tspan>a</tspan><tspan>b</tspan></text><use xlink:href="#u" x="3"/><image width="2" height="2" xlink:href="data:image/png;base64,AAAA"/></g></svg>"""
        mustNotCrash(base.toByteArray(), "temel")
        val rnd = Random(7)
        val chars = "<>/\"'=#()%.,-0123456789eEMmZzAaCc ".toCharArray()
        for (i in 0 until 400) {
            val sb = StringBuilder(base)
            repeat(1 + rnd.nextInt(6)) { sb.setCharAt(rnd.nextInt(sb.length), chars[rnd.nextInt(chars.size)]) }
            if (i % 4 == 0) sb.setLength(rnd.nextInt(sb.length))
            mustNotCrash(sb.toString().toByteArray(), "mutasyon $i")
        }
        // Çok derin iç içe gruplar
        mustNotCrash(("<svg xmlns=\"http://www.w3.org/2000/svg\">" + "<g>".repeat(3000) + "<rect width=\"1\" height=\"1\"/>" + "</g>".repeat(3000) + "</svg>").toByteArray(), "derin gruplar")
        mustNotCrash(ByteArray(0), "boş")
    }
}
