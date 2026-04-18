These composable functions add data points to a `HeatmapOverlay`. All variants must be placed
within the `content` block of a `HeatmapOverlay` to function correctly.

---

# HeatmapPoint

Adds a single weighted geographical point to the heatmap. The point is added to the map when
the composable enters the composition and removed when it leaves.

## Signature

```kotlin
@Composable
fun MapViewScope.HeatmapPoint(
    position: GeoPointInterface,
    weight: Double = 1.0,
    id: String? = null,
    extra: Serializable? = null,
)
```

## Description

Use this composable to represent a single data point on the heatmap. For large datasets, prefer
`HeatmapPoints` for better performance.

## Parameters

- `position`
    - Type: `GeoPointInterface`
    - Description: **Required.** The geographical location of the heatmap point.
- `weight`
    - Type: `Double`
    - Default: `1.0`
    - Description: The intensity of the point. Higher values contribute more heat to that area.
- `id`
    - Type: `String?`
    - Default: `null`
    - Description: An optional unique identifier for the point.
- `extra`
    - Type: `Serializable?`
    - Default: `null`
    - Description: Optional serializable metadata to associate with the point.

## Example

```kotlin
// Replace "MapView" with semantic SDK mapview such as "GoogleMapView"
MapView(state = mapViewState) {
    HeatmapOverlay {
        HeatmapPoint(position = GeoPoint(40.7128, -74.0060))
        HeatmapPoint(
            position = GeoPoint(34.0522, -118.2437),
            weight = 3.5,
            id = "la-point",
        )
    }
}
```

---

# HeatmapPoints

Efficiently renders a list of heatmap points in a single operation. Optimized for large or
dynamic datasets where adding points individually would be too slow.

## Signature

```kotlin
@Composable
fun MapViewScope.HeatmapPoints(states: List<HeatmapPointState>)
```

## Description

On initial composition and whenever `states` changes, this composable replaces all existing
points in the overlay with the new list. When it leaves the composition, all managed points are
automatically cleared.

> **Note:** This function replaces the entire set of points. It does not append to points added
> by other `HeatmapPoint` or `HeatmapPoints` composables.

## Parameters

- `states`
    - Type: `List<HeatmapPointState>`
    - Description: **Required.** The complete list of `HeatmapPointState` objects to display.

## Example

```kotlin
GoogleMapView(state = mapViewState) {
    HeatmapOverlay {
        HeatmapPoints(states = heatmapPointStates)
    }
}
```

---

# HeatmapPoint (State-based)

A lower-level composable that adds a single heatmap point from a pre-constructed
`HeatmapPointState`. Typically used when the state is managed externally.

## Signature

```kotlin
@Composable
fun MapViewScope.HeatmapPoint(state: HeatmapPointState)
```

## Parameters

- `state`
    - Type: `HeatmapPointState`
    - Description: **Required.** The state object representing the heatmap point.
