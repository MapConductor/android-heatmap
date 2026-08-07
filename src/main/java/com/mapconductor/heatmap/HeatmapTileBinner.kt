package com.mapconductor.heatmap

/** タイル 1 枚ぶんの、世界座標での位置と格子の刻み。 */
internal data class TileGeometry(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val bucketWidth: Double,
    val gridDim: Int,
)

/**
 * タイルにかかる点を集め、格子のセルへ足し込む部分。
 *
 * ここが効率の要。全点を毎回走査すると点数に比例して重くなるので、点が多いときは
 * [PointIndex] の格子から**このタイルにかかるセルだけ**を辿る。点が少ないときは
 * インデックスを引く方が高くつくので、素直に全走査する（切り替えは呼び出し側）。
 *
 * 日付変更線をまたぐタイルは [HeatmapWorld.buildTileXRanges] で区間に割り、
 * 区間ごとのオフセットを足してから判定する。
 *
 * ios-sdk / react-sdk の同名ファイルと同じ選び方。
 */
internal object HeatmapTileBinner {
    /**
     * @return 1 点でも格子に入ったとき true。false なら描くものが無い。
     */
    fun bin(
        points: List<WeightedPoint>,
        index: PointIndex?,
        geometry: TileGeometry,
        buffers: HeatmapRenderBuffers,
        timings: HeatmapPhaseTimings?,
    ): Boolean {
        var hasPoints = false
        val trackStats = timings != null
        var candidatesVisited = 0
        var cellsVisited = 0
        var pointsBinned = 0

        fun addPoint(
            adjustedWorldX: Double,
            worldY: Double,
            weight: Double,
        ) {
            val bucketX = ((adjustedWorldX - geometry.minX) / geometry.bucketWidth).toInt()
            val bucketY = ((worldY - geometry.minY) / geometry.bucketWidth).toInt()
            if (bucketX !in 0 until geometry.gridDim || bucketY !in 0 until geometry.gridDim) return
            val idx = bucketY * geometry.gridDim + bucketX
            val prev = buffers.intensity[idx]
            if (prev == 0.0f) {
                buffers.nonZeroIntensity[buffers.nonZeroIntensityCount++] = idx
            }
            buffers.intensity[idx] = prev + weight.toFloat()
            hasPoints = true
            if (trackStats) {
                pointsBinned += 1
            }
        }

        if (index == null) {
            timings?.let {
                it.usedIndex = false
                it.indexGridSize = 0
                it.indexNonEmptyBuckets = 0
                it.indexMaxBucketSize = 0
                it.xRanges = 0
            }
            points.forEach { point ->
                if (trackStats) {
                    candidatesVisited += 1
                }
                if (point.y < geometry.minY || point.y > geometry.maxY) return@forEach
                if (point.x >= geometry.minX && point.x <= geometry.maxX) {
                    addPoint(point.x, point.y, point.intensity)
                } else if (geometry.minX < 0.0 && point.x >= geometry.minX + HeatmapWorld.WORLD_WIDTH) {
                    addPoint(point.x - HeatmapWorld.WORLD_WIDTH, point.y, point.intensity)
                } else if (geometry.maxX > HeatmapWorld.WORLD_WIDTH &&
                    point.x <= geometry.maxX - HeatmapWorld.WORLD_WIDTH
                ) {
                    addPoint(point.x + HeatmapWorld.WORLD_WIDTH, point.y, point.intensity)
                }
            }
        } else {
            timings?.let {
                it.usedIndex = true
                it.indexGridSize = index.gridSize
                it.indexNonEmptyBuckets = index.nonEmptyBuckets
                it.indexMaxBucketSize = index.maxBucketSize
            }
            val gridSize = index.gridSize
            val heads = index.heads
            val next = index.next
            val yMin = geometry.minY.coerceAtLeast(0.0)
            val yMax = geometry.maxY.coerceAtMost(HeatmapWorld.WORLD_WIDTH)
            if (yMin <= yMax) {
                val cyStart = (yMin * gridSize).toInt().coerceIn(0, gridSize - 1)
                val cyEnd = ((yMax * gridSize).toInt()).coerceIn(0, gridSize - 1)

                val xRanges = HeatmapWorld.buildTileXRanges(geometry.minX, geometry.maxX)
                timings?.let { it.xRanges = xRanges.size }
                xRanges.forEach { range ->
                    val min = range.min.coerceAtLeast(0.0)
                    val max = range.max.coerceAtMost(HeatmapWorld.WORLD_WIDTH)
                    if (min > max) return@forEach
                    val cxStart = (min * gridSize).toInt().coerceIn(0, gridSize - 1)
                    val cxEnd = ((max * gridSize).toInt()).coerceIn(0, gridSize - 1)
                    for (cy in cyStart..cyEnd) {
                        val row = cy * gridSize
                        for (cx in cxStart..cxEnd) {
                            if (trackStats) {
                                cellsVisited += 1
                            }
                            var i = heads[row + cx]
                            while (i != -1) {
                                if (trackStats) {
                                    candidatesVisited += 1
                                }
                                val point = points[i]
                                if (point.y >= geometry.minY && point.y <= geometry.maxY) {
                                    val xAdj = point.x + range.offset
                                    if (xAdj >= geometry.minX && xAdj <= geometry.maxX) {
                                        addPoint(xAdj, point.y, point.intensity)
                                    }
                                }
                                i = next[i]
                            }
                        }
                    }
                }
            }
        }

        if (timings != null) {
            timings.cellsVisited = cellsVisited
            timings.candidatesVisited = candidatesVisited
            timings.pointsBinned = pointsBinned
        }
        return hasPoints
    }
}
