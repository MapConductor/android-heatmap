# HeatmapOverlay (State-based)

A convenience overload that uses a `HeatmapOverlayState` object to configure the heatmap. This is
the recommended approach when you need to manage the heatmap's properties as a single observable
state object.

## Signature

```kotlin
@Composable
fun MapViewScope.HeatmapOverlay(
    state: HeatmapOverlayState,
    content: @Composable () -> Unit,
)
```

## Description

Renders a heatmap layer configured by the provided `state` object. Data points are defined by
`HeatmapPoint` composables placed inside the `content` block.

## Parameters

- `state`
    - Type: `HeatmapOverlayState`
    - Description: **Required.** The state object holding the heatmap configuration, including
      `radiusPx`, `opacity`, `gradient`, `maxIntensity`, and `weightProvider`.
- `content`
    - Type: `@Composable () -> Unit`
    - Description: **Required.** A composable lambda where `HeatmapPoint` children are placed.

## Example

```kotlin
@Composable
fun HeatmapWithStateExample() {
    val heatmapState = remember {
        HeatmapOverlayState(
            radiusPx = 35,
            opacity = 0.75,
        )
    }

    GoogleMapView(state = mapViewState) {
        HeatmapOverlay(state = heatmapState) {
            HeatmapPoint(position = GeoPoint(34.0522, -118.2437), weight = 0.8)
            HeatmapPoint(position = GeoPoint(40.7128, -74.0060))
        }
    }
}
```

---

# HeatmapOverlay (Detailed)

An overload that provides fine-grained control over all heatmap properties directly as parameters.
Useful for static configurations or when you prefer to manage individual properties.

## Signature

```kotlin
@Composable
fun MapViewScope.HeatmapOverlay(
    radiusPx: Int = HeatmapDefaults.DEFAULT_RADIUS_PX,
    opacity: Double = HeatmapDefaults.DEFAULT_OPACITY,
    gradient: HeatmapGradient = HeatmapGradient.DEFAULT,
    maxIntensity: Double? = null,
    weightProvider: (HeatmapPointState) -> Double = { state -> state.weight },
    tileSize: Int = HeatmapTileRenderer.DEFAULT_TILE_SIZE,
    trackPointUpdates: Boolean = false,
    disableTileServerCache: Boolean = false,
    content: @Composable () -> Unit,
)
```

## Description

Creates a heatmap layer by rendering data points onto tiles and displaying them as a raster
overlay. Offers detailed configuration options for appearance and performance.

## Parameters

- `radiusPx`
    - Type: `Int`
    - Default: `HeatmapDefaults.DEFAULT_RADIUS_PX` (`20`)
    - Description: The radius of influence for each data point in pixels. Larger values create a
      smoother, more blurry heatmap.
- `opacity`
    - Type: `Double`
    - Default: `HeatmapDefaults.DEFAULT_OPACITY` (`0.7`)
    - Description: The opacity of the heatmap layer (`0.0` = fully transparent, `1.0` = opaque).
- `gradient`
    - Type: `HeatmapGradient`
    - Default: `HeatmapGradient.DEFAULT`
    - Description: The color gradient used to render the heatmap based on data intensity.
- `maxIntensity`
    - Type: `Double?`
    - Default: `null`
    - Description: The intensity value that maps to the hottest color in the gradient. If `null`,
      the maximum is calculated from the data. Set a fixed value for a consistent color scale
      across different datasets.
- `weightProvider`
    - Type: `(HeatmapPointState) -> Double`
    - Default: `{ state -> state.weight }`
    - Description: A function to extract a numerical weight from a `HeatmapPointState`. Allows
      custom logic to determine the influence of each point.
- `tileSize`
    - Type: `Int`
    - Default: `HeatmapTileRenderer.DEFAULT_TILE_SIZE` (`512`)
    - Description: The size of the underlying raster tiles in pixels.
- `trackPointUpdates`
    - Type: `Boolean`
    - Default: `false`
    - Description: If `true`, re-renders the heatmap when properties of existing `HeatmapPoint`s
      (e.g., `weight`) change. If `false`, only updates when points are added or removed.
      Enabling this may impact performance with large datasets.
- `disableTileServerCache`
    - Type: `Boolean`
    - Default: `false`
    - Description: If `true`, disables the internal tile server's in-memory cache. Primarily for
      debugging; can degrade rendering performance.
- `content`
    - Type: `@Composable () -> Unit`
    - Description: **Required.** A composable lambda where `HeatmapPoint` children are placed.

## Example

```kotlin
@Composable
fun DetailedHeatmapExample() {
    // Replace "MapView" with semantic SDK mapview such as "GoogleMapView"
    MapView(state = mapViewState) {
        HeatmapOverlay(
            radiusPx = 50,
            opacity = 0.8,
            gradient = HeatmapGradient.DEFAULT,
            trackPointUpdates = false,
        ) {
            HeatmapPoint(position = GeoPoint(40.7128, -74.0060), weight = 1.0)
            HeatmapPoint(position = GeoPoint(34.0522, -118.2437), weight = 0.7)
        }
    }
}
```
