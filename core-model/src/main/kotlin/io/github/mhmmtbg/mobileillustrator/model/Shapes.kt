package io.github.mhmmtbg.mobileillustrator.model

/** Temel şekilleri Bezier yoluna çeviren yardımcılar. */
object Shapes {
    /** Çeyrek daireyi tek kübik Bezier ile yaklaştıran katsayı. */
    const val KAPPA = 0.5522847498307936

    fun rect(r: Rect) = SubPath(r.corners.map { Anchor(it) }, closed = true)

    fun ellipse(r: Rect): SubPath {
        val c = r.center
        val rx = r.width / 2
        val ry = r.height / 2
        val kx = rx * KAPPA
        val ky = ry * KAPPA
        return SubPath(
            listOf(
                Anchor(Vec2(c.x, r.top), Vec2(c.x - kx, r.top), Vec2(c.x + kx, r.top)),
                Anchor(Vec2(r.right, c.y), Vec2(r.right, c.y - ky), Vec2(r.right, c.y + ky)),
                Anchor(Vec2(c.x, r.bottom), Vec2(c.x + kx, r.bottom), Vec2(c.x - kx, r.bottom)),
                Anchor(Vec2(r.left, c.y), Vec2(r.left, c.y + ky), Vec2(r.left, c.y - ky)),
            ),
            closed = true,
        )
    }

    fun line(a: Vec2, b: Vec2) = SubPath(listOf(Anchor(a), Anchor(b)), closed = false)
}
