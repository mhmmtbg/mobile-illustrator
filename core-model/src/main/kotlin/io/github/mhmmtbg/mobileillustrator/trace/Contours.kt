package io.github.mhmmtbg.mobileillustrator.trace

/** İkili maskenin sınırlarını piksel kenarları boyunca izler. */
internal object Contours {
    private const val EAST = 0
    private const val SOUTH = 1
    private const val WEST = 2
    private const val NORTH = 3

    /**
     * @return maskenin bağlı her parçası için kapalı döngüler (dış sınır ve delikler). Her döngü, köşe noktalarının
     * (x0, y0, x1, y1, …) tamsayı ızgara koordinatlarıdır; kenarlar yatay ya da dikeydir.
     */
    fun trace(mask: BooleanArray, width: Int, height: Int): List<List<IntArray>> {
        val n = width * height
        // Bağlı parçalar (4 komşuluk): döngüler ait oldukları parçaya göre gruplanır.
        val comp = IntArray(n) { -1 }
        var compCount = 0
        val stack = IntArray(n)
        for (start in 0 until n) {
            if (!mask[start] || comp[start] >= 0) continue
            var top = 0
            stack[top++] = start
            comp[start] = compCount
            while (top > 0) {
                val p = stack[--top]
                val x = p % width
                if (x > 0 && mask[p - 1] && comp[p - 1] < 0) { comp[p - 1] = compCount; stack[top++] = p - 1 }
                if (x < width - 1 && mask[p + 1] && comp[p + 1] < 0) { comp[p + 1] = compCount; stack[top++] = p + 1 }
                if (p >= width && mask[p - width] && comp[p - width] < 0) { comp[p - width] = compCount; stack[top++] = p - width }
                if (p < n - width && mask[p + width] && comp[p + width] < 0) { comp[p + width] = compCount; stack[top++] = p + width }
            }
            compCount++
        }
        if (compCount == 0) return emptyList()

        // Yönlü sınır kenarları: ilerlerken dolu taraf sağda kalır.
        // h[y * width + x]: (x,y)-(x+1,y) kenarı; +1 doğuya, -1 batıya. v[y * (width+1) + x]: (x,y)-(x,y+1); +1 güneye, -1 kuzeye.
        val h = ByteArray((height + 1) * width)
        val v = ByteArray(height * (width + 1))
        for (y in 0..height) {
            for (x in 0 until width) {
                val below = y < height && mask[y * width + x]
                val above = y > 0 && mask[(y - 1) * width + x]
                if (below && !above) h[y * width + x] = 1 else if (above && !below) h[y * width + x] = -1
            }
        }
        for (y in 0 until height) {
            for (x in 0..width) {
                val right = x < width && mask[y * width + x]
                val left = x > 0 && mask[y * width + x - 1]
                if (right && !left) v[y * (width + 1) + x] = -1 else if (left && !right) v[y * (width + 1) + x] = 1
            }
        }

        fun take(x: Int, y: Int, dir: Int): Boolean {
            when (dir) {
                EAST -> if (x < width && h[y * width + x].toInt() == 1) { h[y * width + x] = 0; return true }
                WEST -> if (x > 0 && h[y * width + x - 1].toInt() == -1) { h[y * width + x - 1] = 0; return true }
                SOUTH -> if (y < height && v[y * (width + 1) + x].toInt() == 1) { v[y * (width + 1) + x] = 0; return true }
                NORTH -> if (y > 0 && v[(y - 1) * (width + 1) + x].toInt() == -1) { v[(y - 1) * (width + 1) + x] = 0; return true }
            }
            return false
        }

        val result = Array(compCount) { ArrayList<IntArray>(1) }
        var points = IntArray(64)
        // Her döngüde doğuya giden en az bir kenar vardır (dolu bir pikselin üst kenarı); oradan başlanır.
        for (sy in 0 until height) {
            for (sx in 0 until width) {
                if (h[sy * width + sx].toInt() != 1) continue
                val owner = comp[sy * width + sx]
                var size = 0
                var x = sx
                var y = sy
                var dir = EAST
                // Başlangıç kenarı döngü kapanırken tüketilir: başlangıç noktasından başka bir yöne geçilen
                // (çapraz değen piksellerdeki) durumla döngünün kapanışı böyle ayırt edilir.
                points[size++] = x
                points[size++] = y
                x++
                while (true) {
                    // Önce sağa dönülür: çaprazdan değen pikseller ayrı parçalar olarak kalır.
                    val right = (dir + 1) and 3
                    val left = (dir + 3) and 3
                    val next = when {
                        take(x, y, right) -> right
                        take(x, y, dir) -> dir
                        take(x, y, left) -> left
                        else -> -1
                    }
                    if (next < 0) break // olmamalı; bozuk döngü yarıda bırakılır
                    if (x == sx && y == sy && next == EAST) break
                    if (next != dir) {
                        if (size + 2 > points.size) points = points.copyOf(points.size * 2)
                        points[size++] = x
                        points[size++] = y
                        dir = next
                    }
                    when (dir) {
                        EAST -> x++
                        SOUTH -> y++
                        WEST -> x--
                        NORTH -> y--
                    }
                }
                // Başlangıç noktası düz bir kenarın ortasındaysa köşe değildir.
                var loop = points.copyOf(size)
                if (dir == EAST && size >= 6) loop = loop.copyOfRange(2, size)
                if (loop.size >= 8) result[owner].add(loop)
            }
        }
        return result.filter { it.isNotEmpty() }
    }
}
