package com.mapconductor.heatmap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * タイルを描く前の「データ範囲と重なるか」の判定が、日付変更線の向こう側も見ることを確かめる。
 * 点が経度 179.9 付近にしか無いとき、z1 の西半球タイル (x=0) の左端には
 * HeatmapTileBinner が回り込ませた点がにじむので、ここで弾いてはいけない。
 * react-sdk の tileRenderer.test.mjs と同じ配置で見る。
 */
class BoundsWrapTest {
    @Test
    fun dataJustEastOfTheDateLineReachesTheWesternTile() {
        val dataX = HeatmapWorld.WORLD_WIDTH * (179.9 + 180.0) / 360.0
        val padding = 0.5 * 10.0 / 64.0 // z1 のタイル幅 0.5、半径 10px、64px タイル
        val data = Bounds(dataX - padding, dataX + padding, 0.5 - padding, 0.5 + padding)
        val westTile = Bounds(0.0 - padding, 0.5 + padding, 0.5 - padding, 1.0 + padding)

        assertFalse(westTile.intersects(data))
        assertTrue(westTile.intersectsWrapped(data))
    }

    @Test
    fun wrappingDoesNotMatchTilesThatAreFarAway() {
        val data = Bounds(0.99, 1.0, 0.5, 0.51)
        // 西端から離れたタイル、y の外れたタイルは 1 周ずらしても重ならない。
        assertFalse(Bounds(0.2, 0.3, 0.5, 0.51).intersectsWrapped(data))
        assertFalse(Bounds(-0.01, 0.01, 0.0, 0.1).intersectsWrapped(data))
    }
}
