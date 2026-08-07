package com.mapconductor.heatmap

import com.mapconductor.core.features.GeoPointInterface
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

/** 世界座標系の 1 点（0..1 の正規化 Web メルカトル）。 */
internal data class WorldPoint(
    val x: Double,
    val y: Double,
)

/** 世界座標に落とし、重みを確定させたヒートマップの点。 */
internal data class WeightedPoint(
    val x: Double,
    val y: Double,
    val intensity: Double,
)

internal data class Bounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
) {
    fun intersects(other: Bounds): Boolean =
        minX <= other.maxX &&
            maxX >= other.minX &&
            minY <= other.maxY &&
            maxY >= other.minY
}

/**
 * 日付変更線をまたぐタイルを、世界座標の連続した区間へ分けたもの。
 * [offset] は点の x に足して、この区間の座標系へ移すための量。
 */
internal data class XRange(
    val min: Double,
    val max: Double,
    val offset: Double,
)

/**
 * 点の格子インデックス。連結リスト方式で、[heads] がセルごとの先頭、
 * [next] が同じセル内の次の点を指す（配列 2 本だけで済むので確保が軽い）。
 */
internal data class PointIndex(
    val gridSize: Int,
    val heads: IntArray,
    val next: IntArray,
    val nonEmptyBuckets: Int,
    val maxBucketSize: Int,
)

/**
 * 緯度経度を世界座標へ移し、範囲と空間インデックスを組み立てる部分。
 *
 * すべて副作用のない計算で、タイルの中身にもキャッシュにも触らない。
 *
 * ios-sdk / react-sdk の同名ファイルと同じ式。片方だけ直すと 3 者の
 * ヒートマップの見え方がずれるので、変えるときは 3 つとも直すこと。
 */
internal object HeatmapWorld {
    const val WORLD_WIDTH = 1.0
    private const val DEFAULT_INTENSITY = 1.0
    private const val DEFAULT_INDEX_GRID_SIZE = 128
    private const val MAX_ABS_SIN_LAT = 0.9999

    fun buildWeightedPoints(points: List<HeatmapPoint>): List<WeightedPoint> {
        if (points.isEmpty()) return emptyList()
        val weightedPoints = ArrayList<WeightedPoint>(points.size)
        points.forEach { point ->
            // NaN と負の重みは既定値に倒す（負は面積が減る方向に効いてしまうため）。
            val weight =
                if (point.weight.isNaN()) {
                    DEFAULT_INTENSITY
                } else if (point.weight >= 0.0) {
                    point.weight
                } else {
                    DEFAULT_INTENSITY
                }
            val world = toWorldPoint(point.position)
            weightedPoints.add(WeightedPoint(world.x, world.y, weight))
        }
        return weightedPoints
    }

    fun toWorldPoint(position: GeoPointInterface): WorldPoint {
        val x = position.longitude / 360.0 + 0.5
        val siny = sin(Math.toRadians(position.latitude)).coerceIn(-MAX_ABS_SIN_LAT, MAX_ABS_SIN_LAT)
        val y = 0.5 * ln((1 + siny) / (1 - siny)) / -(2 * PI) + 0.5
        return WorldPoint(x, y)
    }

    fun calculateBounds(points: List<WeightedPoint>): Bounds {
        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        points.forEach { point ->
            if (point.x < minX) minX = point.x
            if (point.x > maxX) maxX = point.x
            if (point.y < minY) minY = point.y
            if (point.y > maxY) maxY = point.y
        }
        return Bounds(minX, maxX, minY, maxY)
    }

    /**
     * タイルの x 範囲を、世界の端をまたがない区間へ割る。
     *
     * 端をまたぐタイルをそのまま扱うと、反対側にある点を拾えない。
     * 区間ごとに [XRange.offset] を足してから比較することで、
     * 「世界を 1 周ぶんずらした点」も同じ判定で拾える。
     */
    fun buildTileXRanges(
        minX: Double,
        maxX: Double,
    ): List<XRange> {
        if (minX <= 0.0 && maxX >= WORLD_WIDTH) {
            return listOf(XRange(min = 0.0, max = WORLD_WIDTH, offset = 0.0))
        }
        if (minX < 0.0) {
            return listOf(
                XRange(min = 0.0, max = maxX, offset = 0.0),
                XRange(min = minX + WORLD_WIDTH, max = WORLD_WIDTH, offset = -WORLD_WIDTH),
            )
        }
        if (maxX > WORLD_WIDTH) {
            return listOf(
                XRange(min = minX, max = WORLD_WIDTH, offset = 0.0),
                XRange(min = 0.0, max = maxX - WORLD_WIDTH, offset = WORLD_WIDTH),
            )
        }
        return listOf(XRange(min = minX, max = maxX, offset = 0.0))
    }

    fun buildPointIndex(points: List<WeightedPoint>): PointIndex {
        val gridSize = DEFAULT_INDEX_GRID_SIZE
        val heads = IntArray(gridSize * gridSize) { -1 }
        val next = IntArray(points.size) { -1 }
        val counts = IntArray(gridSize * gridSize)
        var nonEmptyBuckets = 0
        var maxBucketSize = 0
        for (i in points.indices) {
            val p = points[i]
            val cx = (p.x * gridSize).toInt().coerceIn(0, gridSize - 1)
            val cy = (p.y * gridSize).toInt().coerceIn(0, gridSize - 1)
            val idx = cy * gridSize + cx
            next[i] = heads[idx]
            heads[idx] = i
            val c = counts[idx] + 1
            if (c == 1) nonEmptyBuckets += 1
            counts[idx] = c
            if (c > maxBucketSize) maxBucketSize = c
        }
        return PointIndex(
            gridSize = gridSize,
            heads = heads,
            next = next,
            nonEmptyBuckets = nonEmptyBuckets,
            maxBucketSize = maxBucketSize,
        )
    }
}
