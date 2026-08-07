package com.mapconductor.heatmap

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ズームごとの「色マップの上限に対応する強度」を決める部分。
 *
 * ヒートマップは重なりの濃さを色で表すので、何をもって最濃とするかを決めないと
 * 色が付かない。ズームが変われば同じ画面に入る点の数も変わるため、上限は
 * ズームごとに持つ（引いたときだけ真っ赤、寄ると全部薄い、を避ける）。
 *
 * 求め方は「その画面サイズで、半径 2 つ分の升目に落としたときの最大合計」。
 * 実際に畳み込んだ値ではないが、それに比例した量になり、はるかに軽い。
 *
 * ios-sdk / react-sdk の同名ファイルと同じ式。
 */
internal object HeatmapIntensity {
    /** ズーム 3 での基準画面サイズ（px）。ズーム 1 ごとに 2 倍していく。 */
    private const val SCREEN_SIZE = 1280
    private const val SCREEN_SIZE_BASE_ZOOM = 3
    private const val DEFAULT_MIN_ZOOM = 5
    private const val DEFAULT_MAX_ZOOM = 11
    const val MAX_ZOOM_LEVEL = 22

    fun getMaxIntensities(
        points: List<WeightedPoint>,
        bounds: Bounds,
        radius: Int,
        customMaxIntensity: Double?,
    ): DoubleArray {
        val maxIntensityArray = DoubleArray(MAX_ZOOM_LEVEL)
        if (customMaxIntensity != null && customMaxIntensity != 0.0) {
            maxIntensityArray.fill(customMaxIntensity)
            return maxIntensityArray
        }
        // 実際に計算するのは 5..10 だけ。外側は端の値で埋める
        // （引きすぎ・寄りすぎの領域は見た目が変わらず、計算だけ重くなるため）。
        for (i in DEFAULT_MIN_ZOOM until DEFAULT_MAX_ZOOM) {
            val screenDim = (SCREEN_SIZE * 2.0.pow(i - SCREEN_SIZE_BASE_ZOOM)).roundToInt()
            maxIntensityArray[i] = getMaxValue(points, bounds, radius, screenDim)
            if (i == DEFAULT_MIN_ZOOM) {
                for (j in 0 until i) {
                    maxIntensityArray[j] = maxIntensityArray[i]
                }
            }
        }
        for (i in DEFAULT_MAX_ZOOM until MAX_ZOOM_LEVEL) {
            maxIntensityArray[i] = maxIntensityArray[DEFAULT_MAX_ZOOM - 1]
        }
        return maxIntensityArray
    }

    fun getMaxValue(
        points: List<WeightedPoint>,
        bounds: Bounds,
        radius: Int,
        screenDim: Int,
    ): Double {
        val minX = bounds.minX
        val maxX = bounds.maxX
        val minY = bounds.minY
        val maxY = bounds.maxY
        val boundsDim = (maxX - minX).coerceAtLeast(maxY - minY)
        if (boundsDim == 0.0) {
            // 全点が同じ位置。升目に落としても意味がないので、最大の重みをそのまま使う。
            return points.maxOfOrNull { it.intensity } ?: 0.0
        }
        val nBuckets = (screenDim / (2.0 * radius) + 0.5).toInt().coerceAtLeast(1)
        val scale = nBuckets / boundsDim
        val buckets = HashMap<Int, HashMap<Int, Double>>()
        var max = 0.0
        points.forEach { point ->
            val xBucket = ((point.x - minX) * scale).toInt()
            val yBucket = ((point.y - minY) * scale).toInt()
            val column = buckets.getOrPut(xBucket) { HashMap() }
            val nextValue = (column[yBucket] ?: 0.0) + point.intensity
            column[yBucket] = nextValue
            if (nextValue > max) max = nextValue
        }
        return max
    }
}
