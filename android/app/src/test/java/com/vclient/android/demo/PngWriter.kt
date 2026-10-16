package com.vclient.android.demo

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** Writes an 8-bit RGB PNG (nearest-neighbour upscaled), with no image library needed. */
object PngWriter {
    fun save(name: String, pixels: IntArray, w: Int, h: Int, scale: Int = 3) {
        val ow = w * scale
        val oh = h * scale
        val raw = ByteArrayOutputStream()
        for (y in 0 until oh) {
            raw.write(0) // filter: none
            for (x in 0 until ow) {
                val c = pixels[(y / scale) * w + x / scale]
                raw.write((c shr 16) and 0xff); raw.write((c shr 8) and 0xff); raw.write(c and 0xff)
            }
        }
        fun chunk(out: DataOutputStream, type: String, data: ByteArray) {
            out.writeInt(data.size)
            val body = type.toByteArray() + data
            out.write(body)
            out.writeInt(CRC32().also { it.update(body) }.value.toInt())
        }
        val file = File("build/demo-previews/$name.png").also { it.parentFile.mkdirs() }
        DataOutputStream(file.outputStream()).use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            chunk(out, "IHDR", ByteArrayOutputStream().also { b -> DataOutputStream(b).apply { writeInt(ow); writeInt(oh); write(byteArrayOf(8, 2, 0, 0, 0)) } }.toByteArray())
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }
            chunk(out, "IDAT", compressed.toByteArray())
            chunk(out, "IEND", ByteArray(0))
        }
    }
}
