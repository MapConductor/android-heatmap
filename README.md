# MapConductor Heatmap

## Description

MapConductor Heatmap is a map-implementation-agnostic heatmap overlay for the MapConductor SDK.
It renders heatmap data as a tile-based `RasterLayer` so it works with any map implementation (Google Maps, MapLibre, Mapbox, ArcGIS, HERE, etc.).

## Setup

https://docs-android.mapconductor.com/setup/

------------------------------------------------------------------------

## Usage

### Basic HeatmapOverlay

Place `HeatmapOverlay` inside any `XxxMapView` content block and add points with `HeatmapPoint`:

```kotlin
XxxMapView(...) {
    HeatmapOverlay(
        radiusPx = 20,
        opacity = 0.7,
    ) {
        HeatmapPoint(
            position = GeoPoint(latitude = 35.6762, longitude = 139.6503),
            weight = 1.0,
        )
        HeatmapPoint(
            position = GeoPoint(latitude = 35.6895, longitude = 139.6917),
            weight = 2.5,
        )
    }
}
```

### HeatmapPoints (batch)

For large datasets, use `HeatmapPoints` to add all points in a single update:

```kotlin
val pointStates: List<HeatmapPointState> = remember { buildPointList() }

XxxMapView(...) {
    HeatmapOverlay(radiusPx = 20) {
        HeatmapPoints(pointStates)
    }
}
```

### HeatmapOverlayState

Use `HeatmapOverlayState` to control heatmap properties dynamically:

```kotlin
val heatmapState = remember {
    HeatmapOverlayState(
        radiusPx = 20,
        opacity = 0.7,
        gradient = HeatmapGradient.DEFAULT,
    )
}

// Update dynamically
heatmapState.radiusPx = 30
heatmapState.opacity = 0.5

XxxMapView(...) {
    HeatmapOverlay(state = heatmapState) {
        HeatmapPoints(pointStates)
    }
}
```

### Custom HeatmapGradient

```kotlin
val gradient = HeatmapGradient(
    listOf(
        HeatmapGradientStop(position = 0.0, color = Color.argb(0, 0, 0, 255)),   // transparent blue
        HeatmapGradientStop(position = 0.5, color = Color.rgb(0, 255, 0)),         // green
        HeatmapGradientStop(position = 1.0, color = Color.rgb(255, 0, 0)),         // red
    )
)

val heatmapState = remember {
    HeatmapOverlayState(gradient = gradient)
}
```

### HeatmapPointState

`HeatmapPointState` allows per-point customization:

```kotlin
val pointState = HeatmapPointState(
    position = GeoPoint(latitude = 35.6762, longitude = 139.6503),
    weight = 3.0,
)
```

------------------------------------------------------------------------

## API Reference

### HeatmapOverlay

| Parameter | Type | Default | Description |
|---|---|---|---|
| `radiusPx` | `Int` | `20` | Blur radius of each point in pixels |
| `opacity` | `Double` | `0.7` | Layer opacity (0.0–1.0) |
| `gradient` | `HeatmapGradient` | `HeatmapGradient.DEFAULT` | Color gradient |
| `maxIntensity` | `Double?` | `null` | Max intensity for normalization; auto-calculated if null |

### HeatmapOverlayState

| Property | Type | Default |
|---|---|---|
| `radiusPx` | `Int` | `20` |
| `opacity` | `Double` | `0.7` |
| `gradient` | `HeatmapGradient` | `HeatmapGradient.DEFAULT` |
| `maxIntensity` | `Double?` | `null` |
| `weightProvider` | `(HeatmapPointState) -> Double` | `{ it.weight }` |

### HeatmapGradient.DEFAULT

Green (`#66E100`) at position 0.2 → Red (`#FF0000`) at position 1.0.
