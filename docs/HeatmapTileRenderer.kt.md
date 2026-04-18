# HeatmapTileRenderer

A high-performance tile provider that renders heatmap tiles on-the-fly from a set of weighted
geographical points. Implements `TileProviderInterface`, making it compatible with map frameworks
that support custom tile sources.

Performance features:
- **Multi-threaded rendering**: A pool of worker threads renders tiles in the background.
- **In-memory LRU cache**: Recently rendered tiles are cached to avoid redundant computation.
- **Spatial indexing**: Large datasets use a spatial index to quickly query points per tile.
- **Dynamic optimization**: Adjusts rendering parameters based on camera zoom and tile complexity.

## Signature

```kotlin
class HeatmapTileRenderer(
    val tileSize: Int = DEFAULT_TILE_SIZE,
    cacheSizeKb: Int = DEFAULT_CACHE_SIZE_KB,
    maxConcurrentRenders: Int = DEFAULT_MAX_CONCURRENT_RENDERS,
    private val pngCompressionLevel: Int = DEFAULT_PNG_COMPRESSION_LEVEL,
) : TileProviderInterface
```

## Constructor Parameters

- `tileSize`
    - Type: `Int`
    - Default: `DEFAULT_TILE_SIZE` (`512`)
    - Description: The width and height of generated tiles in pixels.
- `cacheSizeKb`
    - Type: `Int`
    - Default: `DEFAULT_CACHE_SIZE_KB` (`8192`)
    - Description: The maximum size of the in-memory tile cache in kilobytes.
- `maxConcurrentRenders`
    - Type: `Int`
    - Default: `DEFAULT_MAX_CONCURRENT_RENDERS` (`2`)
    - Description: The number of worker threads for rendering tiles. More threads increase
      throughput on multi-core devices but also increase CPU and memory usage.
- `pngCompressionLevel`
    - Type: `Int`
    - Default: `DEFAULT_PNG_COMPRESSION_LEVEL` (`1`)
    - Description: The PNG compression level (`0`–`9`). `0` is fastest with no compression,
      `1` is the default balance, and `9` is slowest with best compression. The renderer may
      dynamically fall back to level `0` for complex tiles to maintain low latency.

## Methods

### `update`

Updates the heatmap with a new dataset and rendering parameters. Asynchronously regenerates
tiles and invalidates the existing cache.

**Signature**

```kotlin
fun update(
    points: List<HeatmapPoint>,
    radiusPx: Int,
    gradient: HeatmapGradient,
    maxIntensity: Double?,
)
```

**Parameters**

- `points`
    - Type: `List<HeatmapPoint>`
    - Description: The list of data points to render. Each point has a location and optional weight.
- `radiusPx`
    - Type: `Int`
    - Description: The radius of influence per point in pixels. Determines how "spread out" the
      heat from each point is.
- `gradient`
    - Type: `HeatmapGradient`
    - Description: The color gradient that maps intensity values to colors.
- `maxIntensity`
    - Type: `Double?`
    - Description: Optional maximum intensity. If `null`, it is calculated from the data. Set a
      fixed value to maintain a consistent color scale across different datasets.

### `updateCameraZoom`

Informs the renderer of the current map zoom level. The renderer uses this to adjust the
effective point radius, ensuring visual consistency and optimizing performance during zoom and
pan operations.

**Signature**

```kotlin
fun updateCameraZoom(zoom: Double)
```

**Parameters**

- `zoom`
    - Type: `Double`
    - Description: The current zoom level of the map camera.

### `renderTile`

Renders a single map tile for the given request. Part of `TileProviderInterface` — typically
called by the map framework. Checks the in-memory cache first; if not found, queues a render job
on a worker thread and blocks until the tile is ready.

**Signature**

```kotlin
override fun renderTile(request: TileRequest): ByteArray?
```

**Parameters**

- `request`
    - Type: `TileRequest`
    - Description: An object containing the tile coordinates (`z`, `x`, `y`).

**Returns**

- Type: `ByteArray?`
- Description: The rendered tile as a PNG image. A fully transparent tile is returned for areas
  with no data. Returns `null` if the tile could not be rendered.

## Companion Object — Default Constants

- `DEFAULT_TILE_SIZE`
    - Type: `Int`
    - Value: `512`
    - Description: The default tile size in pixels.
- `DEFAULT_PNG_COMPRESSION_LEVEL`
    - Type: `Int`
    - Value: `1`
    - Description: The default PNG compression level.

## Example

```kotlin
val renderer = HeatmapTileRenderer(
    tileSize = 512,
    cacheSizeKb = 16 * 1024,
    maxConcurrentRenders = 4,
)

renderer.update(
    points = listOf(
        HeatmapPoint(GeoPoint(34.0522, -118.2437), weight = 10.0),
        HeatmapPoint(GeoPoint(40.7128, -74.0060), weight = 25.0),
    ),
    radiusPx = 30,
    gradient = HeatmapGradient.DEFAULT,
    maxIntensity = null,
)

// In a camera move listener:
renderer.updateCameraZoom(cameraPosition.zoom)
```
