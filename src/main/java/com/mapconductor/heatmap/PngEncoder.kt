package com.mapconductor.heatmap

import java.util.Arrays
import java.util.zip.CRC32

/**
 * 強度の配列を RGBA の PNG へ書き出す部分。
 *
 * **Bitmap.compress を使わない。** ヒートマップは 1 画面で数十枚のタイルを描くため、
 * Bitmap を作って圧縮する経路だと確保と GC が支配的になる。ここでは行バッファ 1 本を
 * 使い回し、zlib ストリームを IDAT チャンクへ直接流し込む。
 *
 * 圧縮レベル 0 のときは `Deflater` を通さず、**格納（無圧縮）ブロック**を自前で書く。
 * レベル 0 でも `Deflater` は入出力のコピーを挟むので、それを省くため。
 *
 * react-sdk の `PngEncoder.ts` と同じ構成（あちらは CRC32 / Adler32 も自前）。
 * ios-sdk は CoreGraphics で画像を作るのでこの相当物を持たない。
 */
internal object PngEncoder {
    private val PNG_SIGNATURE =
        byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    private val PNG_IHDR = byteArrayOf(0x49, 0x48, 0x44, 0x52) // IHDR
    private val PNG_IDAT = byteArrayOf(0x49, 0x44, 0x41, 0x54) // IDAT
    private val PNG_IEND = byteArrayOf(0x49, 0x45, 0x4E, 0x44) // IEND
    private val ZLIB_HEADER_NO_COMPRESSION = byteArrayOf(0x78.toByte(), 0x01)
    private val ZLIB_FINAL_EMPTY_BLOCK = byteArrayOf(0x01, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte())
    private val EMPTY_BYTES = ByteArray(0)

    /** 格納ブロック 1 つで扱える最大長。DEFLATE の仕様上の上限。 */
    private const val STORED_BLOCK_MAX_LEN = 65535

    /**
     * 強度配列を色マップで引きながら PNG へ書き出す。
     *
     * @param maxIntensity 色マップの最後尾に対応する強度
     */
    fun encodeFromIntensity(
        intensity: FloatArray,
        colorMap: IntArray,
        maxIntensity: Double,
        width: Int,
        height: Int,
        buffers: PngBuffers,
        compressionLevel: Int,
    ): ByteArray {
        buffers.ensureRow(width)
        buffers.ensureOutCapacity(width, height)
        buffers.out.reset()

        val out = buffers.out
        writeSignature(out)
        writeIhdr(buffers.ihdr, width, height)
        val crc32 = buffers.crc32
        writeChunk(out, PNG_IHDR, buffers.ihdr, 0, buffers.ihdr.size, crc32)

        // Stream zlib output directly into a single IDAT chunk.
        val idatLenPos = out.position()
        out.writeInt32BE(0) // placeholder length
        out.writeBytes(PNG_IDAT)
        crc32.reset()
        crc32.update(PNG_IDAT)
        val idatDataStart = out.position()

        val row = buffers.row
        val lastIndex = colorMap.size - 1
        val maxColor = colorMap[lastIndex]
        val scaling = (lastIndex.toFloat() / maxIntensity.toFloat())

        var srcIndex = 0
        if (compressionLevel == 0) {
            // Bypass Deflater; write a zlib stream with stored (uncompressed) DEFLATE blocks.
            val adler32 = buffers.adler32
            adler32.reset()
            writeIdatData(out, crc32, ZLIB_HEADER_NO_COMPRESSION, 0, ZLIB_HEADER_NO_COMPRESSION.size)
            for (y in 0 until height) {
                val rowLen = fillRowRgba(row, intensity, colorMap, maxColor, scaling, srcIndex, width)
                srcIndex += width
                adler32.update(row, 0, rowLen)
                writeZlibStoredBlock(out, crc32, buffers.zlibBlockHeader, row, 0, rowLen)
            }
            // Final empty block with BFINAL=1 and Adler32 checksum.
            writeIdatData(out, crc32, ZLIB_FINAL_EMPTY_BLOCK, 0, ZLIB_FINAL_EMPTY_BLOCK.size)
            writeInt32BE(buffers.adlerBuf, 0, adler32.value.toInt())
            writeIdatData(out, crc32, buffers.adlerBuf, 0, 4)
        } else {
            val deflater = buffers.deflater
            deflater.reset()
            deflater.setLevel(compressionLevel.coerceIn(0, 9))
            for (y in 0 until height) {
                val rowLen = fillRowRgba(row, intensity, colorMap, maxColor, scaling, srcIndex, width)
                srcIndex += width
                deflater.setInput(row, 0, rowLen)
                while (!deflater.needsInput()) {
                    val n = deflater.deflate(buffers.deflateBuf)
                    if (n > 0) {
                        writeIdatData(out, crc32, buffers.deflateBuf, 0, n)
                    }
                }
            }
            deflater.finish()
            while (!deflater.finished()) {
                val n = deflater.deflate(buffers.deflateBuf)
                if (n > 0) {
                    writeIdatData(out, crc32, buffers.deflateBuf, 0, n)
                }
            }
        }

        finishIdat(out, crc32, idatLenPos, idatDataStart)
        writeChunk(out, PNG_IEND, EMPTY_BYTES, 0, 0, crc32)
        return out.toByteArray()
    }

    /** ARGB の配列をそのまま PNG へ書き出す。透明タイルの生成に使う。 */
    fun encodeRgba(
        colors: IntArray,
        width: Int,
        height: Int,
        buffers: PngBuffers,
        compressionLevel: Int,
    ): ByteArray {
        buffers.ensureRow(width)
        buffers.ensureOutCapacity(width, height)
        buffers.out.reset()

        val deflater = buffers.deflater
        deflater.reset()
        deflater.setLevel(compressionLevel.coerceIn(0, 9))

        val out = buffers.out
        writeSignature(out)
        writeIhdr(buffers.ihdr, width, height)
        val crc32 = buffers.crc32
        writeChunk(out, PNG_IHDR, buffers.ihdr, 0, buffers.ihdr.size, crc32)

        val idatLenPos = out.position()
        out.writeInt32BE(0) // placeholder length
        out.writeBytes(PNG_IDAT)
        crc32.reset()
        crc32.update(PNG_IDAT)
        val idatDataStart = out.position()

        var srcIndex = 0
        for (y in 0 until height) {
            val row = buffers.row
            row[0] = 0 // filter type 0 (None)
            var p = 1
            val rowEnd = srcIndex + width
            while (srcIndex < rowEnd) {
                val c = colors[srcIndex]
                row[p++] = ((c ushr 16) and 0xff).toByte() // r
                row[p++] = ((c ushr 8) and 0xff).toByte() // g
                row[p++] = (c and 0xff).toByte() // b
                row[p++] = ((c ushr 24) and 0xff).toByte() // a
                srcIndex += 1
            }
            deflater.setInput(row, 0, p)
            while (!deflater.needsInput()) {
                val n = deflater.deflate(buffers.deflateBuf)
                if (n > 0) {
                    writeIdatData(out, crc32, buffers.deflateBuf, 0, n)
                }
            }
        }
        deflater.finish()
        while (!deflater.finished()) {
            val n = deflater.deflate(buffers.deflateBuf)
            if (n > 0) {
                writeIdatData(out, crc32, buffers.deflateBuf, 0, n)
            }
        }

        finishIdat(out, crc32, idatLenPos, idatDataStart)
        writeChunk(out, PNG_IEND, EMPTY_BYTES, 0, 0, crc32)
        return out.toByteArray()
    }

    /** 保留にしていた IDAT の長さを埋め、CRC を書く。 */
    private fun finishIdat(
        out: PngByteBuffer,
        crc32: CRC32,
        idatLenPos: Int,
        idatDataStart: Int,
    ) {
        val idatLen = out.position() - idatDataStart
        out.setInt32BE(idatLenPos, idatLen)
        out.writeInt32BE(crc32.value.toInt())
    }

    /**
     * 1 行ぶんを RGBA で埋める。強度 0 の連続は**まとめてゼロ埋め**する。
     * ヒートマップのタイルはほとんどが透明なので、この分岐が効く。
     *
     * @return 書き込んだ長さ（フィルタ種別の 1 バイトを含む）
     */
    private fun fillRowRgba(
        row: ByteArray,
        intensity: FloatArray,
        colorMap: IntArray,
        maxColor: Int,
        scaling: Float,
        srcIndexStart: Int,
        width: Int,
    ): Int {
        row[0] = 0 // filter type 0 (None)
        var p = 1
        val rowEnd = srcIndexStart + width
        var srcIndex = srcIndexStart
        val lastIndex = colorMap.size - 1
        while (srcIndex < rowEnd) {
            val value = intensity[srcIndex]
            if (value == 0.0f) {
                var run = 1
                while (srcIndex + run < rowEnd && intensity[srcIndex + run] == 0.0f) {
                    run += 1
                }
                val end = p + run * 4
                Arrays.fill(row, p, end, 0)
                p = end
                srcIndex += run
                continue
            }
            val colorIndex = (value * scaling).toInt()
            val c = if (colorIndex <= lastIndex) colorMap[colorIndex] else maxColor
            row[p++] = ((c ushr 16) and 0xff).toByte() // r
            row[p++] = ((c ushr 8) and 0xff).toByte() // g
            row[p++] = (c and 0xff).toByte() // b
            row[p++] = ((c ushr 24) and 0xff).toByte() // a
            srcIndex += 1
        }
        return p
    }

    private fun writeIdatData(
        out: PngByteBuffer,
        crc32: CRC32,
        data: ByteArray,
        offset: Int,
        len: Int,
    ) {
        if (len <= 0) return
        out.writeBytes(data, offset, len)
        crc32.update(data, offset, len)
    }

    private fun writeZlibStoredBlock(
        out: PngByteBuffer,
        crc32: CRC32,
        header: ByteArray,
        data: ByteArray,
        offset: Int,
        len: Int,
    ) {
        // One stored (uncompressed) DEFLATE block. This is valid as long as len <= 65535.
        val safeLen = len.coerceIn(0, STORED_BLOCK_MAX_LEN)
        header[0] = 0x00 // BFINAL=0, BTYPE=00
        header[1] = (safeLen and 0xff).toByte()
        header[2] = ((safeLen ushr 8) and 0xff).toByte()
        val nlen = safeLen.inv() and 0xFFFF
        header[3] = (nlen and 0xff).toByte()
        header[4] = ((nlen ushr 8) and 0xff).toByte()
        writeIdatData(out, crc32, header, 0, 5)
        writeIdatData(out, crc32, data, offset, safeLen)
    }

    private fun writeSignature(out: PngByteBuffer) {
        out.writeBytes(PNG_SIGNATURE)
    }

    private fun writeIhdr(
        out: ByteArray,
        width: Int,
        height: Int,
    ) {
        writeInt32BE(out, 0, width)
        writeInt32BE(out, 4, height)
        out[8] = 8 // bit depth
        out[9] = 6 // color type: RGBA
        out[10] = 0 // compression method
        out[11] = 0 // filter method
        out[12] = 0 // interlace method
    }

    private fun writeChunk(
        out: PngByteBuffer,
        type: ByteArray,
        data: ByteArray,
        offset: Int,
        len: Int,
        crc32: CRC32,
    ) {
        out.writeInt32BE(len)
        out.writeBytes(type)
        if (len > 0) {
            out.writeBytes(data, offset, len)
        }
        crc32.reset()
        crc32.update(type)
        if (len > 0) {
            crc32.update(data, offset, len)
        }
        out.writeInt32BE(crc32.value.toInt())
    }

    private fun writeInt32BE(
        buf: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buf[offset] = ((value ushr 24) and 0xff).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xff).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xff).toByte()
        buf[offset + 3] = (value and 0xff).toByte()
    }
}
