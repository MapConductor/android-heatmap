package com.mapconductor.heatmap

import kotlin.math.roundToInt
import android.graphics.Color

/** 色マップ上の 1 区間。[duration] は区間が占めるインデックス数。 */
private data class ColorInterval(
    val color1: Int,
    val color2: Int,
    val duration: Float,
)

/**
 * グラデーション定義から、強度 → 色の引き当て表を作る部分。
 *
 * 描画のたびに補間すると重いので、[COLOR_MAP_SIZE] 段の表へ一度だけ展開しておき、
 * タイル描画では配列の添字を引くだけにする。
 *
 * **補間は HSV で行う。** RGB で補間すると青→赤のような組み合わせで中間が
 * 濁った灰色になり、ヒートマップとして意図した色相の変化にならない。
 *
 * ios-sdk / react-sdk の同名ファイルと同じ式。
 */
internal object HeatmapColorMap {
    const val COLOR_MAP_SIZE = 1000

    fun build(gradient: HeatmapGradient): IntArray {
        val colors = gradient.stops.map { it.color }.toIntArray()
        val startPoints = gradient.stops.map { it.position.toFloat() }.toFloatArray()
        return generate(colors, startPoints, COLOR_MAP_SIZE)
    }

    fun generate(
        colors: IntArray,
        startPoints: FloatArray,
        mapSize: Int,
    ): IntArray {
        require(colors.isNotEmpty()) { "Heatmap gradient requires at least one color." }
        val colorIntervals = HashMap<Int, ColorInterval>()
        // 最初の停止点が 0 でないときは、透明から最初の色へ立ち上げる区間を足す。
        if (startPoints[0] != 0f) {
            val initialColor =
                Color.argb(
                    0,
                    Color.red(colors[0]),
                    Color.green(colors[0]),
                    Color.blue(colors[0]),
                )
            colorIntervals[0] =
                ColorInterval(
                    color1 = initialColor,
                    color2 = colors[0],
                    duration = mapSize * startPoints[0],
                )
        }
        for (i in 1 until colors.size) {
            colorIntervals[(mapSize * startPoints[i - 1]).toInt()] =
                ColorInterval(
                    color1 = colors[i - 1],
                    color2 = colors[i],
                    duration = mapSize * (startPoints[i] - startPoints[i - 1]),
                )
        }
        // 最後の停止点が 1 でないときは、最後の色のまま最後まで伸ばす。
        if (startPoints[startPoints.size - 1] != 1f) {
            val last = startPoints.size - 1
            colorIntervals[(mapSize * startPoints[last]).toInt()] =
                ColorInterval(
                    color1 = colors[last],
                    color2 = colors[last],
                    duration = mapSize * (1 - startPoints[last]),
                )
        }

        val colorMap = IntArray(mapSize)
        var interval = colorIntervals[0] ?: ColorInterval(colors[0], colors[0], 1f)
        var start = 0
        for (i in 0 until mapSize) {
            colorIntervals[i]?.let {
                interval = it
                start = i
            }
            val ratio =
                if (interval.duration == 0f) {
                    0f
                } else {
                    (i - start) / interval.duration
                }
            colorMap[i] = interpolateColor(interval.color1, interval.color2, ratio)
        }
        return colorMap
    }

    /**
     * HSV 空間で 2 色を補間する。
     *
     * 色相は 360 度で循環するので、差が 180 度を超える場合は近い方を回る
     * （そうしないと赤→マゼンタが緑側を大回りしてしまう）。
     */
    fun interpolateColor(
        color1: Int,
        color2: Int,
        ratio: Float,
    ): Int {
        val alpha = ((Color.alpha(color2) - Color.alpha(color1)) * ratio + Color.alpha(color1)).roundToInt()
        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        Color.RGBToHSV(Color.red(color1), Color.green(color1), Color.blue(color1), hsv1)
        Color.RGBToHSV(Color.red(color2), Color.green(color2), Color.blue(color2), hsv2)

        if (hsv1[0] - hsv2[0] > 180) {
            hsv2[0] += 360
        } else if (hsv2[0] - hsv1[0] > 180) {
            hsv1[0] += 360
        }

        val result = FloatArray(3)
        for (i in 0..2) {
            result[i] = (hsv2[i] - hsv1[i]) * ratio + hsv1[i]
        }
        return Color.HSVToColor(alpha, result)
    }
}
