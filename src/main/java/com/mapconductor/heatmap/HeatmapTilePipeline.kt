package com.mapconductor.heatmap

import com.mapconductor.core.tileserver.TileRequest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import android.util.Log
import android.util.LruCache

/**
 * タイル要求のキャッシュ・重複排除・ワーカーへの割り振り。**中身は描かない**。
 *
 * 地図はスクロールのたびに同じタイルを何度も要求し、しかも複数の要求が同時に来る。
 * ここで 3 つ面倒を見る:
 *
 * 1. **キャッシュ** — 元データが変わるたびに epoch を進め、キーごと無効にする。
 * 2. **重複排除** — 同じタイルが同時に来たら 1 回だけ描いて全員で待つ（[inFlight]）。
 * 3. **背圧** — キューが埋まったら待たせる。捨てると親タイルへの
 *    フォールバックが起きて継ぎ目が見えるので、遅らせる方を選ぶ。
 *
 * 空タイルは透明 PNG の実体ではなく [emptyTileMarker] で覚える。
 * 何百枚ぶんも同じバイト列を持つ意味がないため。
 */
internal class HeatmapTilePipeline(
    cacheSizeKb: Int,
    workerCount: Int,
    private val tileSize: Int,
    /** 透明タイルの実体。初回に一度だけ作られる。 */
    private val transparentTile: () -> ByteArray,
    /** 実際の描画。null を返したら「描くものが無い」。 */
    private val render: (TileRequest, TileState, HeatmapPhaseTimings) -> ByteArray?,
) {
    private val cacheLock = Any()
    private val cache =
        object : LruCache<String, ByteArray>(cacheSizeKb) {
            override fun sizeOf(
                key: String,
                value: ByteArray,
            ): Int = (value.size / 1024).coerceAtLeast(1)
        }
    private val emptyTileMarker = ByteArray(1)
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<ByteArray?>>()
    private val renderQueue =
        ArrayBlockingQueue<RenderJob>(
            MAX_RENDER_QUEUE_SIZE,
            // fair =
            false,
        )
    private val threadLocalPhaseTimings = ThreadLocal<HeatmapPhaseTimings>()

    @Volatile
    private var cacheEpoch: Long = 0L

    init {
        repeat(workerCount.coerceAtLeast(1)) { index ->
            Thread({ renderLoop() }, "HeatmapTileRenderer-$index").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** 元データが変わった。以後のキーを別物にし、今のキャッシュを捨てる。 */
    fun invalidate() {
        synchronized(cacheLock) {
            cacheEpoch += 1
            cache.evictAll()
        }
    }

    fun request(
        request: TileRequest,
        zoomKey: Int,
        state: TileState,
    ): ByteArray? {
        val epoch = cacheEpoch
        val key = "$epoch:$zoomKey:${request.z}/${request.x}/${request.y}"
        synchronized(cacheLock) {
            cache.get(key)?.let { return resolve(it) }
        }

        val future = CompletableFuture<ByteArray?>()
        val existing = inFlight.putIfAbsent(key, future)
        if (existing != null) {
            return existing.join()
        }
        val job =
            RenderJob(
                key = key,
                epoch = epoch,
                enqueuedAtNs = System.nanoTime(),
                request = request,
                state = state,
                future = future,
            )
        try {
            renderQueue.put(job)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            inFlight.remove(key)
            future.complete(null)
            return null
        }
        return future.join()
    }

    private fun resolve(cached: ByteArray): ByteArray = if (cached === emptyTileMarker) transparentTile() else cached

    private fun renderLoop() {
        while (true) {
            val job =
                try {
                    renderQueue.take()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }

            try {
                val workStartNs = System.nanoTime()
                val queueWaitMs = (workStartNs - job.enqueuedAtNs) / 1_000_000.0
                val timings = phaseTimings().also { it.reset() }
                synchronized(cacheLock) {
                    cache.get(job.key)?.let { cached ->
                        job.future.complete(resolve(cached))
                        continue
                    }
                }

                val renderStartNs = System.nanoTime()
                val bytes = render(job.request, job.state, timings)
                val renderMs = (System.nanoTime() - renderStartNs) / 1_000_000.0

                synchronized(cacheLock) {
                    if (cacheEpoch == job.epoch) {
                        cache.put(job.key, bytes ?: emptyTileMarker)
                    }
                }

                logIfSlow(job, queueWaitMs, renderMs, bytes == null, timings)
                job.future.complete(bytes ?: transparentTile())
            } catch (e: Exception) {
                job.future.completeExceptionally(e)
            } finally {
                inFlight.remove(job.key)
            }
        }
    }

    private fun logIfSlow(
        job: RenderJob,
        queueWaitMs: Double,
        renderMs: Double,
        isEmptyTile: Boolean,
        timings: HeatmapPhaseTimings,
    ) {
        val totalMs = queueWaitMs + renderMs
        if (totalMs < SLOW_TILE_LOG_THRESHOLD_MS) return
        val qw = (queueWaitMs * 10.0).roundToInt() / 10.0
        val rm = (renderMs * 10.0).roundToInt() / 10.0
        val tm = (totalMs * 10.0).roundToInt() / 10.0
        Log.w(
            TAG,
            """
            Slow tile breakdown
            z=${job.request.z}
            x=${job.request.x}
            y=${job.request.y}
            queueWait=${qw}ms
            render=${rm}ms
            total=${tm}ms
            points=${job.state.points.size}
            tileSize=$tileSize
            isEmptyTile=$isEmptyTile${timings.format()}
            """.trimIndent(),
        )
    }

    private fun phaseTimings(): HeatmapPhaseTimings {
        val existing = threadLocalPhaseTimings.get()
        if (existing != null) return existing
        val created = HeatmapPhaseTimings()
        threadLocalPhaseTimings.set(created)
        return created
    }

    private data class RenderJob(
        val key: String,
        val epoch: Long,
        val enqueuedAtNs: Long,
        val request: TileRequest,
        val state: TileState,
        val future: CompletableFuture<ByteArray?>,
    )

    private companion object {
        const val MAX_RENDER_QUEUE_SIZE = 2048
        const val SLOW_TILE_LOG_THRESHOLD_MS = 250.0
        const val TAG = "HeatmapTileRenderer"
    }
}
