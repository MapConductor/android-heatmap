package com.mapconductor.heatmap

/**
 * タイル 1 枚を描く間だけ使う作業配列。**スレッドごとに使い回す**。
 *
 * 格子・中間結果・出力はタイルサイズと半径で決まる大きさになる。半径 20px、
 * タイル 512px なら格子だけで 30 万要素あり、1 枚ごとに確保すると GC を強く叩く。
 * サイズが変わったときだけ作り直し、それ以外は使い回す。
 *
 * [nonZeroIntensity] / [nonZeroIntermediate] は「値が入っている添字」の控えで、
 * これがあるので [HeatmapKernel] は全セルを走査せずに済む。
 */
internal class HeatmapRenderBuffers {
    var gridDim: Int = 0
    private var gridDimCapacity: Int = 0
    var tileSize: Int = 0
    var intensity: FloatArray = FloatArray(0)
    var intermediate: FloatArray = FloatArray(0)
    var output: FloatArray = FloatArray(0)
    var png: PngBuffers = PngBuffers()
    var nonZeroIntensity: IntArray = IntArray(0)
    var nonZeroIntermediate: IntArray = IntArray(0)
    var nonZeroIntensityCount: Int = 0
    var nonZeroIntermediateCount: Int = 0

    fun ensure(
        gridDim: Int,
        tileSize: Int,
    ) {
        this.gridDim = gridDim
        if (gridDimCapacity < gridDim) {
            gridDimCapacity = gridDim
            this.gridDim = gridDim
            intensity = FloatArray(gridDimCapacity * gridDimCapacity)
            intermediate = FloatArray(gridDimCapacity * gridDimCapacity)
            nonZeroIntensity = IntArray(gridDimCapacity * gridDimCapacity)
            nonZeroIntermediate = IntArray(gridDimCapacity * gridDimCapacity)
        }
        if (this.tileSize != tileSize) {
            this.tileSize = tileSize
            output = FloatArray(tileSize * tileSize)
        }
    }
}

/**
 * 遅いタイルの内訳を出すための計測値。閾値を超えたときだけログに出す。
 *
 * 「遅い」と分かっても、点の選び方が悪いのか畳み込みが重いのか PNG 圧縮が
 * 効いていないのかで打ち手が変わる。段階ごとの時間と、走査した数を残しておく。
 */
internal class HeatmapPhaseTimings {
    var effectiveZoom: Double = 0.0
    var radius: Int = 0
    var gridDim: Int = 0
    var usedIndex: Boolean = false
    var indexGridSize: Int = 0
    var indexNonEmptyBuckets: Int = 0
    var indexMaxBucketSize: Int = 0
    var xRanges: Int = 0
    var cellsVisited: Int = 0
    var candidatesVisited: Int = 0
    var pointsBinned: Int = 0
    var setupMs: Double = 0.0
    var binMs: Double = 0.0
    var convolveMs: Double = 0.0
    var pngMs: Double = 0.0
    var pngLevel: Int = 0

    fun reset() {
        effectiveZoom = 0.0
        radius = 0
        gridDim = 0
        usedIndex = false
        indexGridSize = 0
        indexNonEmptyBuckets = 0
        indexMaxBucketSize = 0
        xRanges = 0
        cellsVisited = 0
        candidatesVisited = 0
        pointsBinned = 0
        setupMs = 0.0
        binMs = 0.0
        convolveMs = 0.0
        pngMs = 0.0
        pngLevel = 0
    }

    /** 遅いタイルのログに出す 1 行。 */
    fun format(): String =
        """
        effZoom=$effectiveZoom
        radius=$radius
        gridDim=$gridDim
        index=$usedIndex
        idxGrid=$indexGridSize
        idxNonEmpty=$indexNonEmptyBuckets
        idxMaxBucket=$indexMaxBucketSize
        xRanges=$xRanges
        cells=$cellsVisited
        cand=$candidatesVisited
        binned=$pointsBinned
        setup=${setupMs}ms
        bin=${binMs}ms
        conv=${convolveMs}ms
        mapPng=${pngMs}ms
        pngLevel=$pngLevel
        """.trimIndent()
}
