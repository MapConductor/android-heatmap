package com.mapconductor.heatmap

import com.mapconductor.core.tileserver.TileProviderInterface
import com.mapconductor.core.tileserver.TileRequest
import java.util.Arrays
import kotlin.math.pow
import kotlin.math.roundToInt
import android.graphics.Color
import android.util.Log

/**
 * ヒートマップのタイルを描くタイルプロバイダ。
 *
 * このクラスが持つのは**元データの保持とタイル要求の段取り**だけで、
 * 実際の計算は責務ごとのファイルにある:
 *
 * | 部品                     | 担当                                       |
 * |--------------------------|--------------------------------------------|
 * | [HeatmapWorld]           | 緯度経度→世界座標、範囲、空間インデックス  |
 * | [HeatmapIntensity]       | ズームごとの色マップ上限                   |
 * | [HeatmapColorMap]        | グラデーション→強度別の色表                |
 * | [HeatmapTileBinner]      | タイルにかかる点を格子へ集める             |
 * | [HeatmapKernel]          | ガウシアンカーネルと畳み込み               |
 * | [PngEncoder]             | 強度配列→PNG                               |
 * | [HeatmapRenderBuffers]   | スレッドごとの作業配列                     |
 *
 * ios-sdk / react-sdk も同じ責務分けのファイル構成にしてある。
 */
class HeatmapTileRenderer(
    val tileSize: Int = DEFAULT_TILE_SIZE,
    cacheSizeKb: Int = DEFAULT_CACHE_SIZE_KB,
    maxConcurrentRenders: Int = DEFAULT_MAX_CONCURRENT_RENDERS,
    private val pngCompressionLevel: Int = DEFAULT_PNG_COMPRESSION_LEVEL,
) : TileProviderInterface {
    @Volatile
    private var didWarmUp: Boolean = false

    private val transparentTileBytes: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val colors = IntArray(tileSize * tileSize) { Color.TRANSPARENT }
        PngEncoder.encodeRgba(
            colors = colors,
            width = tileSize,
            height = tileSize,
            buffers = PngBuffers(),
            compressionLevel = pngCompressionLevel,
        )
    }

    private val kernel = HeatmapKernel()

    private val pipeline =
        HeatmapTilePipeline(
            cacheSizeKb = cacheSizeKb,
            workerCount = maxConcurrentRenders.coerceIn(1, MAX_MAX_CONCURRENT_RENDERS),
            tileSize = tileSize,
            transparentTile = { transparentTileBytes },
            render = ::renderTileInternal,
        )

    @Volatile
    private var cameraZoomQuantized: Double? = null

    @Volatile
    private var cameraZoomKey: Int? = null

    @Volatile
    private var state =
        TileState(
            points = emptyList(),
            index = null,
            bounds = null,
            radiusPx = DEFAULT_RADIUS_PX,
            colorMap = IntArray(HeatmapColorMap.COLOR_MAP_SIZE) { Color.TRANSPARENT },
            maxIntensities = DoubleArray(HeatmapIntensity.MAX_ZOOM_LEVEL),
        )

    private val threadLocalBuffers = ThreadLocal<HeatmapRenderBuffers>()

    fun update(
        points: List<HeatmapPoint>,
        radiusPx: Int,
        gradient: HeatmapGradient,
        maxIntensity: Double?,
    ) {
        val safeRadius = radiusPx.coerceAtLeast(1)
        val weightedPoints = HeatmapWorld.buildWeightedPoints(points)
        val bounds = if (weightedPoints.isEmpty()) null else HeatmapWorld.calculateBounds(weightedPoints)
        // 点が少ないときはインデックスを引く方が高くつくので作らない。
        val index =
            if (weightedPoints.size < INDEX_BUILD_THRESHOLD) null else HeatmapWorld.buildPointIndex(weightedPoints)
        val colorMap = HeatmapColorMap.build(gradient)
        val maxIntensities =
            if (bounds == null) {
                DoubleArray(HeatmapIntensity.MAX_ZOOM_LEVEL)
            } else {
                HeatmapIntensity.getMaxIntensities(weightedPoints, bounds, safeRadius, maxIntensity)
            }
        if (!didWarmUp) {
            didWarmUp = true
            warmUp(colorMap)
        }
        state =
            TileState(
                points = weightedPoints,
                index = index,
                bounds = bounds,
                radiusPx = safeRadius,
                colorMap = colorMap,
                maxIntensities = maxIntensities,
            )
        logUpdate(points.size, weightedPoints, safeRadius, bounds, index)
        pipeline.invalidate()
    }

    private fun logUpdate(
        inputCount: Int,
        weightedPoints: List<WeightedPoint>,
        radiusPx: Int,
        bounds: Bounds?,
        index: PointIndex?,
    ) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val boundsText =
            if (bounds == null) "null" else "(${bounds.minX},${bounds.minY})-(${bounds.maxX},${bounds.maxY})"
        val indexText =
            if (index == null) {
                "null"
            } else {
                "grid=${index.gridSize} nonEmpty=${index.nonEmptyBuckets} maxBucket=${index.maxBucketSize}"
            }
        Log.d(
            TAG,
            "update pointsIn=$inputCount weighted=${weightedPoints.size} radiusPx=$radiusPx " +
                "bounds=$boundsText index=$indexText",
        )
    }

    /**
     * 最初のタイルで出る引っかかりを減らすため、熱い経路を一度空回しする。
     * ART の JIT / プロファイルを温めるのが目的で、結果は捨てる。
     */
    private fun warmUp(colorMap: IntArray) {
        try {
            val radius = 1
            val tileSize = 8
            val gridDim = tileSize + radius * 2
            val warmKernel = kernel.resolveKernel(radius)
            val input = FloatArray(gridDim * gridDim)
            val intermediate = FloatArray(gridDim * gridDim)
            val output = FloatArray(tileSize * tileSize)
            val center = gridDim * (gridDim / 2) + (gridDim / 2)
            input[center] = 1.0f
            val nonZeroInput = intArrayOf(center)
            val nonZeroIntermediate = IntArray(gridDim * gridDim)
            kernel.convolveSparseToOutput(
                input = input,
                intermediate = intermediate,
                output = output,
                kernel = warmKernel,
                gridDim = gridDim,
                radius = radius,
                tileSize = tileSize,
                nonZeroInput = nonZeroInput,
                nonZeroInputCount = nonZeroInput.size,
                nonZeroIntermediate = nonZeroIntermediate,
                nonZeroIntermediateCountOut = {},
            )
            PngEncoder.encodeFromIntensity(
                intensity = output,
                colorMap = colorMap,
                maxIntensity = 1.0,
                width = tileSize,
                height = tileSize,
                buffers = PngBuffers(),
                compressionLevel = pngCompressionLevel,
            )
        } catch (_: Exception) {
            // Ignore warm-up failures; rendering will proceed normally.
        }
    }

    /**
     * カメラのズームを覚える。半径はこの値で決まるので、タイルの z ではなく
     * 実際の見え方に合わせた太さになる。刻んで持つのはキャッシュキーを
     * 安定させるため（連続値だと毎フレーム別キーになる）。
     */
    fun updateCameraZoom(zoom: Double) {
        val nextKey = (zoom * CAMERA_ZOOM_KEY_SCALE).roundToInt()
        val prevKey = cameraZoomKey
        if (prevKey == nextKey && cameraZoomQuantized != null) return
        cameraZoomKey = nextKey
        cameraZoomQuantized = nextKey.toDouble() / CAMERA_ZOOM_KEY_SCALE
    }

    override fun renderTile(request: TileRequest): ByteArray? {
        val zoomKey = cameraZoomKey ?: (request.z * CAMERA_ZOOM_KEY_SCALE)
        return pipeline.request(request, zoomKey, state)
    }

    /** @return タイルの PNG。描くものが無いときは null（呼び出し側が透明タイルに倒す）。 */
    private fun renderTileInternal(
        request: TileRequest,
        tileState: TileState,
        timings: HeatmapPhaseTimings,
    ): ByteArray? {
        val setupStartNs = System.nanoTime()
        val bounds = tileState.bounds ?: return null
        if (tileState.points.isEmpty()) return null

        val zoom = request.z.toDouble()
        val effectiveZoom = cameraZoomQuantized ?: zoom
        val zoomScale = 2.0.pow(effectiveZoom - zoom)
        val radius = (tileState.radiusPx / zoomScale).roundToInt().coerceAtLeast(1)
        val tileKernel = kernel.resolveKernel(radius)
        val tileWidth = HeatmapWorld.WORLD_WIDTH / 2.0.pow(zoom)
        // 半径ぶんの余白を取る。隣のタイルにある点も、この余白ぶんは影響するため。
        val padding = tileWidth * radius / tileSize
        val gridDim = tileSize + radius * 2
        val bucketWidth = (tileWidth + 2 * padding) / gridDim

        timings.let {
            it.effectiveZoom = ((effectiveZoom * 10.0).roundToInt() / 10.0)
            it.radius = radius
            it.gridDim = gridDim
        }

        val geometry =
            TileGeometry(
                minX = request.x * tileWidth - padding,
                maxX = (request.x + 1) * tileWidth + padding,
                minY = request.y * tileWidth - padding,
                maxY = (request.y + 1) * tileWidth + padding,
                bucketWidth = bucketWidth,
                gridDim = gridDim,
            )

        val tileBounds = Bounds(geometry.minX, geometry.maxX, geometry.minY, geometry.maxY)
        val paddedBounds =
            Bounds(
                minX = bounds.minX - padding,
                maxX = bounds.maxX + padding,
                minY = bounds.minY - padding,
                maxY = bounds.maxY + padding,
            )
        if (!tileBounds.intersects(paddedBounds)) return null

        val buffers = resetBuffers(gridDim)
        timings.let { it.setupMs = msSince(setupStartNs) }

        val binStartNs = System.nanoTime()
        val hasPoints = HeatmapTileBinner.bin(tileState.points, tileState.index, geometry, buffers, timings)
        if (!hasPoints) return null
        timings.let { it.binMs = msSince(binStartNs) }

        val convolveStartNs = System.nanoTime()
        kernel.convolveSparseToOutput(
            input = buffers.intensity,
            intermediate = buffers.intermediate,
            output = buffers.output,
            kernel = tileKernel,
            gridDim = gridDim,
            radius = radius,
            tileSize = tileSize,
            nonZeroInput = buffers.nonZeroIntensity,
            nonZeroInputCount = buffers.nonZeroIntensityCount,
            nonZeroIntermediate = buffers.nonZeroIntermediate,
            nonZeroIntermediateCountOut = { buffers.nonZeroIntermediateCount = it },
        )
        timings.let { it.convolveMs = msSince(convolveStartNs) }

        val intensityZoom = effectiveZoom.toInt().coerceIn(0, tileState.maxIntensities.lastIndex)
        val maxIntensity = tileState.maxIntensities[intensityZoom]
        if (maxIntensity <= 0.0) return null

        val pngStartNs = System.nanoTime()
        val level = effectivePngCompressionLevel(radius, buffers.nonZeroIntensityCount)
        timings.let { it.pngLevel = level }
        val out =
            PngEncoder.encodeFromIntensity(
                intensity = buffers.output,
                colorMap = tileState.colorMap,
                maxIntensity = maxIntensity,
                width = tileSize,
                height = tileSize,
                buffers = buffers.png,
                compressionLevel = level,
            )
        timings.let { it.pngMs = msSince(pngStartNs) }
        return out
    }

    private fun resetBuffers(gridDim: Int): HeatmapRenderBuffers {
        val buffers = buffers()
        buffers.ensure(gridDim = gridDim, tileSize = tileSize)
        val gridLen = gridDim * gridDim
        val tileLen = tileSize * tileSize
        Arrays.fill(buffers.intensity, 0, gridLen, 0.0f)
        Arrays.fill(buffers.intermediate, 0, gridLen, 0.0f)
        Arrays.fill(buffers.output, 0, tileLen, 0.0f)
        buffers.nonZeroIntensityCount = 0
        buffers.nonZeroIntermediateCount = 0
        return buffers
    }

    /**
     * 信号の多いタイルは deflate が重くなる（色数が増えて圧縮が効かない）ので、
     * 遅延を優先して無圧縮に落とす。
     */
    private fun effectivePngCompressionLevel(
        radius: Int,
        nonZeroCount: Int,
    ): Int =
        if (radius >= PNG_COMPLEX_TILE_RADIUS_THRESHOLD_PX || nonZeroCount >= PNG_COMPLEX_TILE_POINT_THRESHOLD) {
            0
        } else {
            pngCompressionLevel
        }

    private fun buffers(): HeatmapRenderBuffers {
        val existing = threadLocalBuffers.get()
        if (existing != null) return existing
        val created = HeatmapRenderBuffers()
        threadLocalBuffers.set(created)
        return created
    }

    private fun msSince(startNs: Long): Double {
        val ms = (System.nanoTime() - startNs) / 1_000_000.0
        return (ms * 10.0).roundToInt() / 10.0
    }

    companion object {
        // 256 is the de-facto standard tile size across map SDKs; some (e.g. ArcGIS WebTiledLayer)
        // behave inconsistently when given 512 here, which can lead to mismatched (z,x,y) requests.
        const val DEFAULT_TILE_SIZE = 512
        const val DEFAULT_PNG_COMPRESSION_LEVEL = 1
        private const val DEFAULT_CACHE_SIZE_KB = 8 * 1024
        private const val DEFAULT_RADIUS_PX = 20
        private const val DEFAULT_MAX_CONCURRENT_RENDERS = 2
        private const val MAX_MAX_CONCURRENT_RENDERS = 8
        private const val INDEX_BUILD_THRESHOLD = 1024
        private const val CAMERA_ZOOM_KEY_SCALE = 4
        private const val TAG = "HeatmapTileRenderer"
        private const val PNG_COMPLEX_TILE_POINT_THRESHOLD = 128
        private const val PNG_COMPLEX_TILE_RADIUS_THRESHOLD_PX = 8
    }
}
