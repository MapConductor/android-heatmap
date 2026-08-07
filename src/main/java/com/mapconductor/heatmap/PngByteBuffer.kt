package com.mapconductor.heatmap

import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * 伸長する byte バッファ。
 *
 * `ByteArrayOutputStream` を使わないのは、[setInt32BE] で**書き終えた位置へ戻って
 * 長さを埋める**必要があるため。PNG の IDAT チャンクは長さが先頭にあるが、
 * 実際の長さは圧縮し終わるまで分からない。
 */
internal class PngByteBuffer(
    initialCapacity: Int,
) {
    private var buf: ByteArray = ByteArray(initialCapacity.coerceAtLeast(MIN_CAPACITY))
    private var count: Int = 0

    fun position(): Int = count

    fun reset() {
        count = 0
    }

    fun ensureCapacity(minCapacity: Int) {
        if (buf.size >= minCapacity) return
        var newCap = buf.size
        while (newCap < minCapacity) {
            newCap = (newCap * 2).coerceAtLeast(MIN_CAPACITY)
        }
        buf = buf.copyOf(newCap)
    }

    fun setInt32BE(
        offset: Int,
        value: Int,
    ) {
        if (offset < 0 || offset + 4 > count) {
            throw IndexOutOfBoundsException("offset=$offset count=$count")
        }
        buf[offset] = ((value ushr 24) and 0xff).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xff).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xff).toByte()
        buf[offset + 3] = (value and 0xff).toByte()
    }

    fun writeByte(value: Int) {
        ensureCapacity(count + 1)
        buf[count++] = value.toByte()
    }

    fun writeInt32BE(value: Int) {
        ensureCapacity(count + 4)
        buf[count++] = ((value ushr 24) and 0xff).toByte()
        buf[count++] = ((value ushr 16) and 0xff).toByte()
        buf[count++] = ((value ushr 8) and 0xff).toByte()
        buf[count++] = (value and 0xff).toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        writeBytes(bytes, 0, bytes.size)
    }

    fun writeBytes(
        bytes: ByteArray,
        offset: Int,
        len: Int,
    ) {
        if (len <= 0) return
        ensureCapacity(count + len)
        System.arraycopy(bytes, offset, buf, count, len)
        count += len
    }

    fun toByteArray(): ByteArray = buf.copyOf(count)

    private companion object {
        const val MIN_CAPACITY = 16
    }
}

/**
 * PNG 1 枚を書き出す間だけ使う作業領域。**スレッドごとに使い回す**。
 *
 * `Deflater` と行バッファはタイル 1 枚ごとに確保すると GC を強く叩く。
 * ヒートマップは 1 画面で数十枚のタイルを描くため、確保のコストが効いてくる。
 */
internal class PngBuffers {
    var row: ByteArray = ByteArray(0)
    var zlibBlockHeader: ByteArray = ByteArray(5)
    var adlerBuf: ByteArray = ByteArray(4)
    var deflateBuf: ByteArray = ByteArray(DEFLATE_BUFFER_BYTES)
    var deflater: Deflater = Deflater(HeatmapTileRenderer.DEFAULT_PNG_COMPRESSION_LEVEL)
    var crc32: CRC32 = CRC32()
    var adler32: Adler32 = Adler32()
    var ihdr: ByteArray = ByteArray(IHDR_BYTES)
    var out: PngByteBuffer = PngByteBuffer(OUT_INITIAL_BYTES)

    fun ensureRow(width: Int) {
        val needed = 1 + width * 4
        if (row.size != needed) {
            row = ByteArray(needed)
        }
    }

    fun ensureOutCapacity(
        width: Int,
        height: Int,
    ) {
        // Rough estimate: signature + IHDR/IEND overhead + zlib stream ~ raw bytes (level 0).
        val raw = height * (1 + width * 4)
        val estimated = 8 + 64 + raw + raw / 64
        out.ensureCapacity(estimated)
    }

    private companion object {
        const val DEFLATE_BUFFER_BYTES = 128 * 1024
        const val OUT_INITIAL_BYTES = 512 * 1024
        const val IHDR_BYTES = 13
    }
}
