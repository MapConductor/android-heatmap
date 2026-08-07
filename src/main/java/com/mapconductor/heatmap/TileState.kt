package com.mapconductor.heatmap

/**
 * タイル 1 枚を描くのに必要な元データ一式。
 *
 * **まとめて 1 つの参照にしてある**のが要点。描いている最中に
 * [HeatmapTileRenderer.update] が来ても、途中まで新しい点で途中まで古い色マップ、
 * といった混ざり方をしない。差し替えは参照 1 本の代入で済む。
 */
internal data class TileState(
    val points: List<WeightedPoint>,
    val index: PointIndex?,
    val bounds: Bounds?,
    val radiusPx: Int,
    val colorMap: IntArray,
    val maxIntensities: DoubleArray,
)
