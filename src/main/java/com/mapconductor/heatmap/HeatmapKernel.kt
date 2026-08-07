package com.mapconductor.heatmap

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp

/**
 * ガウシアンカーネルの生成と、それを使った畳み込み。
 *
 * **2 次元の畳み込みを縦横 2 回の 1 次元に分けている。** ガウシアンは可分なので
 * 結果は同じで、計算量が O(r^2) から O(r) に落ちる。半径 20px なら 40 倍以上違う。
 *
 * さらに**値が入っているセルだけを走査する**（nonZero の添字配列）。ヒートマップの
 * 格子はほとんどが 0 なので、全セルを回すと点の数に関係なく格子サイズぶんの
 * 時間がかかってしまう。
 *
 * カーネルは半径ごとにキャッシュする。半径はズームで決まり、同じズームの
 * タイルが大量に来るため、毎回作ると無駄になる。
 *
 * ios-sdk / react-sdk の同名ファイルと同じ式。
 */
internal class HeatmapKernel {
    private val cache = ConcurrentHashMap<Int, FloatArray>()

    fun resolveKernel(radius: Int): FloatArray {
        if (radius <= 0) return floatArrayOf(1.0f)
        val cached = cache[radius]
        if (cached != null) return cached
        val built = generateKernel(radius, radius / KERNEL_SD_DIVISOR)
        cache[radius] = built
        return built
    }

    fun generateKernel(
        radius: Int,
        sd: Double,
    ): FloatArray {
        val kernel = FloatArray(radius * 2 + 1)
        for (i in -radius..radius) {
            kernel[i + radius] = exp(-i * i / (2 * sd * sd)).toFloat()
        }
        return kernel
    }

    /**
     * [input]（格子）に横方向、続いて縦方向のカーネルをかけ、[output]（タイル）へ出す。
     *
     * [nonZeroInput] / [nonZeroIntermediate] は「値が入っている添字」の作業配列で、
     * 呼び出し側が使い回す。中間結果の非ゼロ数は [nonZeroIntermediateCountOut] で返す。
     */
    fun convolveSparseToOutput(
        input: FloatArray,
        intermediate: FloatArray,
        output: FloatArray,
        kernel: FloatArray,
        gridDim: Int,
        radius: Int,
        tileSize: Int,
        nonZeroInput: IntArray,
        nonZeroInputCount: Int,
        nonZeroIntermediate: IntArray,
        nonZeroIntermediateCountOut: (Int) -> Unit,
    ) {
        val lowerLimit = radius
        val upperLimit = radius + tileSize - 1

        // Horizontal spread into `intermediate` (row-major).
        var nonZeroIntermediateCount = 0
        var i = 0
        while (i < nonZeroInputCount) {
            val idx = nonZeroInput[i]
            val y = idx / gridDim
            val x = idx - y * gridDim
            val value = input[idx]
            val rowBase = y * gridDim
            val xStart = lowerLimit.coerceAtLeast(x - radius)
            val xEndExclusive = (upperLimit.coerceAtMost(x + radius)) + 1
            var x2 = xStart
            while (x2 < xEndExclusive) {
                val j = rowBase + x2
                val prev = intermediate[j]
                if (prev == 0.0f) {
                    nonZeroIntermediate[nonZeroIntermediateCount++] = j
                }
                intermediate[j] = prev + value * kernel[x2 - x + radius]
                x2 += 1
            }
            i += 1
        }
        nonZeroIntermediateCountOut(nonZeroIntermediateCount)

        // Vertical spread into `output` (tileSize x tileSize, row-major).
        i = 0
        while (i < nonZeroIntermediateCount) {
            val idx = nonZeroIntermediate[i]
            val y = idx / gridDim
            val x = idx - y * gridDim
            val value = intermediate[idx]
            val yStart = lowerLimit.coerceAtLeast(y - radius)
            val yEndExclusive = (upperLimit.coerceAtMost(y + radius)) + 1
            val xOut = x - radius
            var y2 = yStart
            while (y2 < yEndExclusive) {
                output[(y2 - radius) * tileSize + xOut] += value * kernel[y2 - y + radius]
                y2 += 1
            }
            i += 1
        }
    }

    companion object {
        /** 半径を標準偏差へ落とす除数。3σ でほぼ 0 になるので半径の 1/3。 */
        private const val KERNEL_SD_DIVISOR = 3.0
    }
}
