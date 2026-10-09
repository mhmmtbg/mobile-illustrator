package io.github.mhmmtbg.mobileillustrator.model

/** Değişmez geri al/yinele yığını. Belge kopyaları dallarını paylaştığı için bellek maliyeti düşüktür. */
data class History<T>(
    val past: List<T> = emptyList(),
    val present: T,
    val future: List<T> = emptyList(),
    val limit: Int = 200,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** Yeni durumu kaydeder; yinele yığını boşalır. Durum değişmediyse hiçbir şey yapmaz. */
    fun push(state: T): History<T> {
        if (state == present) return this
        val newPast = (past + present).let { if (it.size > limit) it.drop(it.size - limit) else it }
        return copy(past = newPast, present = state, future = emptyList())
    }

    fun undo(): History<T> =
        if (!canUndo) this else copy(past = past.dropLast(1), present = past.last(), future = listOf(present) + future)

    fun redo(): History<T> =
        if (!canRedo) this else copy(past = past + present, present = future.first(), future = future.drop(1))
}
