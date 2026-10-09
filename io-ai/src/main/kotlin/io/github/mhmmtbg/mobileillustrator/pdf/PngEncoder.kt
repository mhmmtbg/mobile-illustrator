package io.github.mhmmtbg.mobileillustrator.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** 8 bit RGBA PNG yazar. Satırlar [row] ile istendikçe üretilir; dev görsellerde bellek şişmez. */
object PngEncoder {
    fun encode(width: Int, height: Int, row: (y: Int, out: ByteArray) -> Unit): ByteArray {
        val out = ByteArrayOutputStream(width * height / 2 + 1024)
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val ihdr = ByteArray(13)
        putInt(ihdr, 0, width)
        putInt(ihdr, 4, height)
        ihdr[8] = 8
        ihdr[9] = 6
        chunk(out, "IHDR", ihdr, ihdr.size)

        val deflater = Deflater(6)
        val line = ByteArray(width * 4)
        val scan = ByteArray(1 + width * 4)
        val buf = ByteArray(64 * 1024)
        val idat = ByteArrayOutputStream(64 * 1024)
        fun drain(finish: Boolean) {
            if (finish) {
                while (!deflater.finished()) {
                    val n = deflater.deflate(buf)
                    if (n > 0) idat.write(buf, 0, n)
                }
            } else {
                while (!deflater.needsInput()) {
                    val n = deflater.deflate(buf)
                    if (n > 0) idat.write(buf, 0, n)
                }
            }
            if (idat.size() >= 1 shl 20 || finish) {
                if (idat.size() > 0) chunk(out, "IDAT", idat.toByteArray(), idat.size())
                idat.reset()
            }
        }
        for (y in 0 until height) {
            row(y, line)
            scan[0] = 0
            System.arraycopy(line, 0, scan, 1, line.size)
            deflater.setInput(scan)
            drain(false)
        }
        deflater.finish()
        drain(true)
        deflater.end()
        chunk(out, "IEND", ByteArray(0), 0)
        return out.toByteArray()
    }

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        b[at] = (v ushr 24).toByte(); b[at + 1] = (v ushr 16).toByte(); b[at + 2] = (v ushr 8).toByte(); b[at + 3] = v.toByte()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray, len: Int) {
        val head = ByteArray(4)
        putInt(head, 0, len)
        out.write(head)
        val t = type.toByteArray(Charsets.ISO_8859_1)
        out.write(t)
        out.write(data, 0, len)
        val crc = CRC32()
        crc.update(t)
        crc.update(data, 0, len)
        putInt(head, 0, crc.value.toInt())
        out.write(head)
    }
}
