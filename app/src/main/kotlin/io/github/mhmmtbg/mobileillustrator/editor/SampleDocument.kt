package io.github.mhmmtbg.mobileillustrator.editor

import io.github.mhmmtbg.mobileillustrator.model.Anchor
import io.github.mhmmtbg.mobileillustrator.model.Artboard
import io.github.mhmmtbg.mobileillustrator.model.Document
import io.github.mhmmtbg.mobileillustrator.model.GradientStop
import io.github.mhmmtbg.mobileillustrator.model.Layer
import io.github.mhmmtbg.mobileillustrator.model.LineCap
import io.github.mhmmtbg.mobileillustrator.model.Paint
import io.github.mhmmtbg.mobileillustrator.model.PathNode
import io.github.mhmmtbg.mobileillustrator.model.Rect
import io.github.mhmmtbg.mobileillustrator.model.Rgba
import io.github.mhmmtbg.mobileillustrator.model.Shapes
import io.github.mhmmtbg.mobileillustrator.model.Stroke
import io.github.mhmmtbg.mobileillustrator.model.SubPath
import io.github.mhmmtbg.mobileillustrator.model.Vec2

/** İlk açılışta tuvalin boş görünmemesi için küçük bir örnek. Dosya açma gelince kalkacak. */
fun sampleDocument(): Document {
    val sun = PathNode(
        name = "Güneş",
        subpaths = listOf(Shapes.ellipse(Rect(620.0, 140.0, 900.0, 420.0))),
        fill = Paint.RadialGradient(
            center = Vec2(760.0, 280.0),
            radius = 140.0,
            stops = listOf(
                GradientStop(0.0, Rgba.rgb(0xFFE08A)),
                GradientStop(1.0, Rgba.rgb(0xFF7A3C)),
            ),
        ),
    )
    val hill = PathNode(
        name = "Tepe",
        subpaths = listOf(
            SubPath(
                listOf(
                    Anchor(Vec2(0.0, 760.0), handleOut = Vec2(220.0, 560.0)),
                    Anchor(Vec2(540.0, 700.0), handleIn = Vec2(380.0, 600.0), handleOut = Vec2(700.0, 800.0)),
                    Anchor(Vec2(1080.0, 620.0), handleIn = Vec2(900.0, 560.0)),
                    Anchor(Vec2(1080.0, 1080.0)),
                    Anchor(Vec2(0.0, 1080.0)),
                ),
                closed = true,
            ),
        ),
        fill = Paint.LinearGradient(
            start = Vec2(0.0, 600.0),
            end = Vec2(0.0, 1080.0),
            stops = listOf(
                GradientStop(0.0, Rgba.rgb(0x3FA796)),
                GradientStop(1.0, Rgba.rgb(0x1F5F6B)),
            ),
        ),
    )
    val card = PathNode(
        name = "Dikdörtgen",
        subpaths = listOf(Shapes.rect(Rect(140.0, 180.0, 460.0, 480.0))),
        fill = Paint.Solid(Rgba.rgb(0x3B6FE0)),
        stroke = Stroke(Paint.Solid(Rgba.rgb(0x14213D)), width = 8.0),
    )
    val wave = PathNode(
        name = "Eğri",
        subpaths = listOf(
            SubPath(
                listOf(
                    Anchor(Vec2(160.0, 620.0), handleOut = Vec2(300.0, 480.0)),
                    Anchor(Vec2(480.0, 600.0), handleIn = Vec2(380.0, 720.0), handleOut = Vec2(580.0, 480.0)),
                    Anchor(Vec2(860.0, 540.0), handleIn = Vec2(720.0, 660.0)),
                ),
            ),
        ),
        stroke = Stroke(Paint.Solid(Rgba.rgb(0x14213D)), width = 14.0, cap = LineCap.Round),
    )
    return Document(
        name = "Örnek",
        artboards = listOf(Artboard(bounds = Rect(0.0, 0.0, 1080.0, 1080.0))),
        layers = listOf(
            Layer(name = "Arka plan", children = listOf(sun, hill)),
            Layer(name = "Katman 1", children = listOf(card, wave)),
        ),
    )
}
