# HeatmapPoint

A data class representing a single data point on a heatmap. Each point combines a geographical
location with an intensity value (weight).

This class is `Serializable`, allowing its instances to be saved to a `Bundle` or transferred
across processes.

## Signature

```kotlin
data class HeatmapPoint(
    val position: GeoPointInterface,
    val weight: Double = 1.0,
) : Serializable
```

## Description

`HeatmapPoint` is the fundamental building block for generating a heatmap layer. It encapsulates a
geographic coordinate (`position`) and its contribution to the heatmap's intensity (`weight`). A
collection of these points is typically passed to `HeatmapOverlay` via `HeatmapPoint` composables.

The `weight` parameter allows you to represent varying magnitudes at different locations. For
example, a location with 10 sales could have a weight of `10.0`, while a location with 1 sale
could have a weight of `1.0`. If all points are of equal importance, the default weight `1.0`
can be used.

## Parameters

- `position`
    - Type: `GeoPointInterface`
    - Description: **Required.** The geographical coordinate of the data point.
- `weight`
    - Type: `Double`
    - Default: `1.0`
    - Description: The intensity of the data point. Higher values contribute more to the heatmap.

## Example

```kotlin
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.heatmap.HeatmapPoint

// 1. Create a HeatmapPoint with the default weight of 1.0.
val pointA = HeatmapPoint(
    position = GeoPoint(latitude = 34.0522, longitude = -118.2437) // Los Angeles
)

// 2. Create a HeatmapPoint with a custom weight.
val pointB = HeatmapPoint(
    position = GeoPoint(latitude = 40.7128, longitude = -74.0060), // New York City
    weight = 5.5
)
```
